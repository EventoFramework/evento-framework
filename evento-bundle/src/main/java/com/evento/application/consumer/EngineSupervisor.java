package com.evento.application.consumer;

import com.evento.application.consumer.ConsumerHandle;
import com.evento.common.modeling.bundle.types.ComponentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lifecycle owner for the v2 engines.
 *
 * <p>v1 spawned one platform {@code Thread.start()} per consumer; this supervisor
 * runs every engine on a shared virtual-thread executor. Each engine implements
 * {@link Runnable} and loops until {@link #isShuttingDown()} reports true.
 *
 * <h2>Why not a single {@code StructuredTaskScope}?</h2>
 * <p>JDK 25's {@code StructuredTaskScope.open()} is owner-thread-scoped: the
 * scope must be opened and closed from the same thread, and {@code close()}
 * auto-joins all subtasks. That fits short-lived parallel fan-out (e.g.
 * processing one batch in parallel), not long-running background loops that
 * outlive the bundle's startup method. The PLAN's "structured concurrency"
 * intent — <em>bounded shutdown, explicit lifecycle, no orphan threads</em> —
 * is delivered here by a virtual-thread executor with deadlined {@code shutdown
 * → awaitTermination → shutdownNow} on stop. Engines that want bounded
 * parallel fan-out within a batch can still open a scope inside their own
 * {@code run()} body.
 *
 * <p>The supervisor is also the source for {@link ConsumerHandle} resolution
 * by {@link ComponentType} — that's the bridge point for
 * {@code BundleAdminRequestHandler.ConsumerLookup} when the v2 path is wired.
 */
public final class EngineSupervisor {

    private static final Logger logger = LogManager.getLogger(EngineSupervisor.class);

    private final List<ProjectorEngine> projectorEngines = new ArrayList<>();
    private final List<SagaEngine> sagaEngines = new ArrayList<>();
    private final List<ObserverEngine> observerEngines = new ArrayList<>();

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private ExecutorService executor;

    public EngineSupervisor() {}

    /** Caller-supplied shutdown flag — engines read this each loop iteration. */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /** Convenience supplier for engine constructors. */
    public java.util.function.Supplier<Boolean> shutdownSupplier() {
        return this::isShuttingDown;
    }

    public void addProjector(ProjectorEngine e) { projectorEngines.add(e); }
    public void addSaga(SagaEngine e) { sagaEngines.add(e); }
    public void addObserver(ObserverEngine e) { observerEngines.add(e); }

    public Collection<ProjectorEngine> getProjectorEngines() { return projectorEngines; }
    public Collection<SagaEngine> getSagaEngines() { return sagaEngines; }
    public Collection<ObserverEngine> getObserverEngines() { return observerEngines; }

    /**
     * Look up a consumer by id + component type — fans out across the three
     * engine lists. Used by {@code BundleAdminRequestHandler.ConsumerLookup}.
     */
    public Optional<? extends ConsumerHandle> findConsumer(String consumerId, ComponentType componentType) {
        return switch (componentType) {
            case Saga -> sagaEngines.stream()
                    .filter(c -> c.getConsumerId().equals(consumerId))
                    .map(c -> (ConsumerHandle) c)
                    .findFirst();
            case Projector -> projectorEngines.stream()
                    .filter(c -> c.getConsumerId().equals(consumerId))
                    .map(c -> (ConsumerHandle) c)
                    .findFirst();
            case Observer -> observerEngines.stream()
                    .filter(c -> c.getConsumerId().equals(consumerId))
                    .map(c -> (ConsumerHandle) c)
                    .findFirst();
            case null, default -> Optional.empty();
        };
    }

    /**
     * Fork every projector engine onto the virtual-thread executor. v1 starts
     * projectors first (separately from sagas + observers) because the bundle
     * registration semaphore is only released once every projector has caught
     * up to head — we preserve that two-phase startup.
     */
    public void startProjectorEngines() {
        ensureExecutor();
        for (var e : projectorEngines) {
            executor.submit(wrap(e, e.getConsumerId()));
        }
    }

    /** Fork every saga + observer engine onto the executor. Called after the head-reached gate. */
    public void startSagaAndObserverEngines() {
        ensureExecutor();
        for (var e : sagaEngines) {
            executor.submit(wrap(e, e.getConsumerId()));
        }
        for (var e : observerEngines) {
            executor.submit(wrap(e, e.getConsumerId()));
        }
    }

    /**
     * Request all engines stop and block until they do, up to {@code deadline}.
     * If the deadline elapses, the executor is force-interrupted via
     * {@code shutdownNow()} — engines block in {@code Sleep.apply(...)} which
     * is interruptible, so this terminates cleanly even from deep inside a
     * sleep window.
     */
    public void stop(Duration deadline) {
        if (!shuttingDown.compareAndSet(false, true)) {
            return; // already stopping
        }
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(deadline.toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warn("engines did not exit within {} — interrupting", deadline);
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.error("engines did not exit after interrupt");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void ensureExecutor() {
        if (executor == null) {
            executor = Executors.newVirtualThreadPerTaskExecutor();
        }
    }

    private static Runnable wrap(Runnable r, String consumerId) {
        return () -> {
            Thread.currentThread().setName("evento-v2-" + consumerId);
            try {
                r.run();
            } catch (Throwable t) {
                logger.error("engine {} crashed", consumerId, t);
            }
        };
    }
}
