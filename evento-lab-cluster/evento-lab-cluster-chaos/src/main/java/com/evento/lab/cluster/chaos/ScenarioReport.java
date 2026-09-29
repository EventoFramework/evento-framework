package com.evento.lab.cluster.chaos;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a scenario learned, in the shape the results files take. A
 * scenario records; it does not assert. The verdict of each observation says
 * how it compares with the two expectations of the readiness assessment: the
 * behaviour of a correct system, and the documented behaviour at HEAD before a
 * gap is fixed.
 */
public class ScenarioReport {

    public enum Verdict {
        /** The system behaved as a correct system should. */
        AS_EXPECTED,
        /** The documented defect showed up, as predicted for the pre-fix code. */
        KNOWN_DEFECT_REPRODUCED,
        /** Neither the correct behaviour nor the documented defect. */
        UNEXPECTED,
        /** Not enough signal to tell (e.g. the fault did not bite in this run). */
        INCONCLUSIVE
    }

    public record Observation(String name, String expected, String expectedBeforeFix, String observed,
                              Verdict verdict, String note) {
    }

    public record TimelineEntry(long tMs, String at, String event) {
    }

    private final String id;
    private final String title;
    private final List<String> gaps;
    private final String label;
    private final Instant startedAt = Instant.now();
    private Instant finishedAt;
    private final Map<String, Object> parameters = new LinkedHashMap<>();
    private final List<TimelineEntry> timeline = new ArrayList<>();
    private final List<Observation> observations = new ArrayList<>();
    private final List<InvariantResult> invariants = new ArrayList<>();
    private final Map<String, Object> metrics = new LinkedHashMap<>();
    private final Map<String, List<String>> attachments = new LinkedHashMap<>();
    private final List<String> harnessErrors = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public ScenarioReport(String id, String title, List<String> gaps, String label) {
        this.id = id;
        this.title = title;
        this.gaps = List.copyOf(gaps);
        this.label = label;
    }

    public synchronized ScenarioReport param(String k, Object v) {
        parameters.put(k, v);
        return this;
    }

    public synchronized ScenarioReport event(String e) {
        var now = Instant.now();
        timeline.add(new TimelineEntry(Duration.between(startedAt, now).toMillis(), now.toString(), e));
        System.out.println("[" + id + "] " + e);
        return this;
    }

    public synchronized ScenarioReport metric(String k, Object v) {
        metrics.put(k, v);
        return this;
    }

    public synchronized ScenarioReport note(String n) {
        notes.add(n);
        return this;
    }

    public synchronized ScenarioReport attach(String name, List<String> lines) {
        attachments.put(name, List.copyOf(lines));
        return this;
    }

    public synchronized ScenarioReport harnessError(String e) {
        harnessErrors.add(e);
        System.out.println("[" + id + "] HARNESS ERROR " + e);
        return this;
    }

    /**
     * Record an observation and classify it.
     *
     * @param correct      did the system show the correct behaviour?
     * @param defectShown  did it show the documented pre-fix defect? (ignored when {@code correct})
     */
    public synchronized Observation observe(String name, String expected, String expectedBeforeFix, String observed,
                                            Boolean correct, Boolean defectShown, String note) {
        Verdict v;
        if (correct == null) v = Verdict.INCONCLUSIVE;
        else if (correct) v = Verdict.AS_EXPECTED;
        else if (Boolean.TRUE.equals(defectShown)) v = Verdict.KNOWN_DEFECT_REPRODUCED;
        else v = Verdict.UNEXPECTED;
        var o = new Observation(name, expected, expectedBeforeFix, observed, v, note);
        observations.add(o);
        System.out.println("[" + id + "] " + v + " " + name + ": " + observed);
        return o;
    }

    /** Record an observation that has no pre-fix defect variant. */
    public Observation observe(String name, String expected, String observed, Boolean correct) {
        return observe(name, expected, null, observed, correct, null, null);
    }

    public synchronized ScenarioReport invariants(List<InvariantResult> results) {
        invariants.addAll(results);
        for (var r : results) {
            System.out.println("[" + id + "] " + r.id() + " " + r.status() + " " + r.observed());
        }
        return this;
    }

    public synchronized void finish() {
        finishedAt = Instant.now();
    }

    public synchronized Map<String, Object> toMap() {
        var m = new LinkedHashMap<String, Object>();
        m.put("scenario", id);
        m.put("title", title);
        m.put("gaps", gaps);
        m.put("label", label);
        m.put("startedAt", startedAt.toString());
        m.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
        m.put("durationSeconds", finishedAt == null ? null : Duration.between(startedAt, finishedAt).toSeconds());
        m.put("parameters", parameters);
        m.put("verdictCounts", verdictCounts());
        m.put("observations", observations);
        m.put("invariants", invariants);
        m.put("metrics", metrics);
        m.put("timeline", timeline);
        m.put("notes", notes);
        m.put("harnessErrors", harnessErrors);
        m.put("attachments", attachments);
        return m;
    }

    public synchronized Map<String, Integer> verdictCounts() {
        var c = new LinkedHashMap<String, Integer>();
        for (var o : observations) c.merge(o.verdict().name(), 1, Integer::sum);
        return c;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public List<String> gaps() {
        return gaps;
    }

    public String label() {
        return label;
    }

    public synchronized List<Observation> observations() {
        return List.copyOf(observations);
    }

    public synchronized List<InvariantResult> invariants() {
        return List.copyOf(invariants);
    }

    public synchronized List<String> harnessErrors() {
        return List.copyOf(harnessErrors);
    }

    synchronized Map<String, Object> parameters() {
        return new LinkedHashMap<>(parameters);
    }

    synchronized Map<String, Object> metrics() {
        return new LinkedHashMap<>(metrics);
    }

    synchronized List<TimelineEntry> timeline() {
        return List.copyOf(timeline);
    }

    synchronized List<String> notes() {
        return List.copyOf(notes);
    }

    synchronized Map<String, List<String>> attachments() {
        return new LinkedHashMap<>(attachments);
    }

    Instant startedAt() {
        return startedAt;
    }
}
