package com.evento.server.es;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** The head notifier announces a head that moved, once, with the names of the events behind it. */
class HeadNotifierTest {

    private final AtomicLong head = new AtomicLong(0);
    private final List<HeadNotifier.Notice> announced = new CopyOnWriteArrayList<>();
    private final List<long[]> rangesRead = new CopyOnWriteArrayList<>();

    private List<String> names(long after, long upTo) {
        rangesRead.add(new long[]{after, upTo});
        return List.of("E" + upTo);
    }

    private HeadNotifier notifier() {
        return new HeadNotifier(head::get, this::names, () -> 0, announced::add, 0);
    }

    @Test
    void announcesEachNewHeadOnceWithTheNamesOfTheEventsSinceTheLastNotice() {
        var n = notifier();
        n.tick();                       // the head the server found at start: nobody waits for it
        n.tick();
        head.set(3);
        n.tick();
        n.tick();                       // still 3
        head.set(5);
        n.tick();
        assertThat(announced).extracting(HeadNotifier.Notice::head).containsExactly(3L, 5L);
        assertThat(announced).extracting(HeadNotifier.Notice::eventNames)
                .containsExactly(List.of("E3"), List.of("E5"));
        assertThat(rangesRead).extracting(r -> r[0] + ".." + r[1]).containsExactly("0..3", "3..5");
    }

    @Test
    void aLongRangeNamesNothingAndWakesEveryConsumer() {
        var n = notifier();
        n.tick();
        head.set(HeadNotifier.MAX_NAMED_RANGE + 1);
        n.tick();
        assertThat(rangesRead).isEmpty();
        assertThat(announced.getFirst().eventNames()).isEmpty();
    }

    @Test
    void theNoticeCarriesTheHeadThenTheNamesOnePerLine() {
        var bytes = new HeadNotifier.Notice(42, List.of("OrderCreatedEvent", "OrderPaidEvent")).encode();
        var buf = ByteBuffer.wrap(bytes);
        assertThat(buf.getLong()).isEqualTo(42);
        var rest = new byte[buf.remaining()];
        buf.get(rest);
        assertThat(new String(rest, StandardCharsets.UTF_8)).isEqualTo("OrderCreatedEvent\nOrderPaidEvent");
    }

    @Test
    void aFailedReadAnnouncesNothingAndTheNextOneCatchesUp() {
        var failing = new java.util.concurrent.atomic.AtomicBoolean();
        var n = new HeadNotifier(() -> {
            if (failing.get()) throw new IllegalStateException("database away");
            return head.get();
        }, this::names, () -> 0, announced::add, 0);
        n.tick();
        failing.set(true);
        head.set(7);
        n.tick();
        assertThat(announced).isEmpty();
        failing.set(false);
        n.tick();
        assertThat(announced).extracting(HeadNotifier.Notice::head).containsExactly(7L);
    }

    @Test
    void namesThatCannotBeReadWakeEveryConsumer() {
        var n = new HeadNotifier(head::get, (a, b) -> { throw new IllegalStateException("x"); },
                () -> 0, announced::add, 0);
        n.tick();
        head.set(2);
        n.tick();
        assertThat(announced).hasSize(1);
        assertThat(announced.getFirst().eventNames()).isEmpty();
    }

    @Test
    void aFailingAnnouncementDoesNotStopTheNotifier() {
        var calls = new AtomicLong();
        var n = new HeadNotifier(head::get, this::names, () -> 0, notice -> {
            calls.incrementAndGet();
            throw new IllegalStateException("session gone");
        }, 0);
        n.tick();
        head.set(1);
        n.tick();
        head.set(2);
        n.tick();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void aNonPositiveIntervalTurnsItOff() {
        assertThat(HeadNotifier.enabled(0)).isFalse();
        assertThat(HeadNotifier.enabled(-1)).isFalse();
        assertThat(HeadNotifier.enabled(50)).isTrue();
    }
}
