package com.evento.lab.cluster.gateway;

import com.evento.application.EventoBundle;
import com.evento.common.modeling.messaging.payload.Command;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.cluster.api.command.AddOrderItemCommand;
import com.evento.lab.cluster.api.command.CompleteOrderCommand;
import com.evento.lab.cluster.api.command.CreateOrderCommand;
import com.evento.lab.cluster.api.command.SetPriceCommand;
import com.evento.lab.cluster.api.event.OrderCreatedEvent;
import com.evento.lab.cluster.api.query.FindGlobalOrderQuery;
import com.evento.lab.cluster.api.query.FindOrderQuery;
import com.evento.lab.cluster.api.query.FindPriceQuery;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.BundleRunner;
import com.evento.lab.cluster.support.web.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The HTTP ingress of one region (bundle {@code order-gateway}, one deployment
 * per region behind the location-aware LB). Every command is stamped with
 * this deployment's region ({@code EVENTO_REGION}) and sent through the
 * region's own evento-server (the first address of
 * {@code EVENTO_SERVER_ADDRESSES}). Queries are answered by an order-query
 * deployment of the same cell (with the broker's {@code locality-preferred}
 * pick strategy and {@code EVENTO_ZONE} set). A 2xx on a command means Evento
 * acknowledged it, i.e. the event is stored. Retries are never automatic: the
 * chaos driver owns them, so it can measure what a retry does (G5, S14).
 *
 * <p>{@code X-Idempotency-Key} is passed to Evento as the command's idempotency
 * key when the framework has them ({@code CommandGateway.sendIdempotent}, G5):
 * a retry with the same key, through any cell, is then answered with the event
 * of the first attempt that was stored. Items of a create carry
 * {@code <key>#<n>}. On a replayed create the order id is the stored one, not
 * the id this request generated. On {@code :before} images the header only
 * travels on the payload, as evidence for the invariant checker.
 *
 * <p>The gateway runs no consumer and owns no database; the only exception is
 * the diagnostic {@code GET /catalog/prices/{sku}?db=<cell>}, which reads one
 * regional database directly ({@code DATA_TARGETS}) to show what the global
 * fan-out wrote there.
 */
@RestController
public class OrderGatewayController {

    private static final Logger log = LoggerFactory.getLogger(OrderGatewayController.class);

    private final BundleRunner runner;
    private final BundleDeployment deployment;
    private final PriceReaders priceReaders;
    private final String region;

    public OrderGatewayController(BundleRunner runner, PriceReaders priceReaders) {
        this.runner = runner;
        this.deployment = runner.deployment();
        this.priceReaders = priceReaders;
        this.region = deployment.requireRegion();
        runner.features().idempotencyKeys();
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

    /** @param priceCents the list price in cents; {@code currency} defaults to EUR */
    public record PriceBody(String sku, Long priceCents, String currency) {
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody(required = false) CreateBody body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        var orderId = body != null && body.orderId() != null && !body.orderId().isBlank()
                ? body.orderId() : UUID.randomUUID().toString();
        var context = body != null && body.context() != null && !body.context().isBlank()
                ? body.context() : region;
        log.info("event=gateway_request op=create orderId={} region={} context={} idempotencyKey={}",
                orderId, region, context, idempotencyKey);
        var result = base(orderId);
        long start = System.nanoTime();
        try {
            // A replay answers with the order the first attempt created, whose id wins.
            var created = send(new CreateOrderCommand(orderId, context, idempotencyKey), idempotencyKey);
            if (created instanceof OrderCreatedEvent e && e.getOrderId() != null && !e.getOrderId().equals(orderId)) {
                log.info("event=gateway_idempotent_replay op=create requestedOrderId={} orderId={} idempotencyKey={}",
                        orderId, e.getOrderId(), idempotencyKey);
                orderId = e.getOrderId();
                result.put("orderId", orderId);
                result.put("replayed", true);
            }
            int added = 0;
            if (body != null && body.items() != null) {
                for (var item : body.items()) {
                    var itemKey = idempotencyKey == null ? null : idempotencyKey + "#" + added;
                    send(new AddOrderItemCommand(orderId, item.sku(), item.qty() == null ? 1 : item.qty(),
                            context, itemKey), itemKey);
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
        log.info("event=gateway_request op=add_item orderId={} region={} idempotencyKey={}",
                orderId, region, idempotencyKey);
        var result = base(orderId);
        long start = System.nanoTime();
        try {
            send(new AddOrderItemCommand(orderId, body.sku(), body.qty() == null ? 1 : body.qty(),
                    region, idempotencyKey), idempotencyKey);
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
        log.info("event=gateway_request op=complete orderId={} region={} idempotencyKey={}",
                orderId, region, idempotencyKey);
        var result = base(orderId);
        long start = System.nanoTime();
        try {
            send(new CompleteOrderCommand(orderId, region, idempotencyKey), idempotencyKey);
            result.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return failure("complete", orderId, idempotencyKey, e, result, start);
        }
    }

    /** The order as this cell's read model holds it ({@code order_view} of the cell database). */
    @GetMapping("/orders/{id}")
    public ResponseEntity<Object> regional(@PathVariable("id") String orderId) {
        return query(new FindOrderQuery(orderId), Map.of("orderId", orderId));
    }

    /** The world-wide read model ({@code order_view_global}), through the query bus. */
    @GetMapping("/orders/global/{id}")
    public ResponseEntity<Object> global(@PathVariable("id") String orderId) {
        return query(new FindGlobalOrderQuery(orderId), Map.of("orderId", orderId));
    }

    /**
     * Set a world-wide price: one {@code PriceChangedEvent} with the literal
     * context {@code ALL}, which the single global lane fans out to every
     * regional database.
     */
    @PostMapping("/catalog/prices")
    public ResponseEntity<Map<String, Object>> setPrice(
            @RequestBody PriceBody body,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        var result = new LinkedHashMap<String, Object>();
        result.put("sku", body == null ? null : body.sku());
        result.put("region", region);
        result.put("instance", deployment.instanceId());
        if (body == null || body.sku() == null || body.sku().isBlank() || body.priceCents() == null) {
            result.put("error", "sku and priceCents are required");
            result.put("kind", "bad_request");
            return ResponseEntity.badRequest().body(result);
        }
        log.info("event=gateway_request op=set_price sku={} priceCents={} region={} idempotencyKey={}",
                body.sku(), body.priceCents(), region, idempotencyKey);
        long start = System.nanoTime();
        try {
            send(new SetPriceCommand(body.sku(), body.priceCents(), body.currency(), region, idempotencyKey),
                    idempotencyKey);
            result.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
            return ResponseEntity.status(201).body(result);
        } catch (Exception e) {
            return failure("set_price", body.sku(), idempotencyKey, e, result, start);
        }
    }

    /**
     * A price as one regional database holds it. Without {@code db}: the
     * caller's cell, through the query bus. With {@code db=<name>} (a
     * {@code DATA_TARGETS} entry, e.g. {@code eu}, {@code am}, {@code apac}):
     * that database, read directly, to check the fan-out reached it.
     */
    @GetMapping("/catalog/prices/{sku}")
    public ResponseEntity<Object> price(@PathVariable("sku") String sku,
                                        @RequestParam(value = "db", required = false) String db) {
        if (db == null || db.isBlank()) {
            return query(new FindPriceQuery(sku), Map.of("sku", sku));
        }
        var name = db.trim().toLowerCase(Locale.ROOT);
        var reader = priceReaders.get(name);
        if (reader == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown db " + db,
                    "kind", "bad_request", "available", priceReaders.names()));
        }
        try {
            return reader.find(sku)
                    .<ResponseEntity<Object>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                            "error", "price not in " + name, "sku", sku, "db", name, "kind", "not_found")));
        } catch (Exception e) {
            var f = Failure.of(e);
            return ResponseEntity.status(500).body(Map.of("error", f.message(), "kind", f.kind(), "db", name));
        }
    }

    private <T extends Single<?>> ResponseEntity<Object> query(Query<T> q, Map<String, Object> ids) {
        try {
            var single = bundle().getQueryGateway()
                    .query(q, deployment.commandTimeoutMs(), TimeUnit.MILLISECONDS)
                    .get();
            return ResponseEntity.ok(single.getData());
        } catch (Exception e) {
            var f = Failure.of(e);
            var out = new LinkedHashMap<String, Object>(ids);
            out.put("error", f.message());
            out.put("kind", f.kind());
            out.put("region", region);
            return ResponseEntity.status(f.status()).body(out);
        }
    }

    /** Sends with {@code idempotencyKey} as the Evento idempotency key (when supported); returns the event. */
    private Object send(Command command, String idempotencyKey) throws Exception {
        return runner.features()
                .send(bundle().getCommandGateway(), command, idempotencyKey, deployment.commandTimeoutMs())
                .get();
    }

    private EventoBundle bundle() {
        return runner.bundle()
                .orElseThrow(() -> new IllegalStateException("not connected: bundle not started yet ("
                        + runner.lastError() + ")"));
    }

    private Map<String, Object> base(String orderId) {
        var m = new LinkedHashMap<String, Object>();
        m.put("orderId", orderId);
        m.put("region", region);
        m.put("cell", deployment.cell());
        m.put("instance", deployment.instanceId());
        return m;
    }

    private ResponseEntity<Map<String, Object>> failure(String op, String id, String idempotencyKey,
                                                        Exception e, Map<String, Object> result, long start) {
        var f = Failure.of(e);
        long ms = (System.nanoTime() - start) / 1_000_000;
        log.warn("event=gateway_command_failed op={} orderId={} region={} kind={} latencyMs={} idempotencyKey={} error={}",
                op, id, region, f.kind(), ms, idempotencyKey, f.message());
        result.put("error", f.message());
        result.put("errorType", f.type());
        result.put("kind", f.kind());
        result.put("latencyMs", ms);
        // not connected yet: let the LB fail over to a sibling gateway of the cell
        var status = runner.bundle().isEmpty() ? 503 : f.status();
        return ResponseEntity.status(status).body(result);
    }
}
