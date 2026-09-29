package com.evento.lab.cluster.chaos;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Toxiproxy HTTP API client that only ever undoes what it did itself.
 *
 * <p>The WAN baseline is a pair of toxics per proxy named
 * {@code wan-latency-up} / {@code wan-latency-down}, installed by the stack.
 * {@code POST /reset} would drop them, so it is never used: scenario toxics are
 * named {@code chaos-*} and tracked, baseline edits are snapshotted and
 * restored, and {@link #heal()} reverts exactly that.
 */
public class Toxi {

    public record Toxic(String name, String type, String stream, Map<String, Object> attributes) {
    }

    public record Proxy(String name, String listen, String upstream, boolean enabled, List<Toxic> toxics) {
        /** Source cell of a proxy named {@code <src>-to-<svc>-<dst>}. */
        public String sourceCell() {
            var i = name.indexOf("-to-");
            return i < 0 ? "" : name.substring(0, i);
        }
    }

    private record Added(String proxy, String toxic) {
    }

    private record Edited(String proxy, String toxic, Map<String, Object> originalAttributes) {
    }

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final List<Added> added = new ArrayList<>();
    private final List<Edited> edited = new ArrayList<>();
    private final List<String> disabled = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();

    public Toxi(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public List<String> actions() {
        synchronized (actions) {
            return List.copyOf(actions);
        }
    }

    private void action(String a) {
        synchronized (actions) {
            actions.add(Instant.now() + " " + a);
        }
        System.out.println("[toxi] " + a);
    }

    public List<Proxy> proxies() {
        var node = Json.read(call("GET", "/proxies", null));
        var out = new ArrayList<Proxy>();
        node.fields().forEachRemaining(e -> out.add(toProxy(e.getValue())));
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    public List<Proxy> select(Predicate<Proxy> filter) {
        return proxies().stream().filter(filter).toList();
    }

    /** Proxies whose upstream is {@code host} (e.g. {@code es-pg-eu}). */
    public List<Proxy> towards(String host) {
        return select(p -> p.upstream().startsWith(host + ":"));
    }

    /** Proxies used by cell {@code cell} (lower case, e.g. {@code apac}) to reach other cells. */
    public List<Proxy> from(String cell) {
        return select(p -> p.sourceCell().equals(cell));
    }

    /** Proxies touching cell {@code cell} in either direction. */
    public List<Proxy> touching(String cell) {
        return select(p -> p.sourceCell().equals(cell) || p.name().endsWith("-" + cell));
    }

    public void addToxic(String proxy, String name, String type, String stream, Map<String, Object> attributes) {
        if (!name.startsWith("chaos-")) {
            throw new IllegalArgumentException("scenario toxics must be named chaos-*: " + name);
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("name", name);
        body.put("type", type);
        body.put("stream", stream);
        body.put("toxicity", 1.0);
        body.put("attributes", attributes);
        action("add toxic " + proxy + "/" + name + " " + type + " " + stream + " " + attributes);
        call("POST", "/proxies/" + proxy + "/toxics", Json.write(body));
        synchronized (added) {
            added.add(new Added(proxy, name));
        }
    }

    public void addLatency(String proxy, String name, int latencyMs, int jitterMs) {
        addToxic(proxy, name + "-down", "latency", "downstream", Map.of("latency", latencyMs, "jitter", jitterMs));
        addToxic(proxy, name + "-up", "latency", "upstream", Map.of("latency", latencyMs, "jitter", jitterMs));
    }

    /** A black hole: data stops flowing and the connection is never closed (silent partition). */
    public void addBlackhole(String proxy, String name) {
        addToxic(proxy, name + "-down", "timeout", "downstream", Map.of("timeout", 0));
        addToxic(proxy, name + "-up", "timeout", "upstream", Map.of("timeout", 0));
    }

    /**
     * Set the latency of every existing toxic on {@code proxy} whose name
     * starts with {@code wan-latency}; the original attributes are restored by
     * {@link #heal()}.
     */
    public void setBaselineLatency(String proxy, int latencyMs, int jitterMs) {
        var p = proxies().stream().filter(x -> x.name().equals(proxy)).findFirst()
                .orElseThrow(() -> new HarnessException("no proxy " + proxy));
        for (var t : p.toxics()) {
            if (!t.name().startsWith("wan-latency")) continue;
            synchronized (edited) {
                boolean already = edited.stream().anyMatch(e -> e.proxy().equals(proxy) && e.toxic().equals(t.name()));
                if (!already) edited.add(new Edited(proxy, t.name(), t.attributes()));
            }
            action("set " + proxy + "/" + t.name() + " latency=" + latencyMs + " jitter=" + jitterMs);
            var body = Map.of("attributes", Map.of("latency", latencyMs, "jitter", jitterMs));
            call("POST", "/proxies/" + proxy + "/toxics/" + t.name(), Json.write(body));
        }
    }

    public void setEnabled(String proxy, boolean enabled) {
        action((enabled ? "enable " : "disable ") + proxy);
        call("POST", "/proxies/" + proxy, Json.write(Map.of("enabled", enabled)));
        synchronized (disabled) {
            if (!enabled && !disabled.contains(proxy)) disabled.add(proxy);
            if (enabled) disabled.remove(proxy);
        }
    }

    /** Remove every toxic this instance added, restore edited baselines, re-enable proxies. */
    public List<String> heal() {
        var errors = new ArrayList<String>();
        synchronized (added) {
            for (var a : added) {
                try {
                    action("remove toxic " + a.proxy() + "/" + a.toxic());
                    call("DELETE", "/proxies/" + a.proxy() + "/toxics/" + a.toxic(), null);
                } catch (Exception e) {
                    if (!String.valueOf(e.getMessage()).contains("404")) errors.add(e.toString());
                }
            }
            added.clear();
        }
        synchronized (edited) {
            for (var e : edited) {
                try {
                    action("restore " + e.proxy() + "/" + e.toxic() + " " + e.originalAttributes());
                    call("POST", "/proxies/" + e.proxy() + "/toxics/" + e.toxic(),
                            Json.write(Map.of("attributes", e.originalAttributes())));
                } catch (Exception ex) {
                    errors.add(ex.toString());
                }
            }
            edited.clear();
        }
        synchronized (disabled) {
            for (var p : List.copyOf(disabled)) {
                try {
                    setEnabled(p, true);
                } catch (Exception ex) {
                    errors.add(ex.toString());
                }
            }
            disabled.clear();
        }
        return errors;
    }

    /** Remove any leftover {@code chaos-*} toxic, e.g. from a run that was killed. */
    public void sweepLeftovers() {
        for (var p : proxies()) {
            for (var t : p.toxics()) {
                if (t.name().startsWith("chaos-")) {
                    action("sweep leftover " + p.name() + "/" + t.name());
                    try {
                        call("DELETE", "/proxies/" + p.name() + "/toxics/" + t.name(), null);
                    } catch (Exception ignored) {
                        // best effort
                    }
                }
            }
            if (!p.enabled()) setEnabled(p.name(), true);
        }
    }

    public boolean reachable() {
        try {
            call("GET", "/version", null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Proxy toProxy(JsonNode n) {
        var toxics = new ArrayList<Toxic>();
        n.path("toxics").forEach(t -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> attrs = Json.MAPPER.convertValue(t.path("attributes"), Map.class);
            toxics.add(new Toxic(t.path("name").asText(), t.path("type").asText(), t.path("stream").asText(),
                    attrs == null ? Map.of() : attrs));
        });
        return new Proxy(n.path("name").asText(), n.path("listen").asText(), n.path("upstream").asText(),
                n.path("enabled").asBoolean(true), toxics);
    }

    private String call(String method, String path, String body) {
        try {
            var b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10));
            if (body != null) {
                b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            var resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new HarnessException("toxiproxy " + method + " " + path + " -> " + resp.statusCode() + " " + resp.body());
            }
            return resp.body();
        } catch (HarnessException e) {
            throw e;
        } catch (Exception e) {
            throw new HarnessException("toxiproxy " + method + " " + path + " unreachable at " + baseUrl, e);
        }
    }
}
