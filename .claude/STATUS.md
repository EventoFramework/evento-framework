# Evento Framework — status snapshot

Last updated: 2026-05-26. Branch `next` merged to `main`; v2.0 rewrite complete.

Companion docs:
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — authoritative reference for architecture, design, classes, tests
- [`PLAN.md`](PLAN.md) — historical v2 rewrite plan (all PRs shipped)
- `STATUS.md` (this file) — session delta; update at end of every session

---

## Current state

| Item | State |
|---|---|
| Branch | **`main`** (active development). `next` merged. |
| Version | `2.0.0-rc1` (git tag `v2.0.0-rc1`) |
| v2 rewrite | **Complete** — all 3 PRs (transport / server / bundle) shipped |
| Test count | ~245 on JDK 25 (transport, server, common, bundle, lab, lab-ms); 50+ JDBC ITs gated on Docker |
| Known issues | None open post Fix A–D |

## What shipped (v2 rewrite summary)

- **Transport:** `evento-transport-api` (sealed `Message` SPI, `InMemoryTransport`) +
  `evento-transport-netty` (Netty pipeline, chunking, TLS, heartbeat, zero-copy forwarding)
- **Server bus:** `BusLifecycle` orchestrator replacing `MessageBus.java`; composable
  `ConnectionRegistry`, `ClusterRegistry`, `CorrelationStore`, `ForwardingTable`,
  `HandshakeHandler`, `TokenValidator`, `MessageRouter`, `BusEventBus`
- **Bundle client:** `BundleClient` + `ConnectionSupervisor`, `BundleCorrelationTracker`,
  `ProcessedRequestCache`, `HandlerRegistry`, `InboundDispatcher`, `EventoServerAdapter`
- **Consumer engines:** `ProjectorEngine`, `SagaEngine`, `ObserverEngine`, `EngineSupervisor`
  composed on five focused SPIs + JDBC impls (Postgres + MySQL, Flyway migrations)
- **Command broker:** `CommandBrokerHandler` restores aggregate + service command interception
  on top of `BusLifecycle`
- **v1 deletion:** `MessageBus.java`, v1 bundle transport (1 634 LOC), v1 consumer abstract class all gone
- **Post-RC1 fixes:** Fix A (superseded-session race), Fix B (FK violation), Fix C (DEGRADED
  drops), Fix D (QoS reconnect delivery for in-flight responses)

## Remaining before `v2.0.0` GA

1. Deploy `main` to staging with real traffic generator
2. Soak 1–2 weeks with early adopters
3. Address any staging findings
4. Bump version → `2.0.0`, tag, publish to Maven Central

## Next feature planning

### ASM-based self-discovery of invocations (2026-05-26) ✅

**Goal:** eliminate the `evento-cli` static-analysis step for building the dependency graph.
Bundles now scan their own bytecode at startup and publish invocation edges as part of `BundleDiscoveryInfo`.

**Changes shipped:**

| Module | Change |
|--------|--------|
| `evento-common` | `RegisteredHandler` +`invokedCommands: List<String>` + `invokedQueries: List<String>` |
| `evento-bundle` | `AsmInvocationScanner` — single-pass class scan + BFS from handler through intra-class call graph |
| `evento-bundle` | `HandlerMetadataBuilder` — calls scanner for every handler (aggregate, service, projector, observer, saga, projection, invoker) |
| `evento-bundle` | `InvokerManager` — exposes `handlerMethods` map so metadata builder can access Method objects for invokers |
| `evento-bundle/build.gradle` | `org.ow2.asm:asm:9.10.1` (JDK 25 / class file v69 compatible) |

**Algorithm guarantees:**
- Detects direct `gateway.send(new Cmd())` calls
- Detects transitive calls through any depth of private/package helper methods (BFS, cycle-safe)
- Detects lambda bodies via `INVOKEDYNAMIC` → `LambdaMetafactory` bootstrap handle tracing
- Isolated per handler — scanning `handlerA` cannot bleed into `handlerB`

**Tests:** 9 tests in `AsmInvocationScannerTest` covering direct, one-hop, three-hop, lambda, ServiceCommand, Query, isolation, and empty cases.

