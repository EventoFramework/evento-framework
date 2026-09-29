package com.evento.lab.cluster.web;

import com.evento.application.EventoBundle;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.lab.cluster.api.command.AddOrderItemCommand;
import com.evento.lab.cluster.api.command.CompleteOrderCommand;
import com.evento.lab.cluster.api.command.CreateOrderCommand;
import com.evento.lab.cluster.api.query.FindGlobalOrderQuery;
import com.evento.lab.cluster.config.LabBundles;
import com.evento.lab.cluster.config.LabSettings;
import com.evento.lab.cluster.config.Regions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The HTTP ingress of one region (L4). Every command is stamped with this
 * JVM's region and sent through the region's own evento-server (the first
 * address of {@code EVENTO_SERVER_ADDRESSES}). A 2xx means Evento acknowledged
 * the command, i.e. the event is stored. Retries are never automatic: the
 * chaos driver owns them, so it can measure what a retry does (G5, S14).
 *
 * <p>Only the {@code region} role serves these endpoints; other roles answer 404.
 */
@RestController
public class OrderGatewayController {

    private static final Logger log = LoggerFactory.getLogger(OrderGatewayController.class);

    private final LabBundles bundles;
    private final LabSettings settings;

    public OrderGatewayController(LabBundles bundles) {
        this.bundles = bundles;
        this.settings = bundles.settings();
    }

    public record ItemBody(String sku, Integer qty) {
    }

    /**
     * @param orderId optional; generated when absent
     * @param items   optional items added right after creation
     * @param context lab-only hook: overrides the event context (S11 injects
     *                {@code SUDXAMERICA} this way). Normally absent.
     */
    public record CreateBody(String orderId, List<ItemBody> items, String context) {
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody(required = false) CreateBody body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        var notRegion = requireRegion();
        if (notRegion != null) return notRegion;
        var orderId = body != null && body.orderId() != null && !body.orderId().isBlank()
                ? body.orderId() : UUID.randomUUID().toString();
        var context = body != null && body.context() != null && !body.context().isBlank()
                ? body.context() : settings.region();
        log.info("event=gateway_request op=create orderId={} region={} context={} idempotencyKey={}",
                orderId, settings.region(), context, idempotencyKey);
        var result = base(orderId);
        long start = System.nanoTime();
        try {
            send(new CreateOrderCommand(orderId, context, idempotencyKey));
            int added = 0;
            if (body != null && body.items() != null) {
                for (var item : body.items()) {
                    send(new AddOrderItemCommand(orderId, item.sku(), item.qty() == null ? 1 : item.qty(),
                            context, idempotencyKey == null ? null : idempotencyKey + "#" + added));
                    added++;
                }
            }
            result.put("itemsAdded", added);
            result.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
            return ResponseEntity.status(201).body(result);
        } catch (Exception e) {
            return failure("create", orderId, idempotencyKey, e, result, start);
        }
    }

    @PostMapping("/orders/{id}/items")
    public ResponseEntity<Map<String, Object>> addItem(
            @PathVariable("id") String orderId,
            @RequestBody ItemBody body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        var notRegion = requireRegion();
        if (notRegion != null) return notRegion;
        log.info("event=gateway_request op=add_item orderId={} region={} idempotencyKey={}",
                orderId, settings.region(), idempotencyKey);
        var result = base(orderId);
        long start = System.nanoTime();
        try {
            send(new AddOrderItemCommand(orderId, body.sku(), body.qty() == null ? 1 : body.qty(),
                    settings.region(), idempotencyKey));
            result.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return failure("add_item", orderId, idempotencyKey, e, result, start);
        }
    }

