# Evento v2 — Enterprise-Readiness Plan

Assessment + phased improvement plan produced 2026-05-30, after the
`evento-cli` / `evento-parser` / deployment-surface removals. Companion to
[`ARCHITECTURE.md`](ARCHITECTURE.md) (authoritative reference) and
[`STATUS.md`](STATUS.md) (session delta). Update the status column as items land.

## Scope & verdict

Production surface = 6 modules: `evento-transport-api`, `evento-transport-netty`,
`evento-common`, `evento-bundle`, `evento-server`,
`evento-consumer-state-store/evento-consumer-state-store-jdbc`. The `evento-lab*`
modules are integration-test harnesses and `evento-gui` is the React UI — out of scope here.

**The core engineering is strong** — sealed-message wire protocol, OCP dispatch,
bounded correlation stores with deadlined shutdown, optimistic-locked consumer
checkpoints, zero-copy forwarding, dialect-abstracted JDBC with Flyway.

**The gap is "enterprise skin," not the skeleton.** Three categories: (1) a few real
correctness/security defects, (2) observability that is *documented but not wired*,
(3) operational hardening (secure-by-default, health, CI coverage of the server's own
web/persistence layers). "Insecure dev defaults" is partly by-design for an RC, but
there is no *wired* path to a hardened mode — that is the enterprise blocker.

All findings below were verified against source at assessment time (file:line cited).
Two claims from the initial sweep were **disproven on verification** and are recorded
in "Corrected non-findings" so they are not re-investigated.

## Findings by severity

### P0 — defects to fix before any GA

| # | Finding | Evidence |
|---|---|---|
| 1 | **SQL injection in distributed lock.** `key` (command `lockId`/`aggregateId`, caller-controlled) is string-concatenated into advisory-lock SQL. The JDBC *consumer* lock is correctly parameterized — this class is not. | `evento-common/.../utils/PgDistributedLock.java:63,91,137` |
| 2 | **Unbounded chunk reassembly → remote OOM/DoS.** A peer can stream non-final chunks across unlimited stream UUIDs; buffers are cleared only on the last chunk. No per-stream byte cap, no concurrent-stream cap, no idle timeout. | `evento-transport-netty/.../ChunkReassembler.java:34,48,55` |
| 3 | **Distributed tracing is a stub.** `TracingAgent.correlate(...)` returns metadata unchanged; no trace-id on `Hello`/`Request`/`Response`; no OpenTelemetry. ARCHITECTURE §6/§14 implies it works. | `evento-bundle/.../performance/TracingAgent.java:73,85` |

### P1 — enterprise-blocking gaps

| # | Finding | Evidence |
|---|---|---|
| 4 | **Micrometer declared, never wired.** Actuator on classpath; zero `MeterRegistry`/Counter/Timer/Gauge. The four `evento.server.*` metrics in ARCHITECTURE §14 do not exist. | `evento-server/build.gradle:31`; no `io.micrometer` usage |
| 5 | **No health/readiness.** No `HealthIndicator`, no actuator exposure config. Orchestrators cannot probe bus/DB/transport. | no `HealthIndicator` in tree |
| 6 | **Insecure defaults, no wired hardening path.** `TokenValidator.acceptAll()` default; TLS off by default, no mTLS; REST `anyRequest().permitAll()` + CORS `*`; weak default JWT key shipped in tracked `application.properties`. | `BusLifecycle.java:125`; `NettyTransportConfig` (ssl null default); `WebConfig.java:43,67`; `application.properties:27,29` |
| 7 | **Polymorphic-typing gadget risk.** `activateDefaultTyping(..., NON_FINAL)` with `allowIfSubType(Serializable.class)` on the admin/v1 payload path. | `evento-common/.../admin/AdminPayloadCodec.java:49-62`; `ObjectMapperUtils.java:29,37` |
| 8 | **Server web/es/domain/service untested in CI.** `evento-server:test` is filtered to `com.evento.server.bus.*`; REST controllers + event store + command broker have no CI tests. JDBC ITs are gated off and never run in CI. | `.github/workflows/ci.yml`; `evento-server/src/test` (all under `bus/`) |

