package com.evento.server.web;

import com.evento.server.web.spi.AdminActionListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminActionsTest {

    final List<String> heard = new ArrayList<>();

    AdminActions actions(AdminActionListener... listeners) {
        var beans = new StaticListableBeanFactory();
        for (int i = 0; i < listeners.length; i++) beans.addBean("listener" + i, listeners[i]);
        return new AdminActions(beans.getBeanProvider(AdminActionListener.class));
    }

    AdminActionListener recording() {
        return (action, target) -> {
            heard.add("start " + action + " " + target);
            return error -> heard.add("end " + action + " " + (error == null ? "ok" : error.getMessage()));
        };
    }

    @Test
    void withoutListenersTheActionJustRuns() throws Exception {
        var ran = new ArrayList<String>();
        actions().run("bundle.unregister", Map.of("bundleId", "b"), () -> ran.add("b"));
        assertThat(actions().call("x", Map.of(), () -> CompletableFuture.completedFuture(1)).join()).isEqualTo(1);
        assertThat(ran).containsExactly("b");
    }

    @Test
    void listenersHearTheStartAndTheOutcome() {
        actions(recording()).run("bundle.unregister", Map.of("bundleId", "b"), () -> heard.add("running"));
        assertThatThrownBy(() -> actions(recording()).run("bundle.unregister", Map.of("bundleId", "c"), () -> {
            throw new IllegalArgumentException("no such bundle");
        })).hasMessage("no such bundle");

        assertThat(heard).containsExactly(
                "start bundle.unregister {bundleId=b}", "running", "end bundle.unregister ok",
                "start bundle.unregister {bundleId=c}", "end bundle.unregister no such bundle");
    }

    @Test
    void anAsynchronousActionEndsWhenTheAnswerArrives() throws Exception {
        var answer = new CompletableFuture<String>();
        var result = actions(recording()).call("consumer.dead_queue.consume", Map.of("consumerId", "c1"), () -> answer);
        assertThat(heard).containsExactly("start consumer.dead_queue.consume {consumerId=c1}");

        answer.completeExceptionally(new IllegalStateException("no instance of c1 is up"));
        assertThat(result).isCompletedExceptionally();
        assertThat(heard).last().isEqualTo("end consumer.dead_queue.consume no instance of c1 is up");
    }

    @Test
    void aListenerThatCannotStartRefusesTheAction() {
        var ran = new ArrayList<String>();
        AdminActionListener broken = (action, target) -> {
            throw new IllegalStateException("audit store down");
        };
        assertThatThrownBy(() -> actions(broken).run("bundle.unregister", Map.of(), () -> ran.add("x")))
                .hasMessage("audit store down");
        assertThat(ran).isEmpty();
    }

    @Test
    void aListenerFailingAtTheEndDoesNotChangeTheOutcome() {
        AdminActionListener failsAtEnd = (action, target) -> error -> {
            throw new IllegalStateException("late");
        };
        actions(failsAtEnd, recording()).run("bundle.unregister", Map.of(), () -> {
        });
        assertThat(heard).last().isEqualTo("end bundle.unregister ok");
    }
}
