package com.evento.application.manager;

import com.evento.application.performance.TracingAgent;
import com.evento.application.proxy.GatewayTelemetryProxy;
import com.evento.application.reference.ServiceReference;
import com.evento.common.messaging.outcome.DuplicateServiceOutcomeException;
import com.evento.common.messaging.outcome.InMemoryServiceCommandOutcomeStore;
import com.evento.common.messaging.outcome.ServiceCommandOutcomeStore;
import com.evento.common.messaging.outcome.ServiceCommandOutcomes;
import com.evento.common.messaging.outcome.StoredOutcome;
import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.common.modeling.exceptions.IdempotencyKeyMismatchException;
import com.evento.common.modeling.messaging.message.application.ServiceCommandMessage;
import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.common.performance.PerformanceInvocationsMessage;
import com.evento.common.performance.PerformanceService;
import com.evento.common.performance.PerformanceServiceTimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bundle side of the G5 residual window: a keyed service command whose key already has an
 * outcome in the {@link ServiceCommandOutcomeStore} is answered from it without running the
 * handler, which is what the broker's retry needs after a handler ran and its event was not
 * stored. (Payloads are top-level classes: the framework names commands by the last segment of
 * the class name, which for a nested class carries the {@code $}.)
 */
class ServiceManagerOutcomeStoreTest {

    private ServiceManager manager;
    private OutcomePayments payments;

    @BeforeEach
    void setUp() {
        payments = new OutcomePayments();
        manager = new ServiceManager("payments-bundle",
                (component, message) -> new GatewayTelemetryProxy(null, null, "payments-bundle", new NoMetrics(),
                        component, message, new TracingAgent("payments-bundle", 1), "i-1"),
                new TracingAgent("payments-bundle", 1),
                new MessageHandlerInterceptor() {
                });
        var reference = new ServiceReference(payments);
        for (var command : reference.getRegisteredCommands()) {
            manager.getHandlers().put(command, reference);
        }
    }

