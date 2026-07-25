# Async Consumers — parallel event handling with bounded executors

**Status: complete — Phases 1–4 shipped** (see §12 / §13 / §14 / §15 for what landed and
how it deviated from this plan). **Module surface:** `evento-common`, `evento-bundle`
(+ `evento-server`/`evento-gui` in Phase 3). **Wire protocol:** unchanged in Phase 1.

---

## 1. Goal

Today every consumer is strictly serial: fetch N events by context → hand each event to the
handler → **wait for the handler to return** → commit checkpoint → next event. Throughput is
bounded by handler latency, which is wrong for handlers whose effect is idempotent or a blind
overwrite (upsert a read model row, push to a cache, fire-and-forget a notification).

The feature lets such handlers opt into **parallel consumption** by naming an executor on the
handler annotation:

```java
@Projector(version = 1)
public class OrderViewProjector {

    // unchanged: serial, one at a time, checkpoint after completion
    @EventHandler
    public void on(OrderCreated e) { … }

    // new: dispatched to the "read-model" executor, up to its concurrency limit
    @EventHandler(executor = "read-model", retry = 5, retryDelay = 500)
    public void on(OrderTotalRecomputed e) { … }
}
```

Executors are declared **at the bundle**, in the same shape as component contexts:

```java
EventoBundle.Builder.builder()
    .setComponentContexts(OrderViewProjector.class, "ctx-a", "ctx-b")   // existing
    .addConsumerExecutor("read-model", ConsumerExecutors.virtual(64))   // new
    .addConsumerExecutor("slow-io",    ConsumerExecutors.pooled(8))     // new
    …
```

Default (`executor = ""`) is today's inline path — **existing bundles see no behaviour change**.

---

## 2. Core mechanic: checkpoint on *start*, not on completion

The requested semantic, and the reason it is the right one:

> the consumer does not register the event as consumed until the execution **starts**.

This is what gives the design its backpressure. The fetch loop, per event, does:

```
submit(task) ──blocks until a concurrency permit is free (= task has started)──► commit checkpoint
```

An executor with capacity `P` therefore lets the loop run at most `P` events ahead of completion,
and never enqueues an unbounded prefix of the event store. If no permit frees within a timeout,
the cycle **ends** at the last started event, the consumer lock is released, and the next cycle
re-fetches from the checkpoint. Nothing is lost and nothing is buffered.

### The property this buys, stated precisely

| Event state on crash | Outcome |
|---|---|
| Not yet submitted | Not checkpointed → **redelivered** |
| Submitted, waiting for a permit | Not checkpointed → **redelivered** |
| Started, still running | Checkpointed → **not redelivered — lost** |
| Completed | Checkpointed → not redelivered (correct) |

**The crash-loss window is exactly the set of concurrently-executing tasks** (≤ `P` per executor),
not the queue depth. That is the whole reason to gate on *start* rather than on *enqueue* — gating
on enqueue would make the loss window the queue size.

⚠️ **Flag this loudly in the docs.** Async mode downgrades the delivery guarantee from
at-least-once to *at-most-once for the in-flight window*. "Idempotent" protects against
duplicates, not against loss. Two mitigations, both in scope:

- **Graceful shutdown drains** (§6) — turns the normal stop path into zero loss; only a `kill -9`
  or a host failure can hit the window.
- **Optional `CheckpointMode.WATERMARK`** (§9, deferred) — commit the highest *contiguous
  completed* sequence instead. Restores at-least-once at the cost of replaying up to `P` events
  after a crash. Costs nothing extra at runtime because we already gate on start; it is purely a
  different commit rule. Recommend offering it, defaulting to `ON_START` as specified.

---

## 3. Ordering — what breaks and what to say about it

Parallel dispatch means events for the **same aggregate** can be applied out of sequence order.
This is unavoidable and is why the feature is scoped to idempotent/overwrite handlers. Rules:

1. **Same-consumer, mixed handlers.** A projector may have some inline and some async handlers.
   When the loop reaches an event whose handler is **inline** and async tasks from earlier events
   are still in flight, it **awaits quiescence of this consumer's in-flight tasks first**. Without
   this, a serial handler could observe state from before an earlier event was applied — a
   surprise that would be very hard to debug. Cheap to implement, so do it.
2. **No cross-event ordering guarantee** between two async events, even for the same aggregate.
   Documented, enforced by nothing.
3. **Sagas are excluded** (§5). Saga handlers are a read-modify-write on shared saga state;
   parallel dispatch would corrupt it.
4. Key-partitioned ordering (same aggregate → same lane) is the natural extension and would make
   the feature safe for far more handlers. Deferred to §9, not Phase 1.

---

## 4. New SPI: `ConsumerExecutor`

`java.util.concurrent.Executor` is insufficient — we need "did it start?", "wait until idle", and a
bounded submit. New interface in `evento-common`
(`com.evento.common.messaging.consumer.ConsumerExecutor`):

```java
public interface ConsumerExecutor extends AutoCloseable {

    String name();

    /**
     * Run {@code task} as soon as capacity allows. The returned future completes when the
     * task has BEGUN executing (not when it finishes) — that is the checkpoint trigger.
     * Returns empty if no capacity became available within {@code waitFor}.
     */
    Optional<CompletableFuture<Void>> submit(Runnable task, Duration waitFor) throws InterruptedException;

    /** In-flight (started, not finished) task count. */
    int inFlight();

    /** Block until every started task has finished, or the deadline elapses. */
    boolean awaitQuiescence(Duration deadline) throws InterruptedException;

    /** Stop accepting work; await in-flight completion up to the deadline. */
    void shutdown(Duration deadline);
}
```