    @PostMapping("/orders/{id}/complete")
    public ResponseEntity<Map<String, Object>> complete(
            @PathVariable("id") String orderId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        var notRegion = requireRegion();
        if (notRegion != null) return notRegion;
        log.info("event=gateway_request op=complete orderId={} region={} idempotencyKey={}",
                orderId, settings.region(), idempotencyKey);
        var result = base(orderId);
        long start = System.nanoTime();
        try {
            send(new CompleteOrderCommand(orderId, settings.region(), idempotencyKey));
            result.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return failure("complete", orderId, idempotencyKey, e, result, start);
        }
    }

    /** This region's own read model, read straight from its table. */
    @GetMapping("/orders/{id}")
    public ResponseEntity<Object> regional(@PathVariable("id") String orderId) {
        var notRegion = requireRegion();
        if (notRegion != null) return ResponseEntity.status(notRegion.getStatusCode()).body(notRegion.getBody());
        try {
            var view = bundles.view(Regions.regionalTable(settings.region())).orElseThrow();
            return view.find(orderId)
                    .<ResponseEntity<Object>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                            "error", "order not in " + view.table(), "orderId", orderId,
                            "region", settings.region())));
        } catch (Exception e) {
            var f = Failure.of(e);
            return ResponseEntity.status(500).body(Map.of("error", f.message(), "kind", f.kind()));
        }
    }

    /** The worldwide read model, through the query bus (served by a cell core). */
    @GetMapping("/orders/global/{id}")
    public ResponseEntity<Object> global(@PathVariable("id") String orderId) {
        var notRegion = requireRegion();
        if (notRegion != null) return ResponseEntity.status(notRegion.getStatusCode()).body(notRegion.getBody());
        try {
            var bundle = bundle();
            var single = bundle.getQueryGateway()
                    .query(new FindGlobalOrderQuery(orderId), settings.commandTimeoutMs(), TimeUnit.MILLISECONDS)
                    .get();
            return ResponseEntity.ok(single.getData());
        } catch (Exception e) {
            var f = Failure.of(e);
            var out = new LinkedHashMap<String, Object>();
            out.put("error", f.message());
            out.put("kind", f.kind());
            out.put("orderId", orderId);
            out.put("region", settings.region());
            return ResponseEntity.status(f.status()).body(out);
        }
    }

    private void send(Command command) throws Exception {
        bundle().getCommandGateway()
                .send(command, settings.commandTimeoutMs(), TimeUnit.MILLISECONDS)
                .get();
    }

    private EventoBundle bundle() {
        return bundles.primaryBundle()
                .orElseThrow(() -> new IllegalStateException("not connected: bundle not started yet ("
                        + bundles.lastError() + ")"));
    }

    private Map<String, Object> base(String orderId) {
        var m = new LinkedHashMap<String, Object>();
        m.put("orderId", orderId);
        m.put("region", settings.region());
        m.put("cell", settings.cell());
        m.put("instance", settings.instanceId());
        return m;
    }

    private ResponseEntity<Map<String, Object>> failure(String op, String orderId, String idempotencyKey,
                                                        Exception e, Map<String, Object> result, long start) {
        var f = Failure.of(e);
        long ms = (System.nanoTime() - start) / 1_000_000;
        log.warn("event=gateway_command_failed op={} orderId={} region={} kind={} latencyMs={} idempotencyKey={} error={}",
                op, orderId, settings.region(), f.kind(), ms, idempotencyKey, f.message());
        result.put("error", f.message());
        result.put("errorType", f.type());
        result.put("kind", f.kind());
        result.put("latencyMs", ms);
        // not connected yet: let the LB fail over to a sibling gateway of the cell
        var status = bundles.primaryBundle().isEmpty() ? 503 : f.status();
        return ResponseEntity.status(status).body(result);
    }

    private ResponseEntity<Map<String, Object>> requireRegion() {
        if (settings.role() == LabSettings.Role.REGION) return null;
        var m = new LinkedHashMap<String, Object>();
        m.put("error", "this JVM is role " + settings.role().envValue() + ", not a region gateway");
        m.put("kind", "wrong_role");
        return ResponseEntity.status(404).body(m);
    }
}