    @Test
    void aKeyedCommandRunsOnceAndItsRetryIsAnsweredFromTheStore() throws Throwable {
        var store = new InMemoryServiceCommandOutcomeStore();
        manager.setOutcomeStore(store);
        var key = "pay-" + UUID.randomUUID();

        var first = (OutcomePaid) manager.handle(keyed(new OutcomePay("o-1"), key)).getPayload();
        var retry = (OutcomePaid) manager.handle(keyed(new OutcomePay("o-1"), key)).getPayload();

        assertThat(payments.executions).hasValue(1);
        assertThat(retry.paymentNonce).as("the recorded outcome, not a new execution").isEqualTo(first.paymentNonce);
        assertThat(manager.outcomesReplayed()).isEqualTo(1);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void aVoidHandlerIsRecordedToo() throws Throwable {
        manager.setOutcomeStore(new InMemoryServiceCommandOutcomeStore());
        var key = "mail-" + UUID.randomUUID();
        assertThat(manager.handle(keyed(new OutcomeMail("o-2"), key)).getPayload()).isNull();
        var retry = manager.handle(keyed(new OutcomeMail("o-2"), key));
        assertThat(retry.getPayload()).isNull();
        assertThat(retry.getSerializedPayload().getObjectClass()).as("no event for the broker to store").isNull();
        assertThat(payments.mails).hasValue(1);
    }

    @Test
    void aKeyRecordedForAnotherCommandTypeIsRefusedAndNothingRuns() throws Throwable {
        manager.setOutcomeStore(new InMemoryServiceCommandOutcomeStore());
        var key = "shared-" + UUID.randomUUID();
        manager.handle(keyed(new OutcomePay("o-3"), key));
        assertThatThrownBy(() -> manager.handle(keyed(new OutcomeMail("o-3"), key)))
                .isInstanceOf(IdempotencyKeyMismatchException.class)
                .hasMessageContaining("OutcomePay")
                .hasMessageContaining("nothing was executed");
        assertThat(payments.mails).hasValue(0);
    }

    @Test
    void unkeyedCommandsAndBundlesWithoutAStoreRunEveryTime() throws Throwable {
        var store = new InMemoryServiceCommandOutcomeStore();
        manager.setOutcomeStore(store);
        manager.handle(new ServiceCommandMessage(new OutcomePay("o-4")));
        manager.handle(new ServiceCommandMessage(new OutcomePay("o-4")));
        assertThat(store.size()).as("nothing recorded without a key").isZero();

        manager.setOutcomeStore(null);
        var key = "pay-" + UUID.randomUUID();
        manager.handle(keyed(new OutcomePay("o-5"), key));
        manager.handle(keyed(new OutcomePay("o-5"), key));
        assertThat(payments.executions).hasValue(4);
    }

    @Test
    void aHandlerThatRecordsInItsTransactionIsNotRecordedTwice() throws Throwable {
        var store = new TransactionalStore();
        manager.setOutcomeStore(store);
        var key = "tx-" + UUID.randomUUID();
        var paid = (OutcomePaid) manager.handle(keyed(new OutcomeTxPay("o-6"), key)).getPayload();

        assertThat(store.inTransaction).hasValue(1);
        assertThat(store.afterReturn).as("the handler recorded it; the bundle does not again").hasValue(0);
        assertThat(payments.seenKeys).containsEntry("o-6", key);
        var retry = (OutcomePaid) manager.handle(keyed(new OutcomeTxPay("o-6"), key)).getPayload();
        assertThat(retry.paymentNonce).isEqualTo(paid.paymentNonce);
        assertThat(payments.executions).hasValue(1);
    }

    @Test
    void anExecutionThatLosesTheRaceOnTheOutcomeIsAnsweredWithTheFirstOne() throws Throwable {
        var store = new TransactionalStore();
        manager.setOutcomeStore(store);
        var key = "race-" + UUID.randomUUID();
        // Another instance committed the outcome while this one was running the handler.
        store.preRecordOnTransaction = new StoredOutcome(key, "OutcomeTxPay",
                ServiceCommandOutcomes.serialize(new OutcomePaid("o-7", "first")), Instant.now());

        var answer = (OutcomePaid) manager.handle(keyed(new OutcomeTxPay("o-7"), key)).getPayload();
        assertThat(answer.paymentNonce).isEqualTo("first");
    }

    @Test
    void outsideAKeyedInvocationTheHandleIsInactive() throws Exception {
        assertThat(ServiceCommandOutcomes.current().isActive()).isFalse();
        assertThat(ServiceCommandOutcomes.current().recordWith(null, new OutcomePaid("x", "y"))).isFalse();
        assertThat(ServiceCommandOutcomes.current().idempotencyKey()).isNull();
    }

    private static ServiceCommandMessage keyed(ServiceCommand command, String key) {
        var message = new ServiceCommandMessage(command);
        message.setIdempotencyKey(key);
        return message;
    }

    /** An in-memory store that pretends to join a transaction. */
    static final class TransactionalStore implements ServiceCommandOutcomeStore {
        final InMemoryServiceCommandOutcomeStore delegate = new InMemoryServiceCommandOutcomeStore();
        final AtomicInteger inTransaction = new AtomicInteger();
        final AtomicInteger afterReturn = new AtomicInteger();
        StoredOutcome preRecordOnTransaction;

        @Override
        public Optional<StoredOutcome> find(String key) {
            return delegate.find(key);
        }

        @Override
        public boolean record(String key, String commandType, byte[] serializedResult) {
            afterReturn.incrementAndGet();
            return delegate.record(key, commandType, serializedResult);
        }

        @Override
        public boolean recordInTransaction(Connection connection, String key, String commandType, byte[] result) {
            if (preRecordOnTransaction != null) {
                delegate.record(key, preRecordOnTransaction.commandType(), preRecordOnTransaction.serializedResult());
                throw new DuplicateServiceOutcomeException(key, null);
            }
            inTransaction.incrementAndGet();
            return delegate.record(key, commandType, result);
        }

        @Override
        public int sweepBefore(Instant threshold) {
            return delegate.sweepBefore(threshold);
        }
    }

    static final class NoMetrics extends PerformanceService {
        NoMetrics() {
            super(0);
        }

        @Override
        protected void sendServiceTimeMetricMessage(PerformanceServiceTimeMessage message) {
        }

        @Override
        protected void sendInvocationMetricMessage(PerformanceInvocationsMessage message) {
        }
    }
}

@Service
class OutcomePayments {
    final AtomicInteger executions = new AtomicInteger();
    final AtomicInteger mails = new AtomicInteger();
    final ConcurrentHashMap<String, String> seenKeys = new ConcurrentHashMap<>();

    @CommandHandler
    OutcomePaid handle(OutcomePay cmd) {
        executions.incrementAndGet();
        return new OutcomePaid(cmd.orderId, UUID.randomUUID().toString());
    }

    @CommandHandler
    void handle(OutcomeMail cmd) {
        mails.incrementAndGet();
    }

    @CommandHandler
    OutcomePaid handle(OutcomeTxPay cmd, ServiceCommandOutcomes outcomes) throws Exception {
        executions.incrementAndGet();
        seenKeys.put(cmd.orderId, outcomes.idempotencyKey());
        var event = new OutcomePaid(cmd.orderId, UUID.randomUUID().toString());
        outcomes.recordWith(null, event); // a real handler passes its open transaction
        return event;
    }
}

class OutcomePay extends ServiceCommand {
    public String orderId;

    public OutcomePay() {
    }

    OutcomePay(String orderId) {
        this.orderId = orderId;
    }
}

class OutcomeTxPay extends ServiceCommand {
    public String orderId;

    public OutcomeTxPay() {
    }

    OutcomeTxPay(String orderId) {
        this.orderId = orderId;
    }
}

class OutcomeMail extends ServiceCommand {
    public String orderId;

    public OutcomeMail() {
    }

    OutcomeMail(String orderId) {
        this.orderId = orderId;
    }
}

class OutcomePaid extends ServiceEvent {
    public String orderId;
    public String paymentNonce;

    public OutcomePaid() {
    }

    OutcomePaid(String orderId, String paymentNonce) {
        this.orderId = orderId;
        this.paymentNonce = paymentNonce;
    }
}