Implementations (`com.evento.common.messaging.consumer.impl`, factories in `ConsumerExecutors`):

| Factory | Backing | Use |
|---|---|---|
| `ConsumerExecutors.virtual(maxConcurrency)` | `Semaphore` + virtual thread per task | **Default recommendation.** I/O-bound handlers; permit acquisition *is* the start signal |
| `ConsumerExecutors.pooled(threads)` | Fixed platform pool, synchronous handoff | CPU-bound or handlers pinning a native/DB resource |
| `ConsumerExecutors.inline()` | Caller thread | Internal representation of `executor = ""`; keeps the processor branch-free |

The semaphore design makes "started" exact and needs no queue: a permit is acquired **inside** the
worker before the handler runs, and `submit` returns the future the worker completes at that point.
No unbounded internal queue exists anywhere, by construction.

Ownership: executors are created by the **bundle builder** and owned by `EventoBundle`; the
processor only borrows them. They are shared across consumers by name — a named executor is a
**shared capacity pool**, which is the point (one "slow-io" budget for the whole bundle, not per
projector). Document that explicitly.

---

## 5. Annotation + configuration changes

### `@EventHandler` (`evento-common`)

```java
String executor() default "";   // "" = inline (current behaviour)
```

`retry` / `retryDelay` already exist and already drive the retry loop in
`ProjectorReference.invoke` / `ObserverReference.invoke` — **no new retry machinery is needed**.
"Retry N times, then dead-letter" falls out of wrapping the existing call in a try/catch that
writes to the DLQ.

### `@SagaEventHandler`

**Do not add `executor`.** Startup validation rejects any attempt to route saga handlers through an
executor (see below).

### `EventoBundle.Builder`

```java
private final Map<String, ConsumerExecutor> consumerExecutors = new HashMap<>();

public Builder addConsumerExecutor(String name, ConsumerExecutor executor);
public Builder removeConsumerExecutor(String name);
```

Mirrors `setComponentContexts` / `removeComponentContexts` in style and placement.

### Startup validation (fail fast, in `EventoBundle.Builder.start()`)

Run alongside the existing confinement check; all are `IllegalStateException` at start:

1. `@EventHandler(executor = "x")` where `x` is not registered → unknown executor.
2. `executor` set on a `@SagaEventHandler` → unsupported (annotation change makes this
   unrepresentable; keep a reflective check for safety if the attribute is ever added).
3. A registered executor that no handler references → warn (typo detector), do not fail.

### `retry = -1` under an executor means `0`

`retry = -1` (the annotation default) means *retry forever* on the inline path. Inside an async
task that would pin a concurrency permit indefinitely and silently starve the executor, so for a
handler with an `executor` the effective budget is **coerced to `0`** — one attempt, then straight
to the DLQ. Implement it locally in `ProjectorReference.invoke` / `ObserverReference.invoke`:

```java
var effectiveRetry = a.executor().isEmpty() ? a.retry() : Math.max(0, a.retry());
```

No signature change, and the inline path is untouched. Log the coercion once per handler at
startup so it is not invisible.

One consequence to size for: with the default annotation, an async handler dead-letters on the
*first* failure — including a transient one. That makes the Phase-2 degraded-flag backoff (§6)
the thing that stops a dependency outage from burning the stream into the DLQ at full speed, and
makes an explicit `retry = n` the recommended setting for any async handler that touches a remote
dependency. Documentation item, not a design change.

---

## 6. Changes to the consume loop

### `ConsumerProcessor` (`evento-common`)

Add one collaborator — a per-event executor resolver — and branch inside the existing per-event
loop. Nothing else about checkpointing, locking, or the transient/permanent split moves.

```java
// Builder addition. null result => inline (today's path, byte-for-byte unchanged).
private Function<PublishedEvent, ConsumerExecutor> executorResolver = e -> null;
private Duration submitTimeout = Duration.ofSeconds(5);
```

`consumeEventsForProjector` per-event body becomes:

```java
var ex = executorResolver.apply(event);
if (ex == null) {
    if (inFlightForThisCycle > 0) awaitQuiescence(...);   // §3 rule 1: inline acts as a barrier
    …existing inline body — ConsumerDisabledException / isTransient / DLQ unchanged…
} else {
    var started = ex.submit(() -> runAsync(consumerId, name, event, eventConsumer), submitTimeout);
    if (started.isEmpty()) return consumed;               // no capacity: end cycle, release lock
    started.get().join();                                 // returns as soon as the task begins
}
currentVersion = advanceCheckpoint(consumerId, new ProjectorCheckpoint(seq), currentVersion);
```

and the task body:

```java
private void runAsync(String consumerId, String name, PublishedEvent event, EventConsumer c) {
    try {
        c.consume(event);                    // includes the annotation-driven retry loop
    } catch (ConsumerDisabledException e) {
        logger.warn(…);                      // consumer paused mid-flight: drop, do not DLQ
    } catch (Throwable t) {
        try { deadEventQueue.add(consumerId, event, t); }
        catch (Throwable t2) { logger.error(…); }
    }
}
```