**Server-side complete (2026-05-27):** `AutoDiscoveryService.onNodeJoin` now:
- Calls `buildInvocations(registeredHandler, bundle.getId())` when creating a new ephemeral handler
- Calls the same helper when an existing handler has empty invocations (backfill on re-registration)
- `buildInvocations` iterates `invokedCommands` and `invokedQueries`, resolves or creates ephemeral
  `Payload` entities (type `Command`/`Query`), and returns `Map<Integer, Payload>` for `Handler.invocations`
- `resolveOrCreatePayload` reuses existing payloads or creates ephemeral ones with `jsonSchema="null"`,
  `validJsonSchema=false`, same pattern as handler/component creation

---

### Full self-description parity with CLI — PLAN (not yet started)

**Goal:** Make bundle startup registration carry everything that `BundleDescription` (CLI) carries,
so the `evento-cli` static-analysis + publish step is entirely optional.
Excluded intentionally: JAR upload, `autorun`, `deployable`, min/max instances, `artifactCoordinates`.

#### Source-link URL formula

The server builds a clickable source link from three pieces:
```
{bundle.repositoryUrl}/{component.path}#{bundle.linePrefix}{line}
```
Example (Bitbucket — `linePrefix = "lines-"`):
```
https://bitbucket.org/myorg/repo/src/main/order-bundle/com/example/OrderAggregate.java#lines-42
```
Example (GitHub/GitLab — `linePrefix = "L"`):
```
https://github.com/myorg/repo/blob/main/order-bundle/com/example/OrderAggregate.java#L42
```
- `repositoryUrl` — repo browser base URL for this bundle+branch (deployment-time config, not in bytecode)
- `linePrefix` — anchor format (`lines-` for Bitbucket, `L` for GitHub/GitLab)
- `component.path` — relative source path extracted by ASM (e.g. `com/example/OrderAggregate.java`)
- `line` — component, handler, payload, or invocation line depending on what is being linked

#### Gap being closed

| Level | Currently missing from wire protocol |
|-------|--------------------------------------|
| Bundle | `description`, `detail`, `repositoryUrl`, `linePrefix` |
| Component | `description`, `detail`, `path` (source file relative path), `line` (class declaration line) |
| Handler | `line` (method declaration line) |
| Payload | `description`, `detail`, `path`, `line` (currently only `schema` + `domain` in `String[]`) |
| Invocations | line numbers (currently `List<String>` names only; server model uses `Map<Integer, Payload>`) |

#### Description/Detail standard

Javadoc is stripped from class files at compile time. The solution is a lightweight annotation in `evento-common`:
```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface EventoDescription {
    String value() default "";   // short one-liner shown in dashboards
    String detail() default "";  // markdown long-form
}
```
Usage:
```java
@EventoDescription(value = "Manages order lifecycle", detail = "Handles all commands that mutate order state...")
public class OrderAggregate extends Aggregate { ... }
```
Fallback when absent: description = class simple name; detail = "". Zero effort for developers who skip it.

#### Repository URL and line prefix — application.properties

`repositoryUrl` and `linePrefix` come from deployment configuration, not bytecode.
Added as optional fields on `EventoBundle.Builder`:
```java
EventoBundle.Builder.builder()
    .bundleId("order-service")
    .repositoryUrl("https://bitbucket.org/myorg/repo/src/main/order-service")
    .linePrefix("lines-")
    ...
```
In Spring Boot applications, inject via:
```java
@Value("${evento.bundle.repository-url:}") String repositoryUrl,
@Value("${evento.bundle.line-prefix:L}")   String linePrefix
```
Standard property keys (document in ARCHITECTURE.md):
- `evento.bundle.repository-url` — no default (empty = source links disabled)
- `evento.bundle.line-prefix` — default `L`

#### Implementation steps (in order)

**Step 1 — `evento-common`** ✅
- [x] `@EventoDescription` annotation in `com.evento.common.modeling.annotations`

**Step 2 — `evento-transport-api`** ✅
- [x] `PayloadDiscoveryInfo` record (schema, domain, description, detail, path, line)
- [x] `BundleDiscoveryInfo` uses `Map<String, PayloadDiscoveryInfo>`; adds description, detail, repositoryUrl, linePrefix

