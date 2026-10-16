package com.evento.server.bus.correlation;

import com.evento.transport.message.Response;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ForwardingDedupCacheTest {

    private static Response sized(UUID id, int bytes) {
        return Response.success(id, "Cmd", new byte[bytes]);
    }

    @Test
    void aDuplicateOfAnAnsweredRequestReplaysItsResponse() {
        var cache = new ForwardingDedupCache(100, Duration.ofMinutes(1));
        var id = UUID.randomUUID();
        assertThat(cache.resolveOrClaim(id)).isInstanceOf(ForwardingDedupCache.Outcome.Claimed.class);
        assertThat(cache.resolveOrClaim(id)).isInstanceOf(ForwardingDedupCache.Outcome.InFlight.class);
        var response = sized(id, 10);
        cache.recordResponse(id, response);
        var again = cache.resolveOrClaim(id);
        assertThat(again).isInstanceOf(ForwardingDedupCache.Outcome.Replay.class);
        assertThat(((ForwardingDedupCache.Outcome.Replay) again).response()).isSameAs(response);
    }

    @Test
    void anOversizedResponseIsNotRetainedButItsDuplicateIsNotExecutedAgain() {
        var cache = new ForwardingDedupCache(100, Duration.ofMinutes(1), 10_000, 1_000);
        var id = UUID.randomUUID();
        cache.resolveOrClaim(id);
        cache.recordResponse(id, sized(id, 5_000));
        assertThat(cache.retainedBytes()).isZero();

        var again = cache.resolveOrClaim(id);
        assertThat(again).isInstanceOf(ForwardingDedupCache.Outcome.Replay.class);
        var replay = ((ForwardingDedupCache.Outcome.Replay) again).response();
        assertThat(replay.isError()).isTrue();
        assertThat(replay.error().exceptionClassName())
                .isEqualTo(ForwardingDedupCache.DuplicateRequestException.class.getName());
    }

    @Test
    void retainedResponsesStayWithinTheByteBudgetLeastRecentlyUsedFirst() {
        var cache = new ForwardingDedupCache(100, Duration.ofMinutes(1), 3_000, 2_000);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var third = UUID.randomUUID();
        for (var id : new UUID[]{first, second, third}) {
            cache.resolveOrClaim(id);
            cache.recordResponse(id, sized(id, 1_000));
        }
        // Three responses of ~1 KB each exceed 3 000 bytes with their overhead: the
        // oldest loses its bytes, never its slot.
        assertThat(cache.retainedBytes()).isLessThanOrEqualTo(3_000);
        assertThat(cache.size()).isEqualTo(3);
        var oldest = cache.resolveOrClaim(first);
        assertThat(((ForwardingDedupCache.Outcome.Replay) oldest).response().isError()).isTrue();
        var newest = cache.resolveOrClaim(third);
        assertThat(((ForwardingDedupCache.Outcome.Replay) newest).response().isError()).isFalse();
    }

    @Test
    void invalidateReleasesTheRetainedBytes() {
        var cache = new ForwardingDedupCache(100, Duration.ofMinutes(1));
        var id = UUID.randomUUID();
        cache.resolveOrClaim(id);
        cache.recordResponse(id, sized(id, 4_000));
        assertThat(cache.retainedBytes()).isPositive();
        cache.invalidate(id);
        assertThat(cache.retainedBytes()).isZero();
        assertThat(cache.size()).isZero();
    }

    @Test
    void theCountBoundReleasesTheBytesOfWhatItEvicts() {
        var cache = new ForwardingDedupCache(2, Duration.ofMinutes(1));
        for (int i = 0; i < 10; i++) {
            var id = UUID.randomUUID();
            cache.resolveOrClaim(id);
            cache.recordResponse(id, sized(id, 100));
        }
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.retainedBytes()).isEqualTo(2 * (64 + 100));
    }
}