`InMemoryDeadEventQueue` is already fully `synchronized`, so concurrent DLQ writes from executor
threads are safe; verify the same for the JDBC impls (they are per-call connection users, expected
fine — confirm, don't assume).

**Transient failures change meaning in async mode.** Inline, a `TransientConsumerException`
propagates out, leaves the checkpoint alone, and the engine backs off — the event is redelivered.
Async, the checkpoint has already advanced when the failure happens, so redelivery is impossible
and the DLQ is the only remaining path. Consequences to encode:

- `isTransient` classification still runs inside the task, but only to decide the **retry budget**:
  transient causes get a larger effective budget (`retry` × a transient multiplier, with the
  existing `retryDelay` backoff) before dead-lettering.
- After exhausting it, the event dead-letters like any other failure. Document that async consumers
  turn a dependency outage into DLQ entries rather than a stalled-but-recoverable checkpoint. This
  is the second thing operators must understand about the mode.
- Optionally (small, worth it): if a task fails transiently, set a volatile "degraded" flag the
  engine reads to back off its fetch loop, so an outage does not burn the whole event stream into
  the DLQ at full speed. **Recommended — include in Phase 2.**

### `ConsumerProcessor.consumeEventsForObserver`

Observers **already** dispatch to `observerExecutor` (an unbounded
`newVirtualThreadPerTaskExecutor`) and already checkpoint on submit. This feature is a strict
improvement for them: replace that field with a `ConsumerExecutor` resolved the same way, so
observers gain bounded concurrency and real backpressure. The default when a bundle configures
nothing becomes `ConsumerExecutors.virtual(<default cap>)` rather than unbounded — a behaviour
change for observers, and the only one in Phase 1. Call it out in the release notes.

### `ProjectorEngine` — head-reached must drain

`ProjectorEngine` signals head-reached when `consumedEventCount < sssFetchSize`, and that release
is what **enables the bundle** (`eventoServer.enable()` in the start thread). Under async, "consumed"
means "started", so without a change the bundle would start serving queries while the read model
still has in-flight writes.

Fix: before `alignmentCounter.decrementAndGet()`, await quiescence of every executor this
projector's handlers use (bounded by a deadline; log and proceed on timeout). Same for the
`ps.setHeadReached(true)` transition. **This is a correctness requirement, not a nicety.**

### Engine wiring (`EventoBundle.startProjectorEnginesV2` / `startSagaAndObserverEnginesV2`)

Each engine builds its resolver from the handler map it already holds — for a given
`PublishedEvent`, look up `ProjectorReference.getEventHandler(eventName)`, read
`@EventHandler.executor()`, and map the name through the bundle's executor registry. Cache the
`eventName → ConsumerExecutor` mapping at engine construction; it is static per handler.

### `ConsumerEngineConfig`

Carries the executor registry so the processor and the engines share one instance:
`ConsumerEngineConfig(processor, stateStore, deadEventQueue, executorRegistry)`. `inMemory(…)`
keeps working with an empty registry (all-inline).

### Shutdown (`EngineSupervisor.stop(Duration)`)

Ordering matters and is currently absent:

1. Set the shutdown flag → fetch loops stop submitting.
2. `awaitTermination` on the engine executor (existing).
3. **New:** `awaitQuiescence` on every `ConsumerExecutor`, then `shutdown(remaining deadline)`.
4. `shutdownNow` as today.

Without step 3 a clean stop drops every in-flight task — and those events are already
checkpointed, so a graceful shutdown would silently lose data. This closes the loss window for
all non-`kill -9` stops.

### Interceptor-managed transactions must keep working unchanged

Applications manage their Postgres transaction per event by configuring a
`MessageHandlerInterceptor` — `beforeProjectorEventHandling` opens the transaction,
`afterProjectorEventHandling` commits, `onExceptionProjectorEventHandling` rolls back (same triple
for observer and saga). This is the pattern in production use and it **must survive async
dispatch untouched**. It does, but only because of one invariant that has to be stated and pinned
by a test:

> **Thread-affinity invariant.** For a given event, `before* → handler → after*/onException*` all
> execute on **one and the same thread**, and that thread is the executor's task thread. The fetch
> loop thread never runs any part of it.

This holds by construction in the design: the task body is `eventConsumer.consume(event)`, and
that lambda is the whole `dispatch → Reference.invoke → interceptor` chain. Nothing is split
across the submit boundary — the fetch loop only awaits the *start* signal. Spring's
`TransactionSynchronizationManager` is `ThreadLocal`-backed, so an interceptor that binds a
connection in `before*` and unbinds it in `after*`/`onException*` behaves exactly as it does
inline. Corollaries:

- **Retries keep one transaction per attempt.** The retry loop in `ProjectorReference.invoke`
  wraps the interceptor calls, so each attempt gets its own `before*`/`after*` pair and therefore
  its own transaction. Unchanged from today.
- **The interceptor instance is now called concurrently.** One `MessageHandlerInterceptor` is
  shared across all consumers (`DispatchContext.messageHandlerInterceptor()`) and will be entered
  from many executor threads at once. Per-invocation state must live in `ThreadLocal`s or locals,
  never in instance fields. (This is already true for observers today — they dispatch to an
  unbounded executor — but async projectors make it a general requirement.) Document it on the
  interface javadoc.
- **The DLQ write must land outside the rolled-back transaction.** On a permanent failure the task
  calls `deadEventQueue.add(...)` immediately after `onException*` returned, on the same thread. If
  the interceptor does not fully unbind the transaction in a `finally`, that JDBC write would join
  a rolled-back/closed transaction. The framework side is: perform the DLQ write **after** the
  interceptor chain has completed (it already is). The contract side is: interceptors must unbind
  in a `finally`. Cover with a test that fails the handler and asserts the DLQ row is committed.
- **Checkpoint is not in the handler's transaction** — already true today (the checkpoint commits
  after `consume` returns) and unchanged. Under async it commits *earlier*, at task start, so a
  rollback leaves the checkpoint advanced. Note that this is **not** a new failure mode: inline,
  an exhausted-retry rollback also dead-letters and advances the checkpoint. Only the crash window
  (§2) differs.