### P2 — hardening / leak / operability

| # | Finding | Evidence |
|---|---|---|
| 9 | **No background reaper for `ForwardingTable` / `reconnectBuffer`** — TTL enforced only lazily / on reconnect; orphaned in-flight entries accumulate. | `ForwardingTable.java:61`; `BusLifecycle.java:111-113` |
| 10 | **`BusLifecycle.stop()` does not deadline transport close / `closeAll()`** — shutdown can hang on a slow socket. | `BusLifecycle.java:196-202` |
| 11 | **Event-store schema is hand-written `schema.sql` + `ALTER … IF NOT EXISTS`** (no Flyway, unlike the consumer store); no event-level optimistic version; snapshot LRU has no cross-instance coherency. | `evento-server/.../schema.sql:180-190`; `EventStoreEntry.java` |
| 12 | ~~Auth tokens logged in cleartext~~ — **corrected non-finding** (see below). | — |
| 13 | **No dependency scanning / version catalog / lockfile.** Pinned versions are current today; drift is unmanaged. | root `build.gradle:4-20` |

### Doc drift (cheap)
- ARCHITECTURE §2 module map listed `evento-parser/` (removed in `21d8cd78`). **Fixed 2026-05-30.**
- ARCHITECTURE §14 / §6 oversell metrics + tracing as if present. **Clarified 2026-05-30** (see item 3/4).
- STATUS "evento-cli removal" note said `evento-parser` was *kept*; it has since been removed. **Annotated 2026-05-30.**

