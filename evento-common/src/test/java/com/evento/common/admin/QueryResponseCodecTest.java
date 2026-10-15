package com.evento.common.admin;

import com.evento.common.modeling.messaging.payload.View;
import com.evento.common.modeling.messaging.query.Page;
import com.evento.common.modeling.messaging.query.PageRequest;
import com.evento.common.modeling.messaging.query.Streamed;
import com.evento.common.modeling.messaging.message.internal.EventoResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link Page} and {@link Streamed} travel through the bundle codec like {@code Multiple}. */
class QueryResponseCodecTest {

    public static class OrderView implements View {
        private String id;
        public OrderView() {}
        public OrderView(String id) { this.id = id; }
        public String getId() { return id; }
    }

    private final AdminPayloadCodec codec = new AdminPayloadCodec();

    private Object roundTrip(java.io.Serializable body) {
        var response = new EventoResponse();
        response.setCorrelationId("c-1");
        response.setBody(body);
        return codec.decodeResponse(codec.encodeResponse(response)).getBody();
    }

    @Test
    void anOffsetPageRoundTripsWithItsPosition() {
        var page = Page.of(List.of(new OrderView("a"), new OrderView("b")), 3, 2, 11);
        @SuppressWarnings("unchecked")
        var decoded = (Page<OrderView>) roundTrip(page);

        assertThat(decoded.getData()).extracting(OrderView::getId).containsExactly("a", "b");
        assertThat(decoded.getNumber()).isEqualTo(3);
        assertThat(decoded.getTotalElements()).isEqualTo(11);
        assertThat(decoded.getTotalPages()).isEqualTo(6);
        assertThat(decoded.hasNext()).isTrue();
        assertThat(decoded.next()).isEqualTo(PageRequest.of(4, 2));
    }

    @Test
    void aCursorPageEndsWhenItHasNoNextCursor() {
        @SuppressWarnings("unchecked")
        var middle = (Page<OrderView>) roundTrip(Page.ofCursor(List.of(new OrderView("a")), "after-a"));
        assertThat(middle.isCursorPaged()).isTrue();
        assertThat(middle.next()).isEqualTo(PageRequest.after("after-a", 1));

        @SuppressWarnings("unchecked")
        var last = (Page<OrderView>) roundTrip(Page.ofCursor(List.of(new OrderView("z")), null));
        assertThat(last.hasNext()).isFalse();
        assertThat(last.next()).isNull();
    }

    @Test
    void anOffsetPageWithoutATotalHasANextOnlyWhenFull() {
        assertThat(Page.of(List.of(new OrderView("a"), new OrderView("b")), 0, 2, -1).hasNext()).isTrue();
        assertThat(Page.of(List.of(new OrderView("a")), 0, 2, -1).hasNext()).isFalse();
    }

    @Test
    void aLazyStreamedIsReadOnceAndClosedWhenMaterializedForAPlainReply() {
        var closed = new AtomicBoolean();
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        var streamed = Streamed.of(() -> {
            opened.incrementAndGet();
            return Stream.of(new OrderView("a"), new OrderView("b")).onClose(() -> closed.set(true));
        });
        assertThat(streamed.isLazy()).isTrue();
        assertThat(opened).hasValue(0);

        @SuppressWarnings("unchecked")
        var decoded = (Streamed<OrderView>) roundTrip(streamed.materialize());

        assertThat(opened).hasValue(1);
        assertThat(closed).isTrue();
        assertThat(decoded.isLazy()).isFalse();
        assertThat(decoded.getData()).extracting(OrderView::getId).containsExactly("a", "b");
        try (var views = decoded.open()) {
            assertThat(views.map(OrderView::getId)).containsExactly("a", "b");
        }
    }
}
