package com.evento.server.web;

import com.evento.server.web.spi.AdminActionListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Runs the console's state-changing actions and tells the {@link AdminActionListener}s about each:
 * when it starts, and when it ends with its real outcome, also for the actions the bundles answer
 * asynchronously. Without listeners the action just runs.
 */
@Slf4j
@Component
public class AdminActions {

    private final ObjectProvider<AdminActionListener> listeners;

    public AdminActions(ObjectProvider<AdminActionListener> listeners) {
        this.listeners = listeners;
    }

    /** Runs a synchronous action. */
    public void run(String action, Map<String, ?> target, Runnable call) {
        var started = start(action, target);
        try {
            call.run();
        } catch (RuntimeException e) {
            finish(started, e);
            throw e;
        }
        finish(started, null);
    }

    /** Runs an action answered asynchronously; the listeners hear of its end when the answer arrives. */
    public <T> CompletableFuture<T> call(String action, Map<String, ?> target,
                                         Callable<CompletableFuture<T>> call) throws Exception {
        var started = start(action, target);
        CompletableFuture<T> result;
        try {
            result = call.call();
        } catch (Exception e) {
            finish(started, e);
            throw e;
        }
        return result.whenComplete((value, error) -> finish(started, error));
    }

    /** A listener that cannot start refuses the action: an audit that cannot record must not be skipped. */
    private List<AdminActionListener.Completion> start(String action, Map<String, ?> target) {
        Map<String, Object> copy = Collections.unmodifiableMap(new LinkedHashMap<>(target));
        return listeners.orderedStream().map(l -> l.started(action, copy)).toList();
    }

    /** The action has already happened: a listener failing here is logged, the outcome is not changed. */
    private static void finish(List<AdminActionListener.Completion> started, Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        for (var completion : started) {
            try {
                completion.finished(error);
            } catch (RuntimeException e) {
                log.error("An admin action listener failed on completion", e);
            }
        }
    }
}