### Corrected non-findings (do not re-investigate)
- ❌ "Maven/signing credentials committed." **False** — `gradle.properties` and `signing-key.gpg` are gitignored; only `gradle.properties.template` is tracked.
- ❌ "Advisory locks are parameterized everywhere." Partly false — the *consumer* `JdbcConsumerLock` is parameterized, but `PgDistributedLock` (P0 #1) is not. Both classes exist; do not conflate them.
- ❌ "No dependency scanning / Dependabot" (part of #13). **False** — `.github/dependabot.yml`
  covers gradle + npm + github-actions weekly, and `codeql.yml` + `scorecard.yml` workflows exist.
  Only a version catalog (and an optional build-time CVE gate) is genuinely absent.
- ❌ "Auth tokens logged in cleartext" (was P2 #12). **False on verification** — every flagged log (`session_accepted token=`, `old_token=`/`new_token=`) prints `session.connectionToken()`, an internal per-connection supersede-guard UUID, *not* the bundle `authToken` secret. The real `authToken` is validated at `BusLifecycle.java:572` and never logged; `HandshakeHandler` logs only bundle/instance/version/caps. No redaction needed.

## Phased plan

### Phase 0 — correctness & safety (P0; ~2–3 days; into RC)
1. Parameterize `PgDistributedLock` → `pg_advisory_lock(hashtext(?))` (mirror `JdbcConsumerLock`). Regression test with a `'`-bearing key.
2. Bound `ChunkReassembler`: per-stream max bytes + max concurrent streams + idle-stream eviction (hook `IdleStateHandler`); reject/close on breach. "Partial-chunk flood" test.
3. Decide tracing honestly: thread a W3C `traceparent`-style id through `Hello`/`Request`/`Response` now (OTel bridge in Phase 2), **or** delete the stub and correct ARCHITECTURE §6/§14.

### Phase 1 — secure-by-default & observable (P1; ~1–2 weeks)
4. Wire Micrometer: gauges (`connections{state}`, `correlations.pending`, `forwarding.table.size`, `reconnect.buffer.size`, `chunk.reassembly.active`), timers (`message.processing.duration{type}`), counters (forwarded raw/reencoded, rejects, dedup replays). Expose Prometheus endpoint.
5. `HealthIndicator`s (bus bound, DB reachable, transport accepting) + actuator exposure + liveness/readiness split.
6. Wired hardening: `evento.server.security.mode=open|shared-secret|mtls` driving `TokenValidator`/`SslContext` from properties; remove `permitAll()` default (opt-in dev override); CORS allowlist; move the JWT signing key out of the tracked `application.properties` to an env var with fail-fast when unset in a non-dev profile.
7. Constrain `AdminPayloadCodec`/`ObjectMapperUtils` polymorphic typing to an explicit allowlist (drop blanket `Serializable`).
8. Redact tokens in logs (hash/truncate).

### Phase 2 — persistence & resilience hardening (P2)
9. Background reapers for `ForwardingTable` + `reconnectBuffer`; thread the shutdown deadline through `BusLifecycle.stop()` → transport close.
10. Migrate event-store schema to Flyway (versioned, parity with consumer store); define event-level sequence-conflict handling; document/address snapshot-cache coherency.
11. Hikari sizing guidance for the one-connection-per-consumer lock pattern; document `LockHandle` lifecycle / consider `AutoCloseable` enforcement.

### Phase 3 — quality gates & hygiene (continuous)
12. CI: drop the `com.evento.server.bus.*` filter (or add explicit web/es/domain suites); `@WebMvcTest`/`@SpringBootTest` for REST + auth; run JDBC ITs via Testcontainers (nightly or PR-gated).
13. OWASP dependency-check + Dependabot; version catalog (`libs.versions.toml`); Gradle lockfile.
14. Doc-drift pass on ARCHITECTURE/STATUS (partially done — see Doc drift above).

## Item 7 — proposal (polymorphic-typing gadget risk)

**The risk.** Both `ObjectMapperUtils.getPayloadObjectMapper()` and `AdminPayloadCodec.defaultMapper()`
call `activateDefaultTyping(ptv, NON_FINAL)` with `allowIfSubType(Serializable.class)`. The `@class`
type discriminator on the wire / in the DB can therefore name *any* `Serializable` class on the
classpath, and Jackson will instantiate it — the canonical Jackson default-typing RCE surface
(commons-collections, spring-beans, c3p0, … gadgets, when present).

**Why a quick fix fails.** These mappers carry **user domain types in arbitrary packages**, not just
`com.evento.*`: event/aggregate-state persistence (`EventStore.java:50`, `JsonConverter`), gateways
(`CommandGatewayImpl`), query responses (`SerializedObject`, `SerializedQueryResponse`), and the
bundle's default payload mapper (`EventoBundle.java:337`). Restricting to `com.evento.*` would break
every real application. `allowIfSubType(Serializable.class)` exists *because* user payloads are
`Serializable` and live in app packages.

**Recommended approach (layered, lowest-risk first):**
1. **Admin path → drop default typing entirely.** `EventoRequest`/`EventoResponse.body` carries a
   *finite, known* set of framework message types. Enumerate them and `registerSubtypes(...)` on the
   admin codec, removing `activateDefaultTyping`. This eliminates the gadget surface on the admin path
   with zero allowlist guesswork. Highest value, lowest blast radius.
2. **User-payload path → explicit package allowlist instead of `Serializable`.** Replace
   `allowIfSubType(Serializable.class)` with `com.evento.` + an operator-configured set of application
   base packages (`evento.security.deserialization.allowed-packages`) + a curated set of JDK value
   types actually used (java.time, java.util collections, java.lang wrappers, java.math). On the
   bundle, default the app packages from the configured component-scan base package.
3. **Denylist backstop.** Refuse known-dangerous bases regardless of allowlist (defense in depth).

**Open question — RESOLVED.** `JsonConverter` is a JPA `AttributeConverter<Serializable,String>` that
does `readValue(dbData, Serializable.class)` with default typing; `EventStore` uses the same mapper.
So the server **does** deserialize persisted event payloads into concrete typed objects on the
event-store read path — it is *not* fully payload-agnostic for persistence (a v1 carryover that
contradicts ARCHITECTURE §6.1). Implication for Item 7: the server-side mapper genuinely needs the
package-allowlist treatment of step 2 (it cannot be reduced to opaque bytes without reworking the
event-store entity model — a larger, separate effort). The bundle-side and admin-side hardening
(steps 1–2) are independent of that rework and can proceed first.

## Sequencing
Phase 0 is non-negotiable before GA and is small. Phase 1 (#4–6) is what makes
"enterprise" true — secure-by-default + Prometheus/health is the table-stakes trio.
Phases 2–3 can land during the planned staging soak.

## Status tracker

| Item | Phase | Severity | Status |
|---|---|---|---|
| 1 PgDistributedLock SQLi | 0 | P0 | ✅ done — parameterized all 3 advisory-lock statements; gated regression IT `PgDistributedLockIT` |
| 2 ChunkReassembler bounds | 0 | P0 | ✅ done — per-message byte cap + concurrent-stream cap + TTL eviction + inactive cleanup; `ChunkReassemblerTest` (4 tests) |
| 3 Tracing decision | 0 | P0 | ✅ decided — kept as overridable no-op hook, marked honestly in Javadoc; docs corrected. Real W3C/OTel propagation deferred to Phase 2 |
| 4 Micrometer wiring | 1 | P1 | ✅ done — `BusMetricsBinder` (connections, available, correlations.pending, forwarding.table.size, forwarded{raw,reencoded}); `micrometer-registry-prometheus` + `/actuator/prometheus` exposed |
| 5 Health indicators | 1 | P1 | ✅ done — `BusHealthIndicator` + actuator health/liveness/readiness exposure |
| 6 Secure-by-default path | 1 | P1 | 🟡 mostly done — JWT key externalized (no shipped secret; random fallback + warn); opt-in wire shared-secret `TokenValidator` (`evento.server.bus.auth-token`); REST auth-mode toggle `evento.server.web.security.mode=open\|token` (default open; token → `/api/**` + sensitive actuator require JWT, GUI assets + health stay public); configurable CORS; creds → env placeholders. **Remaining:** GUI token-injection flow so the dashboard works under `mode=token` (touches `evento-gui`) |
| 7 Polymorphic-typing allowlist | 1 | P1 | ✅ done (opt-in) — shared `PayloadTypeAllowlist` drives both the payload mapper and admin codec: open by default (back-compat, warns once), strict package allowlist (`com.evento.` + curated JDK value pkgs + configured app packages) when `evento.serialization.allowed-packages`/`EVENTO_SERIALIZATION_ALLOWED_PACKAGES` is set. `PayloadTypeAllowlistTest` (3). NB: investigation revealed the admin codec carries open user payloads too (command/event forwarding), so the "registerSubtypes finite set" idea was dropped in favour of this uniform allowlist |
| 8 Token log redaction | 1 | P1 | ❌ closed — corrected non-finding (logged `token=` is the internal connection UUID, not the auth secret) |
| 9 Reapers (ForwardingTable + reconnectBuffer) | 2 | P2 | ✅ done — `BusLifecycle` runs a daemon maintenance scheduler (30s) pruning forwarding-table relays >5min and expired reconnect-buffer entries; scheduler shut down in `stop()`. `ForwardingTableTest` added (3 tests, closed a coverage gap) |
| 10 Shutdown-deadline through transport close | 2 | P2 | 🟡 partial — maintenance scheduler now shut down in `stop()`; deadlining `transportServer.stop()`/`closeAll()` needs a `TransportServer` SPI change (deferred) |
| 10b Event-store Flyway | 2 | P2 | open |
| 11 Hikari/lock lifecycle docs | 2 | P2 | ✅ done — ARCHITECTURE §12 documents pool sizing (≥1 conn per concurrent consumer) + `LockHandle` try-with-resources contract |
| 12 CI coverage | 3 | P1 | 🟡 partial — CI now runs the full `:evento-server:test` (dropped the `bus.*` filter) + a new `jdbc-integration-tests` job runs the Testcontainers ITs (`EVENTO_RUN_JDBC_IT=true`). **Remaining:** actually-written web/REST + auth tests (none exist yet) |
| 13 Dep scanning / catalog | 3 | P2 | 🟡 mostly a non-finding — Dependabot (gradle/npm/actions), CodeQL, and Scorecard already configured. Reconciled `nettyVersion` 4.1.118→4.1.124. **Remaining (low):** version catalog (`libs.versions.toml`); optional OWASP dependency-check build-time CVE gate |
| 14 Doc-drift pass | 3 | low | partial |
