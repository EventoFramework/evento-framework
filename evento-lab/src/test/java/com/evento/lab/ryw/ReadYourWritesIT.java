package com.evento.lab.ryw;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.messaging.gateway.QueryOptions;
import com.evento.common.modeling.exceptions.ConsistencyTimeoutException;
import com.evento.common.modeling.messaging.query.ConsistencyToken;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.support.CommandAwareEmbeddedBroker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Read-your-writes: a command reports where its event was stored, and a query that
 * requires that position is answered only once the answering bundle's projector has
 * processed it — or fails explicitly when it does not in time.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class ReadYourWritesIT {

    private CommandAwareEmbeddedBroker broker;
    private CommandAwareEmbeddedBroker.TestGatewayClient caller;
    private EventoBundle bundle;

    @BeforeEach
    void setUp() throws Exception {
        RywStore.reset();
        broker = new CommandAwareEmbeddedBroker();
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(RywStore.class.getPackage())
                .setBundleId("ryw-bundle")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> broker.lifecycle().isBundleAvailable("ryw-bundle"));
        caller = broker.newGatewayClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        RywStore.gate.countDown();
        if (caller != null) caller.close();
        if (bundle != null) bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
        if (broker != null) broker.close();
    }

    private Single<NoteView> find(String id, QueryOptions options) throws Exception {
        return caller.queryGateway().queryWith(new FindNoteQuery(id), options).get(30, TimeUnit.SECONDS);
    }

    @Test
    void aQueryAfterTheCommandsTokenWaitsForTheProjectorAndSeesTheWrite() throws Exception {
        RywStore.gate = new CountDownLatch(1);  // the projector lags

        var tracked = caller.commandGateway().<NoteAddedEvent>sendTracked(new AddNoteCommand("n-1", "hello"))
                .get(30, TimeUnit.SECONDS);
        assertThat(tracked.result().getText()).isEqualTo("hello");
        assertThat(tracked.token().isEmpty()).isFalse();

        // Without the requirement the read model answers as it is: stale.
        assertThat(find("n-1", QueryOptions.defaults()).getData()).isNull();

        // With it, the answer waits for the projector...
        var consistent = caller.queryGateway().queryWith(new FindNoteQuery("n-1"),
                QueryOptions.defaults().after(tracked.token()).timeout(Duration.ofSeconds(20)));
        Thread.sleep(500);
        assertThat(consistent).isNotDone();

        // ...and sees the write once the projector has processed it.
        RywStore.gate.countDown();
        assertThat(consistent.get(20, TimeUnit.SECONDS).getData().getText()).isEqualTo("hello");
    }

    @Test
    void aTokenTravelsAsTextBetweenProcesses() throws Exception {
        RywStore.gate = new CountDownLatch(1);
        var token = caller.commandGateway().sendTracked(new AddNoteCommand("n-2", "over http"))
                .get(30, TimeUnit.SECONDS).token();

        var header = token.encode();  // e.g. sent to a browser, back with its next request
        var consistent = caller.queryGateway().queryWith(new FindNoteQuery("n-2"),
                QueryOptions.defaults().after(ConsistencyToken.parse(header)).timeout(Duration.ofSeconds(20)));
        RywStore.gate.countDown();
        assertThat(consistent.get(20, TimeUnit.SECONDS).getData().getText()).isEqualTo("over http");
    }

    @Test
    void readYourWritesCoversEveryWriteOfThisProcess() throws Exception {
        caller.commandGateway().send(new AddNoteCommand("n-3", "mine")).get(30, TimeUnit.SECONDS);

        // Asked right after the command, with no token in hand: the projector is woken up
        // instead of finishing its idle pause, and the answer reflects the write.
        long start = System.nanoTime();
        var answer = find("n-3", QueryOptions.defaults().readYourWrites().timeout(Duration.ofSeconds(20)));
        assertThat(answer.getData().getText()).isEqualTo("mine");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void aProjectorThatDoesNotCatchUpInTimeFailsTheQueryExplicitly() throws Exception {
        RywStore.gate = new CountDownLatch(1);
        var token = caller.commandGateway().sendTracked(new AddNoteCommand("n-4", "late"))
                .get(30, TimeUnit.SECONDS).token();

        var late = caller.queryGateway().queryWith(new FindNoteQuery("n-4"),
                QueryOptions.defaults().after(token).timeout(Duration.ofSeconds(1)));

        // The answering bundle gives up just before the caller's deadline and says why:
        // nothing stale is read.
        assertThatThrownBy(() -> late.get(20, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(ConsistencyTimeoutException.class)
                .hasMessageContaining("RywProjector");
    }
}
