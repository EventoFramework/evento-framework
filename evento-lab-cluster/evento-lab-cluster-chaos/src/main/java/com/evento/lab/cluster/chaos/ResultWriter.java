package com.evento.lab.cluster.chaos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes {@code results/<scenario>/<label>/summary.json} (machine-readable,
 * the full report) and {@code report.md} (for humans and the thesis).
 */
public class ResultWriter {

    private final Path resultsDir;

    public ResultWriter(Path resultsDir) {
        this.resultsDir = resultsDir;
    }

    public Path write(ScenarioReport report) {
        var dir = resultsDir.resolve(report.id()).resolve(sanitize(report.label()));
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("summary.json"), Json.write(report.toMap()));
            Files.writeString(dir.resolve("report.md"), markdown(report));
            System.out.println("[results] " + dir.toAbsolutePath());
            return dir;
        } catch (IOException e) {
            throw new HarnessException("cannot write results to " + dir, e);
        }
    }

    static String sanitize(String label) {
        var s = label == null || label.isBlank() ? "run" : label;
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static String markdown(ScenarioReport r) {
        var m = r.toMap();
        var sb = new StringBuilder();
        sb.append("# ").append(r.id()).append(": ").append(r.title()).append("\n\n");
        sb.append("- Label: `").append(r.label()).append("`\n");
        sb.append("- Gaps exercised: ").append(r.gaps().isEmpty() ? "none" : String.join(", ", r.gaps())).append('\n');
        sb.append("- Started: ").append(m.get("startedAt")).append(", finished: ").append(m.get("finishedAt"))
                .append(" (").append(m.get("durationSeconds")).append(" s)\n");
        sb.append("- Verdicts: ").append(r.verdictCounts()).append("\n\n");

        if (!r.harnessErrors().isEmpty()) {
            sb.append("## Harness errors\n\n");
            for (var e : r.harnessErrors()) sb.append("- ").append(e).append('\n');
            sb.append('\n');
        }

        if (!r.parameters().isEmpty()) {
            sb.append("## Parameters\n\n");
            for (var e : r.parameters().entrySet()) {
                sb.append("- ").append(e.getKey()).append(": `").append(e.getValue()).append("`\n");
            }
            sb.append('\n');
        }

        sb.append("## Observed vs expected\n\n");
        if (r.observations().isEmpty()) {
            sb.append("_No observation recorded._\n\n");
        } else {
            sb.append("| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |\n");
            sb.append("|---|---|---|---|---|\n");
            for (var o : r.observations()) {
                sb.append("| ").append(cell(o.name())).append(" | **").append(o.verdict()).append("** | ")
                        .append(cell(o.expected())).append(" | ").append(cell(o.expectedBeforeFix())).append(" | ")
                        .append(cell(o.observed())).append(" |\n");
            }
            sb.append('\n');
            for (var o : r.observations()) {
                if (o.note() != null && !o.note().isBlank()) {
                    sb.append("- _").append(o.name()).append("_: ").append(o.note()).append('\n');
                }
            }
            sb.append('\n');
        }

        sb.append("## Invariants\n\n");
        if (r.invariants().isEmpty()) {
            sb.append("_Not evaluated in this scenario._\n\n");
        } else {
            sb.append("| Id | Invariant | Status | Expected | Observed |\n|---|---|---|---|---|\n");
            for (var i : r.invariants()) {
                sb.append("| ").append(i.id()).append(" | ").append(cell(i.name())).append(" | **").append(i.status())
                        .append("** | ").append(cell(i.expected())).append(" | ").append(cell(i.observed())).append(" |\n");
            }
            sb.append('\n');
            for (var i : r.invariants()) {
                if (i.violations().isEmpty()) continue;
                sb.append("<details><summary>").append(i.id()).append(": ").append(i.violationCount())
                        .append(" violation(s)</summary>\n\n");
                for (var v : i.violations()) sb.append("- ").append(v).append('\n');
                if (i.violationCount() > i.violations().size()) {
                    sb.append("- ... ").append(i.violationCount() - i.violations().size()).append(" more\n");
                }
                sb.append("\n</details>\n\n");
            }
        }

        if (!r.metrics().isEmpty()) {
            sb.append("## Metrics\n\n```json\n").append(Json.write(r.metrics())).append("\n```\n\n");
        }

        if (!r.notes().isEmpty()) {
            sb.append("## Notes\n\n");
            for (var n : r.notes()) sb.append("- ").append(n).append('\n');
            sb.append('\n');
        }

        sb.append("## Timeline\n\n");
        for (var t : r.timeline()) {
            sb.append("- +").append(String.format("%.1f", t.tMs() / 1000.0)).append(" s: ").append(t.event()).append('\n');
        }
        sb.append('\n');

        for (Map.Entry<String, java.util.List<String>> a : r.attachments().entrySet()) {
            sb.append("### ").append(a.getKey()).append("\n\n```\n");
            for (var line : a.getValue()) sb.append(line).append('\n');
            sb.append("```\n\n");
        }
        return sb.toString();
    }

    private static String cell(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", " ");
    }
}
