package com.evento.server.es.pg.outcome;

import com.evento.common.messaging.outcome.ServiceCommandOutcomes;
import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A payment provider for {@code ServiceOutcomeReplayPostgresIT}: its side effect is a row in
 * {@code it_payments} (counted by {@code attempts}), and it records its outcome in the same
 * transaction ({@link ServiceCommandOutcomes#recordWith}), the documented exactly-once binding.
 */
@Service
public class OutcomePaymentService {

    /** Handler executions in this JVM (both the committed and the rolled-back ones). */
    public static final AtomicInteger EXECUTIONS = new AtomicInteger();

    private final DataSource dataSource;

    public OutcomePaymentService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @CommandHandler
    OutcomePaidEvent handle(OutcomePayCommand cmd, ServiceCommandOutcomes outcomes) throws Exception {
        EXECUTIONS.incrementAndGet();
        try (var con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (var ps = con.prepareStatement("INSERT INTO it_payments AS p (order_id, idempotency_key) VALUES (?, ?) "
                        + "ON CONFLICT (order_id) DO UPDATE SET attempts = p.attempts + 1")) {
                    ps.setString(1, cmd.getOrderId());
                    ps.setString(2, outcomes.idempotencyKey());
                    ps.executeUpdate();
                }
                var event = new OutcomePaidEvent(cmd.getOrderId(), UUID.randomUUID().toString());
                outcomes.recordWith(con, event);
                con.commit();
                return event;
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }
}
