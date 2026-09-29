package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.Json;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.RequestRecord;
import com.evento.lab.cluster.chaos.Rows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S11: context isolation for the two region names with an underscore.
 * Orders are created in SUD_AMERICA and AUSTRALIA_NZ, plus one synthetic order
 * each whose events carry the context {@code SUDXAMERICA} / {@code AUSTRALIAXNZ}
 * (lab hook: the gateway's {@code context} body field). The server matches
 * contexts with an unescaped LIKE, where {@code _} is a one-character wildcard
 * (G15), so the synthetic events leak into the {@code SUD_AMERICA} /
 * {@code AUSTRALIA_NZ} engines of {@code OrderProjector} and show up as
 * foreign-context rows in the AM / APAC cell databases. The GUI's consumer
 * list parses consumer ids by splitting on {@code _} (G16).
 * This is also the real-server version of the missing isolation assert (L6).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S11ContextIsolationTest extends AbstractScenario {

    private static final Map<String, String> SYNTHETIC = Map.of(
            "SUD_AMERICA", "SUDXAMERICA",
            "AUSTRALIA_NZ", "AUSTRALIAXNZ");

    @Override
    protected String id() {
        return "S11";
    }

    @Override
    protected String title() {
        return "Context isolation with underscore regions";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G15", "G16", "L6");
    }

    @Test
    void contextIsolation() throws Exception {
        scenario(() -> {
            var perRegion = LabConfig.envInt("S11_ORDERS", 5);
            var records = new ArrayList<RequestRecord>();
            var synthetic = new LinkedHashMap<String, String>();
            for (var region : SYNTHETIC.keySet()) {
                for (int i = 0; i < perRegion; i++) {
                    var id = gateway.newOrderId(prefix, region);
                    records.add(gateway.create(region, id, UUID.randomUUID().toString()));
                    records.add(gateway.addItem(region, id, "sku-1", 1, UUID.randomUUID().toString()));
                }
                var fake = SYNTHETIC.get(region);
                var id = gateway.newOrderId(prefix + "synthetic-", fake);
                var r = gateway.create(region, id, UUID.randomUUID().toString(), fake);
                records.add(r);
                synthetic.put(fake, id);
                report.event("synthetic order " + id + " with context " + fake + ": " + r.outcome());
            }
            report.param("syntheticOrders", synthetic);

            // The synthetic events are not "expected" in any regional view, so I6 does not wait for
            // them: wait here until each is in the global view and (if it leaks) in the regional
            // view of the region it looks like, then settle, so I4/I5 see the final state.
            var leakWait = envSeconds("S11_LEAK_WAIT_S", 60);
            var settled = Cluster.waitUntil(leakWait, Duration.ofSeconds(2), () -> {
                var s = db.snapshot(prefix);
                if (s.global() == null || s.cellViews() == null) return false;
                return synthetic.entrySet().stream().allMatch(e -> {
                    var region = SYNTHETIC.entrySet().stream().filter(x -> x.getValue().equals(e.getKey()))
                            .map(Map.Entry::getKey).findFirst().orElseThrow();
                    return s.global().stream().anyMatch(v -> v.orderId().equals(e.getValue()))
                            && s.cellViews().getOrDefault(LabConfig.CELL_OF.get(region), List.of()).stream()
                            .anyMatch(v -> v.orderId().equals(e.getValue()));
                });
            });
            report.event("synthetic orders in global + regional views: " + settled
                    + (settled ? "" : " (not all leaked within " + leakWait.toSeconds() + " s)"));
            Cluster.sleep(Duration.ofSeconds(5));
            var results = checkInvariants(records, envSeconds("S11_CONVERGENCE_S", 90));
            var snap = db.snapshot(prefix);
            for (var region : SYNTHETIC.keySet()) {
                var fake = SYNTHETIC.get(region);
                var cell = LabConfig.CELL_OF.get(region);
                var rows = snap.cellViews() == null ? List.<Rows.ViewRow>of() : snap.cellViews().get(cell);
                var leaked = rows.stream().filter(v -> fake.equals(v.context())).map(Rows.ViewRow::orderId).toList();
                var own = rows.stream().filter(v -> region.equals(v.context())).count();
                report.observe(LabConfig.appPg(cell) + ".order_view vs context " + fake,
                        "only " + region + " events reach OrderProjector's " + region + " engine in "
                                + LabConfig.queryService(region),
                        "LIKE '" + region + "' matches " + fake + ": the synthetic order leaks in (G15)",
                        own + " own rows, leaked: " + (leaked.isEmpty() ? "none" : leaked),
                        leaked.isEmpty(), !leaked.isEmpty(), null);
            }
            var i4 = find(results, "I4");
            report.observe("I4 regional isolation", i4.expected(),
                    "the synthetic SUDXAMERICA / AUSTRALIAXNZ rows are foreign rows in the regional views (G15)",
                    i4.observed(), i4.passed(), !i4.passed(), null);

            guiContexts();
        });
    }

    /** G16: what the admin UI says the context of each regional consumer is. */
    private void guiContexts() {
        try {
            var auth = Base64.getEncoder().encodeToString("evento:secret".getBytes(StandardCharsets.UTF_8));
            var req = HttpRequest.newBuilder(URI.create("http://localhost:" + config.serverHttpPorts().get("evento-am")
                            + "/api/consumer/"))
                    .header("Authorization", "Basic " + auth).timeout(Duration.ofSeconds(15)).GET().build();
            var resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                report.observe("GUI consumer context (G16)", "full context names", "HTTP " + resp.statusCode(), null);
                return;
            }
            var shown = new LinkedHashMap<String, String>();
            for (var c : Json.read(resp.body())) {
                var bundle = c.path("bundleId").asText();
                if (bundle.startsWith("order-")) {
                    shown.put(c.path("consumerId").asText(), "bundle=" + bundle + " context=" + c.path("context").asText()
                            + " version=" + c.path("componentVersion").asText());
                }
            }
            report.attach("GUI /api/consumer/ on evento-am", shown.entrySet().stream()
                    .map(e -> e.getKey() + " -> " + e.getValue()).toList());
            var wrong = shown.entrySet().stream().filter(e -> {
                var id = e.getKey();
                var ctx = id.substring(id.lastIndexOf("_1_") + 3);
                return id.contains("_1_") && !e.getValue().contains("context=" + ctx + " ");
            }).map(Map.Entry::getKey).toList();
            var regionalShown = shown.keySet().stream().filter(k -> k.startsWith("order-query_OrderProjector_1_")
                    && LabConfig.REGIONS.stream().anyMatch(k::endsWith)).count();
            // G16 is only testable if the page lists a consumer whose context contains '_'; the
            // pre-fix page groups per component (N10) and may show none of them (L15).
            var underscoreShown = shown.keySet().stream()
                    .filter(k -> k.contains("_1_") && k.substring(k.lastIndexOf("_1_") + 3).contains("_")).toList();
            report.observe("GUI consumer context (G16)",
                    "every consumer shows its full context (SUD_AMERICA, AUSTRALIA_NZ)",
                    "split('_') cuts names with '_' (e.g. SUD_AMERICA is shown as SUD)",
                    underscoreShown.isEmpty() ? "not testable: the page lists no consumer with '_' in its context"
                            : wrong.isEmpty() ? "all contexts shown correctly (" + underscoreShown + ")" : "wrong for " + wrong,
                    underscoreShown.isEmpty() ? null : wrong.isEmpty(), !wrong.isEmpty(), null);
            // Tell the two pre-fix causes of a short list apart: rows missing in core__consumer
            // (G8) or rows present but grouped per component by the page (N10).
            long regionalInCatalog = -1;
            try (var c = db.es()) {
                regionalInCatalog = db.catalog(c).consumers().keySet().stream()
                        .filter(k -> k.startsWith("order-query_OrderProjector_1_")
                                && LabConfig.REGIONS.stream().anyMatch(k::endsWith)).count();
            } catch (Exception e) {
                report.note("core__consumer not readable: " + e.getMessage());
            }
            report.observe("GUI lists one consumer per regional engine",
                    "7 regional consumers of order-query's OrderProjector (one per context)",
                    "fewer rows: the page groups consumers per component (N10), or registrations lost to the "
                            + "discovery race (G8)",
                    regionalShown + " regional consumer row(s) on the page; " + regionalInCatalog
                            + " regional consumer id(s) in core__consumer"
                            + (regionalShown < LabConfig.REGIONS.size() && regionalInCatalog == LabConfig.REGIONS.size()
                            ? " (N10: grouped by the page)" : ""),
                    regionalShown == LabConfig.REGIONS.size(), regionalShown < LabConfig.REGIONS.size(),
                    "One OrderProjector class, one engine per context (setComponentContexts).");
        } catch (Exception e) {
            report.observe("GUI consumer context (G16)", "full context names", "GUI unreachable: " + e, null);
        }
    }
}