**Connection-pool sizing is the real operational consequence.** ARCHITECTURE §12 already requires
one pooled connection per concurrently-running consumer (each held `LockHandle` pins one). Async
adds a second demand: every concurrently-executing transactional handler holds a connection for
its whole task. The pool must therefore be sized for

```
consumers-running-concurrently  +  Σ(capacity of each executor used by transactional handlers)  +  headroom
```

An executor capacity of 64 against a default 10-connection Hikari pool will not deadlock loudly —
it will surface as connection-acquisition timeouts, which `isTransient` classifies as transient
and (with `retry = -1 → 0`) dead-letters on first hit. This must go in the docs next to the
existing §12 sizing note, and the guidance is: **cap a transactional handler's executor at or
below its pool budget.**

**Virtual threads + JDBC.** The default `ConsumerExecutors.virtual(n)` runs handlers on virtual
threads. Carrier-thread pinning inside the driver or pool would negate the concurrency: verify
against the versions in use (pgjdbc 42.7.13 and HikariCP 7 both moved off `synchronized` to
`ReentrantLock`, so this is expected to be fine — verify, don't assume). `ConsumerExecutors.pooled(n)`
on platform threads exists precisely as the escape hatch for transactional handlers if pinning
shows up; recommend it in the docs for any handler holding a JDBC transaction until the virtual
path is measured.

### Dead-event replay

`consumeDeadEventsForProjector` / `…ForObserver` stay **inline**. Replay is operator-driven and
low-volume; running it through the shared executor would let a replay storm starve live
consumption. Keep it simple.

---

## 7. Files touched

| Module | File | Change |
|---|---|---|
| `evento-common` | `modeling/annotations/handler/EventHandler.java` | `+ String executor() default ""` |
| `evento-common` | `messaging/consumer/ConsumerExecutor.java` | **new** SPI |
| `evento-common` | `messaging/consumer/impl/{Virtual,Pooled,Inline}ConsumerExecutor.java`, `ConsumerExecutors.java` | **new** impls + factories |
| `evento-common` | `messaging/consumer/ConsumerProcessor.java` | resolver + submit/await-start branch; `observerExecutor` → `ConsumerExecutor` |
| `evento-bundle` | `EventoBundle.java` (Builder) | `addConsumerExecutor`, registry plumbing, startup validation |
| `evento-bundle` | `consumer/ConsumerEngineConfig.java` | carry the registry |
| `evento-bundle` | `consumer/ProjectorEngine.java` | drain before head-reached |
| `evento-bundle` | `consumer/ObserverEngine.java` | resolver wiring |
| `evento-bundle` | `consumer/EngineSupervisor.java` | drain + shutdown executors |
| `evento-bundle` | `manager/ProjectorManager`, `ObserverManager` | expose handler→executor-name mapping |
| `evento-bundle` | `reference/ProjectorReference.java`, `ObserverReference.java` | effective-retry coercion (`-1 → 0` when an executor is set) |
| `evento-bundle` | `manager/MessageHandlerInterceptor.java` | javadoc: implementations are entered concurrently; per-invocation state in `ThreadLocal`s, unbind in `finally` |
| `evento-bundle` | `HandlerMetadataBuilder.java` | *(Phase 3)* publish executor name in discovery |

---

## 8. Phasing

**Phase 1 — mechanism (self-contained, shippable).**
`ConsumerExecutor` SPI + virtual/pooled/inline impls; `@EventHandler.executor`; builder
registration; processor branch with checkpoint-on-start and submit timeout; DLQ-on-failure; startup
validation + `retry = -1 → 0` coercion; projector drain-before-head-reached; supervisor
drain-on-shutdown; observer migration to the bounded executor; interceptor thread-affinity
invariant with its test coverage and the pool-sizing documentation.

**Phase 2 — operability. ✅ shipped (§13).** Transient-failure degraded flag + engine backoff;
per-executor counters via `ConsumerExecutorStats` (not Micrometer — see §13);
`ConsumerFetchStatusResponseMessage` gains the async fields so the dashboard can show a
consumer running async.

**Phase 3 — visibility. ✅ shipped (§14).** Publish `executor` on `RegisteredHandler` →
`BundleDiscoveryInfo` → server → GUI handler view, so the topology shows which handlers are
parallel. The only piece that touches the transport wire — and, as §14 records, "additive
field, no break" turned out to be only half true.

**Phase 4. ✅ shipped (§15).** `CheckpointMode.WATERMARK`; key-partitioned ordering.

---

## 9. Deferred / open

- **`CheckpointMode.WATERMARK`** — commit `max contiguous completed sequence` instead of
  `last started`. Restores at-least-once (crash replays ≤ `P` events, harmless for exactly the
  idempotent handlers this feature targets). Needs a small in-flight sequence tracker per consumer.
  My recommendation is to build it in Phase 4 and let per-consumer config choose; `ON_START` stays
  the default because it is what was specified and it never replays.
- **Key-partitioned executor** — `@EventHandler(executor = "x", orderBy = AGGREGATE_ID)` routing
  same-key events to the same lane. Preserves per-aggregate order while parallelising across
  aggregates; would widen applicability far beyond strictly-idempotent handlers.
- **Per-consumer executor override** — declaring the executor on `@Projector`/`@Observer` rather
  than per handler. Cheap to add later if per-handler proves too fine-grained.
- **`submitTimeout` tuning** — 5 s default. Note the interaction with `ConsumerLock`: the JDBC lock
  **pins a pooled connection for the whole cycle**, so a long submit wait holds a connection.
  The timeout must stay short enough that a saturated executor releases the lock promptly rather
  than parking a connection. This is why submit is time-boxed at all.

---

## 10. Test plan

Following the project rule — tests at the boundary, real transports for ITs.

**`evento-common` (unit):**
- `ConsumerExecutor` impls: concurrency never exceeds the cap; the returned future completes at
  task start (not enqueue, not completion); `submit` returns empty exactly on capacity timeout;
  `awaitQuiescence` blocks until the last started task finishes; `shutdown` honours its deadline.
- `ConsumerProcessor`: checkpoint advances on start with a handler blocked mid-run; a saturated
  executor ends the cycle at the last started event and the next cycle resumes there with no gap
  and no duplicate; handler throw after exhausting `retry` lands in the DLQ; `ConsumerDisabled`
  mid-flight drops without dead-lettering; **inline handler acts as a barrier** over earlier async
  events.

**`evento-bundle` (unit):** startup validation — unknown executor name, `executor` on a saga
handler, unused-executor warning. `retry = -1` under an executor resolves to a single attempt then
DLQ (and still means retry-forever inline). Resolver caches the correct executor per event name.

**Interceptor / transaction (extend `evento-lab`'s `ConsumerInterceptorIT` +
`LabBundleInterceptor`):**
- **Thread-affinity**: record `Thread.currentThread()` in `before*`, the handler body, and
  `after*`; assert all three are identical and none is the fetch-loop thread. This is the test
  that protects `ThreadLocal`-bound transactions — the highest-value test in this section.
- A `ThreadLocal` bound in `before*` is visible in the handler and correctly unbound after, across
  many concurrent events on the same shared interceptor instance (catches accidental instance-field
  state).
- Each retry attempt gets its own `before*`/`after*` pair.
- Failing handler → `onException*` runs, then the DLQ entry is committed and readable (proves the
  DLQ write is not swallowed by the rolled-back transaction).

**`evento-lab` (IT):**
- A projector with a deliberately slow async handler completes an N-event batch in ≈`N/P`× the
  serial time — proves actual parallelism, not just plumbing.
- **Head-reached happens only after drain**: assert every event's effect is visible in the store at
  the moment the alignment counter hits zero. This is the regression test for the bundle-enable
  race and the most valuable test in the suite.
- Graceful stop mid-batch loses nothing (supervisor drain).
- A hard-stop simulation documents the in-flight loss window — an *expectation* test that pins the
  documented semantics rather than pretending they don't exist.

**`evento-lab-microservices` (IT):** an async observer under a real broker sustains throughput and
dead-letters correctly when the handler fails permanently.

**Regression:** the full existing suite must be green with zero changes — the default path is
inline and must be byte-for-byte equivalent.

---

## 11. Summary of judgement calls

| Decision | Choice | Why |
|---|---|---|
| Checkpoint trigger | On task **start** (as specified) | Bounds the crash-loss window to `P`, gives backpressure for free |
| Executor scope | Per handler, shared by name across the bundle | Matches the request; a named executor is one capacity budget |
| Sagas | Excluded, fail fast | Read-modify-write on shared state; parallelism corrupts it |
| Retry machinery | Reuse the existing annotation retry loop in the `*Reference` classes | Already implements "retry N, then throw"; DLQ wrapper is the only new part |
| `retry = -1` + executor | Coerced to `0` (one attempt, then DLQ) | Retry-forever would pin a concurrency permit and starve the executor |
| Interceptor-managed transactions | Preserved by keeping the whole `before → handler → after` chain on one task thread | `ThreadLocal`-backed tx managers work unchanged; pool sizing becomes the real constraint |
| Transient failures | Larger retry budget, then DLQ (+ engine backoff in P2) | Checkpoint has already moved; redelivery is no longer available |
| Inline handlers among async ones | Barrier — drain first | Prevents an undebuggable read-your-writes violation |
| Bundle enable | Drain before head-reached | Otherwise queries hit an unaligned read model |
| Submit blocking | Time-boxed, cycle ends on timeout | Never park a pinned JDBC lock connection on a saturated executor |

---

## 12. Phase 1 — what shipped

Full suite green: 321 tests across transport/common/bundle/lab/lab-ms, plus
`com.evento.server.bus.*` and the JDBC consumer-state-store module. **No existing test
needed changing** — the default path stays inline.

### New

| File | Role |
|---|---|
| `evento-common/…/consumer/ConsumerExecutor.java` | SPI |
| `evento-common/…/consumer/ConsumerExecutors.java` | `virtual(name, n)` / `pooled(name, n)` / `unbounded(name, delegate)` |
| `evento-common/…/consumer/impl/BoundedConsumerExecutor.java` | Semaphore + delegate `ExecutorService` |
| `evento-common/…/consumer/impl/UnboundedConsumerExecutor.java` | Legacy `Executor` adapter |
| `evento-bundle/…/consumer/ConsumerExecutorResolver.java` | `event → executor`, precomputed per engine |
| `evento-bundle/…/consumer/ConsumerExecutorValidator.java` | Start-up check |
| `evento-common` tests | `ConsumerExecutorTest` (7), `AsyncConsumerProcessorTest` (9) |
| `evento-bundle` tests | `ConsumerExecutorWiringTest` (7) |
| `evento-lab` tests | `AsyncConsumerIT` (7) + `AsyncLabProjector` / `AsyncLabStore` / `TransactionTrackingInterceptor` fixtures in `com.evento.lab.async` |

### Deviations from the plan above (all deliberate)

1. **No `ConsumerExecutors.inline()`.** The plan wanted it "to keep the processor
   branch-free", but an inline *executor* would bypass the inline path's transient-failure
   and `ConsumerDisabled` handling and dead-letter transients instead — two subtly
   different behaviours behind one concept. `executor = ""` now resolves to `null` and
   takes the genuine inline path. One way to do it.
2. **`ConsumerEngineConfig` does not carry the executor registry.** It turned out nothing
   needs it there: the registry lives on the builder (for validation and resolver
   construction) and the supervisor (for shutdown), while the processor receives a
   per-call resolver. Avoided a breaking change to a public record for no gain.
3. **`ConsumerProcessor` gained overloads, not changed signatures.** The 5-argument
   `consumeEventsFor{Projector,Observer}` still exist and delegate with a `null` resolver.
4. **`observerExecutor(Executor)` kept** as a compatibility overload wrapping
   `UnboundedConsumerExecutor` (six call sites across tests would otherwise have broken),
   alongside a new `observerExecutor(ConsumerExecutor)`. The behaviour change lands where
   it matters: `ConsumerEngineConfig.inMemory` now wires a **bounded** observer executor
   (`DEFAULT_OBSERVER_CONCURRENCY = 32`) instead of an unbounded virtual-thread one.
5. **Validation extracted** to `ConsumerExecutorValidator` rather than living inside
   `EventoBundle.Builder`, so it is unit-testable without starting a bundle.
6. **No runtime check for `executor` on saga handlers** — `@SagaEventHandler` simply has no
   `executor` attribute, so it is unrepresentable rather than rejected.
7. **`retry = -1` coercion** implemented as `maxRetry` locals in `ProjectorReference` /
   `ObserverReference`; the validator logs the coercion once per handler at start-up.

### Found while building

- **`inFlight` must count from admission, not from the worker's first instruction.** The
  first cut incremented inside the task body, so `awaitQuiescence` could slip between
  "permit granted" and "task scheduled" and wrongly report idle. Caught by
  `awaitQuiescenceReportsFalseWhenWorkOutlivesTheDeadline`.
- **Observer saturation must release the dedupe claim.** If `submit` fails after
  `dedupeStore.tryClaim` succeeded, the claim would be held by nobody and the event would
  be permanently skipped when the next cycle re-fetches it.

### Negative-control results (tests were verified to fail without their fix)

| Test | Without the fix |
|---|---|
| `inlineHandlerWaitsForEarlierAsyncEvents` | **fails** — inline handler overtakes the async one |
| `bundleIsNotEnabledUntilAsyncHandlersHaveFinished` | **fails** — 4 of 8 events unapplied at enable |
| `gracefulStopDrainsInFlightHandlers` | still passes — it is pinned by `BoundedConsumerExecutor.shutdown`'s own quiescence wait; the engine-level drain is belt-and-braces on that path |

The head-reached test needed a **1.5 s** handler delay to become sensitive: at 200 ms the
outstanding batch finished inside the enable round-trip and the test passed even with the
drain removed. Worth remembering if it is ever tuned down.

### Not done in Phase 1

Everything in §8 Phase 3–4 — GUI visibility, watermark checkpointing, key-partitioned
ordering. (Phase 2 landed too; see §13.)

---

## 13. Phase 2 — what shipped

### Degraded backoff on transient async failures

The gap this closes: an async handler fails on its own thread, long after the fetch loop
moved on, so its failure never surfaces as the loop's `hasError`. A downed dependency was
therefore met by the loop pulling at full speed and dead-lettering the entire stream —
async events cannot be redelivered, their checkpoint has already advanced.

`ConsumerProcessor` now tracks a per-consumer **transient-failure streak**: incremented when
an async task fails with a cause `isTransient` recognises, reset by the first clean
completion. `ProjectorEngine` / `ObserverEngine` read
`processor.asyncTransientFailureStreak(consumerId)` each iteration and, when non-zero, sleep
on the same `ExponentialBackoffWithJitter` curve a channel error uses. Permanent failures
deliberately do **not** raise the streak — a poison event is no reason to slow the stream.

### Counters

- `ConsumerExecutorStats` record (`name`, `capacity`, `inFlight`, `admitted`, `rejected`,
  `completed`, `failed`) via a new `ConsumerExecutor.stats()` — `default` on the interface,
  overridden by `BoundedConsumerExecutor`, so third-party impls are unaffected.
  **`rejected` is the metric to alert on**: it counts admissions that timed out, i.e. the
  executor announcing it is the bottleneck.
- `ConsumerFetchStatusResponseMessage` gains `asyncInFlight`, `asyncSubmitTimeouts`,
  `asyncTransientFailures`, `asyncExecutors`. Purely additive, and safe on the wire because
  `AdminPayloadCodec` sets `FAIL_ON_UNKNOWN_PROPERTIES=false` — an older server decoding a
  newer bundle's response ignores them.
- `ConsumerExecutorResolver` now returns a `Routing` record (resolver + `executorNames`)
  instead of a bare `Function`, so an engine can report which executors it depends on.

**Deviation: no Micrometer.** The plan said "per-executor metrics via Micrometer", but
Micrometer is an `evento-server` dependency only — wiring it into `evento-bundle` would push
a new transitive dependency onto every user application. Counters are exposed through the
SPI and the consumer-status wire instead, and the server can bind them in Phase 3.

### The JDBC / virtual-thread question — now measured, not assumed

`VirtualThreadJdbcConcurrencyIT` (Testcontainers Postgres, gated on `EVENTO_RUN_JDBC_IT`)
settles §6's open question. 32 handlers, each holding a pooled connection through a
server-side `pg_sleep(0.25)`:

```
availableProcessors=8
virtual-thread JDBC concurrency: peak=32/32 elapsed=303 ms (serial would be 8000 ms)
```

Full capacity at **4× the core count**, so no carrier pinning — consistent with JDK 24's
JEP 491 plus pgjdbc and HikariCP having moved off `synchronized`. `ConsumerExecutors.pooled`
remains available but is no longer the recommended default for transactional handlers.

**The first run of this test failed at `peak=16`, and the cause is worth keeping.** It was
not pinning: Hikari opens connections lazily, and establishing 32 of them costs far more
than a 250 ms handler, so the early handlers finished before the late ones held a
connection. Pre-warming the pool gives 32/32. The operational read-across is real —
**a freshly started async consumer is throttled by a cold connection pool**, not by its
executor capacity, until the pool has ramped. Set `minimumIdle` accordingly if a consumer's
catch-up burst matters.

A second test pins the opposite failure — an executor wider than its pool starves on
connection acquisition (timeouts, not lock errors), which is exactly the ARCHITECTURE §12
sizing rule failing in the way operators will actually see it.

### Phase 2 tests

`AsyncConsumerProcessorTest` +4 (streak raised by transient / not by permanent, status
counters, executor stats), `ConsumerExecutorWiringTest` updated for `Routing`,
`AsyncConsumerIT` +1 (status reports executor names), `VirtualThreadJdbcConcurrencyIT` +2.

---

## 14. Phase 3 — visibility

`@EventHandler.executor` now travels the whole way to the dashboard:

| Layer | Change |
|---|---|
| `evento-common` | `RegisteredHandler.executor` (wire DTO) |
| `evento-bundle` | `HandlerMetadataBuilder.applyExecutor` populates it for projector + observer event handlers |
| `evento-server` | `Handler.executor` JPA field, `schema.sql` column + `ALTER TABLE … ADD COLUMN IF NOT EXISTS` for live upgrades, `AutoDiscoveryService` stores it, `HandlerDto` exposes it |
| `evento-gui` | Component-catalog handler list shows an `Executor: <name>` chip; Cluster Status → Consumers shows a *Parallel* row with executor names, in-flight, saturation and transient-failure counters (rendered only when the consumer actually has async handlers, so inline consumers look unchanged) |

The consumer-status fields needed no server work — `ConsumerController` returns
`ConsumerFetchStatusResponseMessage` directly, so the Phase 2 additions reach the GUI as
JSON already.

**Executor is refreshed, not backfilled.** `AutoDiscoveryService` treats `line` and
`componentPath` as backfill-if-missing, but a redeploy can move a handler between inline and
async, so `executor` is compared and overwritten. Backfill semantics would have pinned
whichever value was seen first and quietly shown stale information forever.

### The bug this phase nearly shipped

Adding a convenience `isAsync()` getter to `RegisteredHandler` **broke bundle discovery
entirely.** Jackson serialized the derived property as `async`; the transport
`payloadCodec` deserializes with `FAIL_ON_UNKNOWN_PROPERTIES` **enabled** (unlike
`AdminPayloadCodec` / `ObjectMapperUtils`, which disable it), so the server rejected the
whole `BundleDiscoveryInfo`:

```
event=listener_error type=Notification
com.evento.transport.codec.CodecException: decode failed for BundleDiscoveryInfo
Caused by: UnrecognizedPropertyException: Unrecognized field "async" (RegisteredHandler)
```

The failure mode is what makes it dangerous: the bundle starts, connects, registers, enables
and consumes normally — **only its handler metadata silently vanishes from the dashboard.**
Nothing in the bundle logs, and every pre-existing test still passed, because none of them
asserted that discovery arrived. It was caught only because the new IT asserted on the
discovery payload.

The immediate fix was to drop the derived getter. The root cause got fixed too: **both
transport codecs now disable `FAIL_ON_UNKNOWN_PROPERTIES`**, matching `AdminPayloadCodec`
and `ObjectMapperUtils`, so all four mappers agree and mixed-version fleets work in both
directions — a newer peer's extra fields are skipped, an older peer's missing fields fall
back to the defaults the records' `@JsonCreator` constructors already normalise. This
matters most on the *message* codec, where a version skew would break the handshake itself.

My earlier note that strictness was deliberate gadget-chain defence was wrong.
`FAIL_ON_UNKNOWN_PROPERTIES` is not a security control: the defence is
`MessageTypeRegistry` / the `PolymorphicTypeValidator`, which bound *which types* may be
instantiated. Skipping an unrecognised property on an already-whitelisted type instantiates
nothing. Both whitelists are untouched. `CodecVersionToleranceTest` pins newer-peer,
older-peer and round-trip skews.

`AsyncConsumerIT.discoveryNotificationDecodesWithTheNewField` is the regression guard: it
asserts a *non-empty* discovery for the bundle actually reaches the broker.

### Phase 3 tests

`AsyncConsumerIT` +2 (per-handler executor in the discovery payload; discovery decodes at
all). `EmbeddedBroker` gained an `eventBus()` accessor so ITs can observe `BusEvent`s.
GUI verified with `ng build --configuration production`.

---

## 15. Phase 4 — the two guarantees given back

Phases 1–3 traded away ordering and at-least-once delivery to gain parallelism. Phase 4
offers each back independently, so a consumer can buy the guarantee it needs and keep the
throughput.

### `CheckpointMode.WATERMARK` — at-least-once

`setCheckpointMode(WATERMARK)` on the builder (or `setComponentCheckpointMode` per
projector, mirroring how contexts are declared) switches the persisted checkpoint from the
dispatch frontier to the **highest contiguous completed sequence**. Events still running sit
above the watermark and are redelivered after a crash.

The design turns on separating two cursors that `ON_START` conflates:

| | `ON_START` | `WATERMARK` |
|---|---|---|
| Fetch cursor | the persisted checkpoint | in-memory dispatch frontier |
| Persisted checkpoint | dispatch frontier | highest contiguous completion |
| Crash outcome | in-flight events lost | in-flight events replayed |

Keeping the fetch cursor on the in-memory frontier is what stops the lagging checkpoint from
re-delivering events the current run has already dispatched — the obvious implementation
(fetch from the checkpoint) would process everything twice, continuously. The frontier is
seeded from the persisted value each cycle, which also covers takeover by another instance.

Details worth keeping:

- **The commit happens on the consume-loop thread, in a `finally`.** Handler threads only
  record completions; the loop slides the watermark and performs the optimistic-version
  commit. That keeps the version dance single-threaded, and the `finally` covers all four
  exits — normal end of batch, saturated-executor early return, consumer-disabled
  short-circuit, and a thrown `TransientConsumerException`. Missing any one would strand
  finished work uncheckpointed.
- **A dead-lettered event counts as completed.** It is resolved, not outstanding; treating
  it otherwise would let one poison event pin the watermark forever.
- **A stuck handler pins the watermark**, so the replay cost on restart grows. The processor
  warns once per cycle past `watermarkLagWarnThreshold` (default 10 000).
- **`flushWatermark` runs after a drain**, so a graceful stop persists what just finished
  rather than replaying the final batch on restart.
- The dashboard's "last event" now trails true progress by the in-flight window under this
  mode — expected, and the price of the checkpoint meaning "completed" rather than "started".

### `ConsumerExecutors.partitioned` — per-aggregate ordering

The bigger win in practice. Unordered parallelism is only safe for idempotent or
overwrite handlers; a partitioned executor pins events sharing an ordering key to one lane
and runs one task per lane at a time, so **same-aggregate events apply in sequence order
while different aggregates run in parallel**. That covers the large middle ground of
read-model projectors that are not idempotent but are per-aggregate sequential —
read-modify-write on a row.

Chosen deliberately as an **executor type, not an annotation flag**: the consume loop always
passes the event's aggregate id as the ordering key, `submit(task, orderingKey, waitFor)` is
a `default` method that ignores it, and only the partitioned implementation acts on it. So
opting in is one line of configuration, no handler changes, no new annotation attribute, and
third-party `ConsumerExecutor` implementations are unaffected.

A busy lane **refuses admission rather than queueing**, preserving both invariants the rest
of the design rests on: admission still means "started", and no internal queue can grow
unbounded. The consequence is that a burst on one hot aggregate serialises — which is the
ordering guarantee doing its job, not a defect. Concurrency is bounded by the lane count and
reduced further by key skew, so size lanes well above the expected number of
concurrently-active aggregates. Keyless events (no aggregate) are spread round-robin rather
than colliding on lane 0, since they have no ordering relationship to preserve.

### Phase 4 tests

`WatermarkCheckpointTest` (7): the gap at a running sequence pins the checkpoint while later
completions wait; a crash replays only the in-flight window (asserted against a *fresh*
processor, so only the persisted state survives); the fetch cursor follows the frontier so
nothing is processed twice within a run; dead-lettered events do not pin the watermark;
inline handlers advance it immediately; `flushWatermark` after a drain; and an `ON_START`
contrast case that pins the difference.

`PartitionedConsumerExecutorTest` (5), including an end-to-end consume-loop test with two
interleaved aggregates and uneven handler latency. **Verified sensitive**: swapping
`partitioned` for `virtual` makes it fail, so it is testing ordering rather than luck.

`AsyncConsumerIT` +2 covering both features over a real broker.