**Step 3 — `evento-common`: enrich `RegisteredHandler`** ✅
- [x] componentDescription, componentDetail, componentPath, componentLine
- [x] handlerLine
- [x] invokedCommands/invokedQueries: `Map<Integer, String>` (source line → simple name)

**Step 4 — `evento-bundle`: ASM extraction + config wiring** ✅
- [x] `AsmClassMetadataScanner`: sourcePath (visitSource + package), declarationLine (min <init> line), description/detail (@EventoDescription via reflection)
- [x] `AsmInvocationScanner`: tracks currentLine via visitLineNumber; returns Map<Integer,String>; handlerLine in Result
- [x] `HandlerMetadataBuilder`: applyComponentMetadata + applyInvocations for all handler types; PayloadDiscoveryInfo with schema/domain/description/detail/path/line
- [x] `EventoBundle.Builder`: repositoryUrl, linePrefix, description, detail fields
- [x] `BundleClientConfig`: all new fields, payloadInfo type changed
- [x] `ConnectionSupervisor`: passes all new fields to BundleDiscoveryInfo

**Step 5 — `evento-server`** ✅
- [x] Bundle JPA entity: repositoryUrl column added
- [x] AutoDiscoveryService: bundle description/detail/repositoryUrl/linePrefix, component path/line/description/detail, handler line, payload description/detail/path/line, invocations keyed by real source line

**Step 6 — Tests (unit)** ✅
- [x] AsmInvocationScannerTest updated for Map<Integer,String>; handlerLine > 0; invocation keys are source lines
- [x] Server IT tests (BusLifecycleIT, BusLifecycleFacadeNettyIT) updated for new BundleDiscoveryInfo constructor and PayloadDiscoveryInfo
- [x] AsmClassMetadataScannerTest — 6 tests: annotated/plain description, sourcePath, declarationLine
- [x] `AsmClassMetadataScanner`: null-classloader guard (bootstrap-loaded classes → system classloader fallback)

**Step 7 — End-to-end verification**
- [ ] Start evento-server locally (Docker Postgres)
- [ ] Start an evento-lab-ms bundle configured with `repositoryUrl` + `linePrefix`
- [ ] Assert via REST:
  - `GET /api/bundle/{bundleId}` → `repositoryUrl`, `linePrefix`, `description`, handler `line`, component `path`/`line` non-null
  - `GET /api/flows/bundle/{bundleId}` → graph nodes include source location
  - `GET /api/catalog/component/{componentName}` → `path`, `line`, `description` populated
  - Manually open `{repositoryUrl}/{component.path}#{linePrefix}{handler.line}` → correct source location
- [ ] No regression in `evento-lab` IT suite

#### Key files to read at session start
- `evento-transport-api/.../BundleDiscoveryInfo.java`
- `evento-common/.../RegisteredHandler.java`
- `evento-bundle/.../AsmInvocationScanner.java`
- `evento-bundle/.../HandlerMetadataBuilder.java`
- `evento-bundle/.../client/BundleClientConfig.java` — add `repositoryUrl`/`linePrefix`
- `evento-bundle/.../client/connection/ConnectionSupervisor.java` line 294 — constructs `BundleDiscoveryInfo`
- `evento-bundle/.../EventoBundle.java` Builder — add `repositoryUrl`/`linePrefix` setters
- `evento-server/.../AutoDiscoveryService.java`
- `evento-server/domain/model/core/Bundle.java` — add `repositoryUrl` field

---

## How to run locally

```bash
# Build (skip tests)
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew clean build -x test

# Full test suite
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :evento-transport-api:test \
  :evento-transport-netty:test \
  :evento-server:test --tests 'com.evento.server.bus.*' \
  :evento-common:test \
  :evento-bundle:test \
  :evento-consumer-state-store:evento-consumer-state-store-jdbc:test \
  :evento-lab:test \
  :evento-lab-microservices:evento-lab-ms-it:test

# JDBC ITs (requires Docker)
EVENTO_RUN_JDBC_IT=true JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-consumer-state-store:evento-consumer-state-store-jdbc:test
```

## Notes for Claude on resume

Read `ARCHITECTURE.md` first — it has the full module map, wire protocol, class responsibilities,
design decisions, and test infrastructure. This `STATUS.md` is the session delta only.
Commit pattern: `feat(module): …` / `fix(module): …` / `chore(build): …` with rich body.
