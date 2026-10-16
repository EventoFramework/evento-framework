package com.evento.common.messaging.gateway;

import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.messaging.message.application.QueryMessage;
import com.evento.common.modeling.messaging.message.internal.EventoRequest;
import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.ConsistencyToken;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.common.utils.Context;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsistencyAndOptionsTest {

    public static class ThingView implements com.evento.common.modeling.messaging.payload.View {
    }

    public static class FindThing extends Query<Single<ThingView>> {
    }

    @Test
    void tokensKeepTheHighestPositionPerContext() {
        var token = ConsistencyToken.of("EU", 10)
                .merge(ConsistencyToken.of("US", 7))
                .merge(ConsistencyToken.of("EU", 4));
        assertThat(token.positions()).containsEntry("EU", 10L).containsEntry("US", 7L);
        assertThat(token.requiredFor("EU")).isEqualTo(10);
        assertThat(token.requiredFor("ASIA")).isEqualTo(-1);
        // A projector reading every context waits for the highest position.
        assertThat(token.requiredFor(Context.WILDCARD)).isEqualTo(10);
        assertThat(ConsistencyToken.of(null, 3).positions()).containsEntry(Context.DEFAULT, 3L);
    }

    @Test
    void aTokenRoundTripsThroughItsTextFormEvenWithOddContextNames() {
        var token = ConsistencyToken.of("eu west, 1:a", 42).merge(ConsistencyToken.of("US", 9));
        assertThat(ConsistencyToken.parse(token.encode())).isEqualTo(token);
        assertThat(ConsistencyToken.parse(null)).isEqualTo(ConsistencyToken.NONE);
        assertThatThrownBy(() -> ConsistencyToken.parse("EU")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aQueryCarriesItsTokenThroughTheBundleCodec() {
        var codec = new AdminPayloadCodec();
        var message = new QueryMessage<>(new FindThing());
        message.setConsistency(ConsistencyToken.of("EU", 5));
        var request = new EventoRequest();
        request.setBody(message);

        var decoded = (QueryMessage<?>) codec.decodeRequest(codec.encodeRequest(request)).getBody();
        assertThat(decoded.getConsistency()).isEqualTo(ConsistencyToken.of("EU", 5));

        message.setConsistency(ConsistencyToken.NONE);
        assertThat(message.getConsistency()).isNull();
    }

    @Test
    void optionsAreImmutableAndValidated() {
        var base = QueryOptions.defaults();
        var tuned = base.timeout(Duration.ofSeconds(3)).window(4).direct().readYourWrites();
        assertThat(base.getTimeout()).isNull();
        assertThat(base.requiresConsistency()).isFalse();
        assertThat(tuned.getWindow()).isEqualTo(4);
        assertThat(tuned.isDirect()).isTrue();
        assertThat(tuned.requiresConsistency()).isTrue();
        assertThatThrownBy(() -> base.window(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base.timeout(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aGatewayWrittenBeforeOptionsRefusesAConsistencyRequirementRatherThanDropIt() {
        QueryGateway legacy = new QueryGateway() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends com.evento.common.modeling.messaging.query.QueryResponse<?>> java.util.concurrent.CompletableFuture<T> query(
                    Query<T> query, com.evento.common.modeling.messaging.message.application.Metadata metadata,
                    com.evento.common.modeling.messaging.message.application.Message<?> handledMessage,
                    long timeout, TimeUnit unit) {
                return java.util.concurrent.CompletableFuture.completedFuture((T) Single.of(null));
            }
        };
        assertThat(legacy.queryWith(new FindThing(), QueryOptions.defaults())).isCompleted();
        assertThatThrownBy(() -> legacy.queryWith(new FindThing(), QueryOptions.defaults().readYourWrites()).get())
                .isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(UnsupportedOperationException.class);

        // And streams by asking the plain way.
        try (var views = legacy.stream(new FindThing())) {
            assertThat(views.stream().count()).isZero();
        }
    }
}
