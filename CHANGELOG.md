# Changelog

All notable changes to Evento Framework are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

Version 3.0: a major release, because one default changes (the server's security profile is `prod`:
it does not start unhardened, see the first upgrade note). Everything else is backward compatible, and
2.5 bundles and servers interoperate with 3.0. Also: the code is all public under a new licence, the
server keeps its schema with Flyway, aggregate commands run without the lock with group commit, the
console works across servers, and APES is deprecated.

Query-path release: queries no longer cost the broker memory, large answers stream with flow
control (through the broker or bundle to bundle), a bundle answers its own queries without the
broker, and a query can require to see a write. Payloads can be versioned: a class records its
revision and upcasters read older shapes. Wire changes are additive and negotiated (`stream:1`,
`revision:1` capabilities); 2.5 bundles and servers interoperate with it in any mix, except that
a revised payload needs a 3.0 broker.

### Upgrade notes

- **A payload that cannot be serialized now fails the send** (`IllegalArgumentException` from
  `SerializedObject`): it used to be logged and sent with a null payload.
- **Breaking: the server refuses to start unhardened.** `evento.server.security.profile` defaults to `prod`
  (was `dev`): without a bus auth token, bus TLS, a payload allowlist, non-default web console and database
  passwords and explicit CORS origins the server does not start, and lists what is missing. Set them, or set
  `EVENTO_SERVER_SECURITY_PROFILE=dev` for development, tests and demos (the shipped compose files, the lab and
  `EventoServerContainer` do).
- **The server schema is migrated by Flyway** (with `spring.sql.init.mode=always`, the shipped value). The
  first 3.0 server creates the history table `evento_es_schema_history`, baselines an existing database at
  version 0 and runs the migrations V1-V5, which find what is already there and add what is missing (the
  primary key, idempotency key and aggregate fence of 2.5 and 3.0, online as before). Nothing to do; give the
  server's database user the right to create that table. With `spring.sql.init.mode=never` nothing changes:
  the server touches no schema and the scripts in `db/upgrade/` stay the way to apply it.
- **Lock waiters may hold a lock-pool connection.** A command waiting for an aggregate held by another server now
  queues in PostgreSQL on a connection of the dedicated lock pool (at most half of it; the rest poll as before),
  so servers take turns on a hot aggregate. Size `evento.lock.pool.maximum-size` for the locks held at once plus
  the contended keys waiting.
- **Queries are routed "straight" by a 3.0 broker** when a 3.0 handler bundle declares them
  (`BundleRegistrationInfo.queryPayloadTypes`): no exactly-once slot, no cached reply, no
  reconnect buffering. A 3.0 caller asks a query again on its next session when it lost it; a
  2.5 caller that loses its session while a query is in flight now waits for its timeout
  instead of getting the buffered reply. Upgrade callers along with handlers.
- **Broker memory is bounded in bytes**, not only in entries: new properties
  `evento.server.bus.dedup.max-retained-bytes` (128 MiB), `evento.server.bus.dedup.max-response-bytes`
  (1 MiB), `evento.server.bus.reconnect-buffer.max-bytes` (256 MiB),
  `evento.server.bus.compression.threshold-bytes` (32 KiB).
- **Upgrade the brokers before deploying a class with `@Revision`.** A 3.0 bundle refuses to send
  or store a revised payload through a broker older than 3.0 (no `revision:1` capability), which
  would record it as revision 0. Unrevised payloads, the default, flow through any broker.
- **Upgrade the brokers before deploying a handler that returns `DomainEvents`.** A 3.0 bundle refuses to answer
  a command with several events through a broker older than 3.0 (no `events:1` capability), which would store
  the last one alone.
- Every 3.0 bundle advertises `stream:1`, `revision:1` and `events:1` in its Hello. Code that asserts the exact
  capability set of a Hello must expect it.
- **Licence: the client libraries are now Apache-2.0** (`evento-transport-api`, `evento-transport-netty`,
  `evento-common`, `evento-bundle`, `evento-consumer-state-store-jdbc` and the new starter, OpenTelemetry and
  test modules).
- **Licence: from 3.0.0 the server, the web console and the rest are Apache-2.0 with the Commons Clause**
  (were AGPL-3.0 or commercial; 2.5.x and earlier keep the licences they were released with), and there is no closed edition any more: single sign-on and the audit trail, until
  now in a commercial module, are part of the server. Using, changing and running Evento, also commercially and
  in production, needs no licence; selling Evento itself (hosting it for others, reselling it, selling
  consulting, support or training about it) needs a written authorisation (`LICENSE-COMMERCIAL.txt`), and the
  names Evento and RECQ are covered by `TRADEMARKS.md`. The Commons Clause makes these components
  source-available rather than OSI open source; the client libraries are not affected. See `LICENSE.txt`;
  contributions go through the CLA (`CLA.md`).
- **The client libraries run on Java 21+** (compiled with `--release 21`; the server stays on 25). On JDK 21–23
  a virtual thread blocked inside `synchronized` pins its carrier: Evento's own critical sections are short,
  but a handler that blocks inside `synchronized` belongs on `ConsumerExecutors.pooled(...)`.
- **Logging goes through SLF4J only.** `evento-common` and `evento-bundle` no longer depend on `log4j-core`
  and no longer ship `log4j2.properties` / `log4j2.xml`, which overrode the application's logging setup. Spring
  Boot applications log Evento through their own configuration with no change; a plain Java application needs
  an SLF4J binding (Logback, `log4j-slf4j2-impl` + `log4j-core`, …), or SLF4J prints a "no providers" warning
  and drops the logs. The unused `okhttp` dependency of `evento-bundle` is gone.
- **`evento-bundle` no longer brings `hibernate-validator`** (nor Jakarta Validation, Jakarta EL, JBoss Logging,
  Classmate) onto the application's classpath: Evento never used it. An application that validates its own
  beans with it declares the dependency itself.
- **Removing a bundle from the catalog (`DELETE /api/bundle/{id}`) needs the `ADMIN` role**, like the other
  console writes; it needed only `WEB`. A script calling it with a WEB-only user gets 403.
- **Schema change: `es__events.aggregate_seq`** (nullable `bigint`) and the partial unique index
  `es__events_aggregate_seq_uidx (aggregate_id, aggregate_seq)`. With `spring.sql.init.mode=always` the first 3.0
  server adds them online at boot (catalog-only column, index built `CONCURRENTLY`); a schema managed outside the
  server applies `classpath:db/upgrade/es-events-aggregate-seq.sql`, and until then aggregate events are appended
  unfenced, as in 2.5 (`event=aggregate_fence_unsupported`). Existing events keep a null version. The fence holds
  once every server runs 3.0: a 2.5 server appends without a version.
- **Concurrent commands on one aggregate without a lock no longer fork it.** A `DomainCommand` whose
  `getLockId()` returns `null` used to let two commands decide from the same story and both store their event;
  now the second one loses the aggregate fence, nothing of it is stored, and the broker runs it again on the new
  story under the aggregate lock (see optimistic concurrency below): its caller gets the answer computed on the
  current state, which may be a refusal by the domain. `AggregateConflictException` (new, `evento-common`) is
  answered only when 5 runs within the deadline all lost.
- **Aggregate commands run without the aggregate lock by default** when the event store fences their appends
  (PostgreSQL with the 3.0 schema): `evento.server.commands.optimistic=true`. The handler of a command that loses
  the fence runs again, so an aggregate handler must be pure (compute its event from the story and the command,
  no side effects): the documented contract, now relied upon. A duplicate of a keyed command can run the handler
  on another server while the first is in flight; still only one event is stored. `false` takes the lock for
  every command, as in 2.5. A command naming another lock than its aggregate (`getLockId()`) keeps it.
- **`evento.server.bus.business-executor-max-size` defaults to 32 threads per CPU** (was 8): the threads mostly
  wait on the event store and the handler bundles, and the old bound capped a 4-CPU server below what its CPU and
  database could do.
- **`evento.es.mode` defaults to `CPES` in the code too**, as the shipped `application.properties` already set it.
  Only a server started without that file (embedded) changes.
- **APES is deprecated; an APES event store can now switch to CPES.** Stop every server, then start them with
  `evento.es.mode=CPES`: the first one moves the CPES sequence above the newest APES id (under `es-lock`, logged
  `event=es_mode_switched`), so new events sort after every old one and consumer checkpoints stay valid. Do not
  run APES and CPES servers together: an id from the last minute at the switch is logged as
  `event=es_mode_switch_while_apes_active` (ERROR). A server still started in APES logs `event=es_mode_deprecated`.

### Added

- **Personal data and the right to erasure (crypto-shredding).** Fields marked `@PersonalData` are stored
  encrypted (AES-256-GCM) with a key of their subject, the object's `@DataSubject` field, in events, commands,
  snapshots and saga state alike. The keys live in the event store's database (`es__subject_keys`, Flyway V7;
  `db/upgrade/subject-keys.sql` for schemas managed outside the server), optionally wrapped with
  `evento.server.personal-data.master-key`; bundles get them over the bus and cache them
  (`EventoBundle.Builder.setSubjectKeyCacheTtl`, one minute). `SubjectKeys.forget(subject)` from a bundle, or
  `DELETE /api/personal-data/subjects/{subject}` (admin, audited), deletes the key: every copy of that subject's
  personal data, backups of the event store included, reads as `null` from then on, and the events stay.
  `evento-test` fixtures and the test broker keep keys in memory.
- **Several events from one aggregate command.** An `@AggregateCommandHandler` may return
  `DomainEvents.of(e1, e2, ...)`, declaring them in `produces`: the events are applied in order and stored in one
  transaction, all or none, with consecutive sequence numbers and aggregate versions (the fence refuses all of
  them when one version is taken); the idempotency key goes on the last event, which is also what the caller is
  answered with. Negotiated with the additive `events:1` capability (`DomainCommandResponseMessage.precedingEventMessages`).
  `AggregateResult.events()` and `expectEvents(...)` in `evento-test`; `expectEvent` on a result with several events
  fails and says so.
- **Spring Boot starter (`evento-spring-boot-starter`).** With `evento.bundle.id` set, the bundle is built from
  `evento.bundle.*` properties (servers, auth token, TLS and mutual TLS, zone, payload allowlist, consumer state,
  tracing), started after the context is ready and stopped gracefully on shutdown; `CommandGateway` and
  `QueryGateway` beans for application code; components get their constructor arguments from the context.
  JDBC consumer state is used when `evento-consumer-state-store-jdbc` and a `DataSource` are present, tracing when
  `evento-opentelemetry` and an `OpenTelemetry` bean are. `EventoBundleCustomizer` beans adjust the builder.
  Compiled against the API Spring Boot 3 and 4 share, tested on 4.1 and 3.5; Evento itself still does not
  depend on Spring.
- **OpenTelemetry tracing (`evento-opentelemetry`), opt-in.** `OpenTelemetryTracingAgent`: a span per handler
  (`SERVER` for commands, queries, invocations; `CONSUMER` for events) and the W3C `traceparent`/`tracestate` in
  the metadata of everything a handler sends, through the server unchanged; `linkingConsumers()` starts consumer
  traces with a link instead of a parent. The gateways `EventoBundle.getCommandGateway()`/`getQueryGateway()`
  carry the trace of the calling code too (an HTTP request span). Depends on the OpenTelemetry API only; a
  bundle without the agent keeps the no-op `TracingAgent` and its gateways unwrapped.
- **Test fixtures (`evento-test`).** `AggregateFixture.of(aggregate).given(events).when(command)
  .expectEvent(...)/expectException(...)/expectState(...)` and `SagaFixture.of(saga).given(events)
  .whenEvent(event).expectCommands(...)/expectEnded()/expectAssociation(...)`, running the component's own
  handlers with no broker or database; `RecordingCommandGateway` and `StubQueryGateway` for the commands and
  queries handlers send. Payloads compare by `equals`, else field by field.
- **Security profile on the server.** `evento.server.security.profile` (`EVENTO_SERVER_SECURITY_PROFILE`):
  `prod`, the default, refuses
  to start, before the bus port opens, until the bus token, bus TLS, the deserialization allowlist, a web
  console and a database password other than the shipped `secret`, and explicit CORS origins are all set;
  `dev` lists at startup the hardening that is off (`event=security_profile_dev`) and starts.
- **TLS and mutual TLS on the bus from properties**: `evento.server.bus.tls.enabled`, `cert-chain`,
  `private-key` (PEM, PKCS#8), `private-key-password`, `trust-certs`, `client-auth=none|optional|require`,
  `protocols` (default TLSv1.3, TLSv1.2), also as `EVENTO_SERVER_BUS_TLS_*`. Before, TLS on the server needed
  code. `evento.server.web.cors.allowed-origins` is now also `EVENTO_SERVER_WEB_CORS_ALLOWED_ORIGINS`.
- **Payload allowlist from code**: `PayloadTypeAllowlist.restrictTo(packages)` and, on the bundle,
  `EventoBundle.Builder.setRestrictPayloadTypes(true)` (the bundle's base package) and
  `setAllowedPayloadPackages(...)`. The validator now reads the allowlist on every check, so mappers built
  before the restriction apply it too.
- `EventoBundle.Builder.setAuthToken(...)`: the shared secret a server with `evento.server.bus.auth-token`
  requires. Before, a bundle built with `EventoBundle.Builder` could not present one.
- `EventoBundle.stop(Duration)`: leave the server's routing, stop the consumer engines, close the connection.
- `EventoBundle.Builder.setBasePackageName(String)`, for when no `Package` object is at hand.
- **Aggregate fence.** Every event of an aggregate command carries the aggregate's version
  (`es__events.aggregate_seq`: 1, 2, 3...) under a unique index. The story is read together with the version it
  ends at (one more branch of the same query, no extra round trip) and the event is appended as the next version,
  so a second append from the same story is refused by the database: a server that lost the aggregate lock (a
  dead lock session, a failover) or a command without a lock can no longer fork an aggregate. Closes G6. Refusals
  log `event=aggregate_fence_conflict` and count in `evento.server.commands.fence.conflicts`; a keyed command that
  loses this way is answered with the stored event, as before.
- **Optimistic concurrency for aggregate commands.** A command runs without the aggregate lock: story, handler,
  fenced append. The lock cost three round trips to the event-store primary per command (acquire, verify,
  release), three WAN round trips from a remote cell, and a lock-pool connection for the whole run, which capped a
  server at about 1300 commands a second. A run that loses the fence is run again on the new story under the lock;
  an aggregate that is contended (another command for it running on the server, or a conflict on it in the last
  2 s) takes the lock from the start, so a hot aggregate queues as before instead of losing races. Correctness
  rests on the database fence, so it holds across servers and cells with no coordination between servers. The
  lock check before a fenced append (`verifyHeld`) is gone too. Metrics: `evento.server.commands.optimistic`,
  `evento.server.commands.fence.retries`.
- **Compatibility policy** ([`COMPATIBILITY.md`](COMPATIBILITY.md)): semantic versioning for the whole
  framework, what a patch, minor and major may change (wire changes only behind capabilities, additive schema
  migrations, deprecation before removal), servers N with bundles N and N-1, servers first then bundles,
  rollback of a server to the previous minor, platforms, and the support window.
- **Versioned schema migrations with Flyway (F4).** The event store and catalog schema is a sequence of
  Flyway migrations (`classpath:db/migration/evento-server`, history `evento_es_schema_history`), run at boot
  under the schema advisory lock, so several servers starting together still migrate once. V1 is the 3.0.0
  schema; the online steps (catalog re-key, primary key, idempotency key, aggregate fence) are V2-V5, outside
  a transaction (`CREATE INDEX CONCURRENTLY`), and one that failed half way runs again on the next boot
  instead of blocking it. Later schema changes are new versions.
- **Integration testing with a broker (A2).** `evento-test-broker` (new, `com.eventoframework:evento-test-broker`):
  `EventoTestBroker`, the server's broker and aggregate command handling in the test's JVM on a free port, with an
  in-memory event store (`events()`, `publish(event, aggregateId)`); commands, events, consumers and queries work
  as against a server, without a database (Java 25, like the server). `EventoServerContainer` in `evento-test`: the
  real server image on Testcontainers, on a PostgreSQL of the test, for applications on Java 21 or another Spring
  Boot version. The server is also published as a library, `com.eventoframework:evento-server` (the plain jar; the
  boot jar of releases is unchanged).
- **Request timers on the broker (O3)**: `evento.server.request`, tagged `kind` (`query`, `service_command`,
  `aggregate_command`, `invocation`, `other`, learnt from the handlers the bundles declare), `payload` and
  `outcome` (`ok` / `error`): the time from a request reaching the broker to its answer leaving it, for the
  requests it answers itself and those it forwards. Fixed buckets (1 ms to 30 s), so one series set per
  payload type stays affordable. Queries a bundle answers itself are timed by the bundle's
  `evento.bundle.request`.
- **Expired requests are not run on the bundle, and an optional bound on the requests it handles (F1).** A
  request whose caller's deadline passed while it queued on the handler bundle is answered
  `RequestExpiredException` ("not executed") without running its handler (`event=request_expired_before_handler`).
  `EventoBundle.Builder.setMaxInboundRequests(n)` (starter: `evento.bundle.max-inbound-requests`, default 0 =
  no bound) refuses one more at once with `TooManyPendingRequestsException` instead of queueing it. Neither answer
  is cached by the bundle's or the broker's exactly-once cache, so a retry of the same request runs. Counter
  `evento.bundle.inbound.not.executed{reason=expired|busy}` (starter).
- **Retention of idempotency keys (F11)**, off by default (keys kept for ever, as before): with
  `evento.es.idempotency.retention` (e.g. `P30D`) one server an hour clears the keys of older events (the events
  stay) and deletes finished or abandoned claims, in batches; `evento.es.idempotency.retention.cleared{what}`.
  Service bundles keep outcomes the same way with `EventoBundle.Builder.setServiceOutcomeRetention`. After the
  retention a command sent again with an old key runs again: choose it longer than any retry, consumer replays
  included.
- **Consumer lag metrics (O2)**, from the server's answer to every consumer fetch, tagged
  `bundle,component,context`: `evento.consumer.lag.seconds` (age of the oldest event not processed yet,
  growing while a consumer is stuck: the one to alert on), `evento.consumer.lag.events` (CPES: newest
  sequence number minus the consumer's position), `evento.consumer.position` and
  `evento.consumer.fetch.age.seconds` (a consumer that stopped). Series of consumers that no longer fetch
  through the server go away after `evento.consumer.lag.expiry` (15 minutes); across servers, take the
  `min by (bundle, component, context)`.
- **Group commit of aggregate appends.** Appends that arrive together are written in one statement and one
  transaction (`AppendBatcher`): under CPES the batch takes `es-lock` once and its events get consecutive
  sequence numbers committed together, so the commit order is still the sequence order. CPES appended one event
  per durable commit under the global lock, about 430 a second on a 4-core machine and one per round trip to a
  synchronous standby; now a batch per commit. A conflict (aggregate version, idempotency key) fails only its own
  append. Without load a batch is one append. `evento.es.append.batch.writers` (default 2 under CPES, 4 under
  APES; 0 turns it off), `evento.es.append.batch.max-size` (256).
- **Pluggable sign-in for the web console**: `evento.server.web.auth` (`EVENTO_SERVER_WEB_AUTH`, default
  `basic`) names the sign-in; a `ConsoleSignIn` bean of that mode adds its flow to the security chain, Basic
  stays for machine clients, and the server refuses to start if no sign-in provides the mode. The GUI asks
  `GET /api/auth/mode` and shows "Sign in with your organisation" for a server-side sign-in; `GET /api/auth/me`
  tells who is signed in.
- **Single sign-on for the web console with OpenID Connect**: `evento.server.web.auth=oidc` and a client
  registration `spring.security.oauth2.client.registration.evento.*`. The server runs the authorization-code
  flow with PKCE, keeps the session in an HttpOnly cookie, protects writes with a CSRF token from the
  `XSRF-TOKEN` cookie, and maps the provider's groups to `WEB` / `ADMIN`:
  `evento.server.web.oidc.{registration-id,roles-claim,web-role,admin-role}` (defaults `evento`, `groups`,
  `evento-web`, `evento-admin`; `roles-claim` may be a dotted path such as `realm_access.roles`). No token
  reaches the browser; Basic stays for machine clients.
- **Server spans for commands**: with the OpenTelemetry Java agent on the server (or an `OpenTelemetry` bean),
  each executed command gets a span `evento command <payload>` with its phase times as attributes
  (`evento.phase.lock.ms`, `.story.ms`, `.append.ms`, …), child of the caller's span; the aggregate's span is
  its child. A no-op without an agent; `evento.server.tracing.enabled=false` turns it off.
- **Fixtures for projectors and services**: `ProjectorFixture` (`given` / `whenEvent`, `replaying()`, asserts on
  the projector's repository) and `ServiceFixture` (`when(command)`, `expectEvent` / `expectNoEvent`).
- **Admin action hook**: every console write (retry, delete or replay dead events, remove a bundle) runs
  through `AdminActions`, which tells the `AdminActionListener` beans when it starts and when it ends, with
  the real outcome (after the bundle's answer).
- **Audit trail of the web console**, on by default (`management.auditevents.enabled=false` turns it off): every
  admin action with who, what, from where and the real outcome, failed sign-ins, OIDC sign-in and sign-out, and
  refused actions of signed-in users. One `key=value` line per event on the `evento.audit` logger (the durable
  record: route it to the SIEM; values are quoted so a crafted name cannot forge a line), and the latest
  `evento.server.audit.capacity` (1000) at `/actuator/auditevents`, ADMIN only.
- **A span per command and query sent**: `OpenTelemetryTracingAgent` wraps every send (from a handler or from
  the application's gateways) in a `CLIENT` span `send <Command>` / `query <Query>` that lasts until the reply,
  with the error when the reply fails; the broker's and the handler's spans are its children. New hook
  `TracingAgent.traceSend(...)`, a plain send in the no-op base agent.
- **Starter observability**: Micrometer meters `evento.bundle.connected`, `evento.bundle.request{payload}`,
  `evento.bundle.handler{type}` (with `.p99` / `.max`); opt-in readiness that follows the connection
  (`evento.bundle.connection-readiness=true`); `EventoBundleStartedEvent` when the server starts routing to the
  bundle. `EventoBundle.getConnectionState()` / `onConnectionStateChange(...)`.

- **Payload versioning (upcasting).** `@Revision(n)` on a payload class (event, command, query,
  aggregate state) records its revision in every payload written (`SerializedObject.revision`,
  absent when 0, so everything stored before reads as revision 0). An `Upcaster` turns a payload
  stored or sent with an older shape into the next one, as JSON, before it is deserialized:
  `Upcaster.from(OrderPlaced.class, 0).to(1, json -> json.set("customerId", json.remove("clientId")))`
  for a revision, `Upcaster.from("OrderPlaced", 0).to(OrderPlacedV2.class, 0[, json -> ...])` for a
  class replaced by another (the old one may be deleted). Registered on the bundle with
  `EventoBundle.Builder.addUpcasters(...)`, chained (validated at start: one step per payload and
  revision, no loops) and applied on every read: events of projectors, sagas and observers
  (dead-event retries too), the events and snapshot an aggregate is rebuilt from, and commands and
  queries received. History is never rewritten. A renamed command or query is also routed under
  its former name, so callers not yet upgraded keep reaching its handler. A failing upcaster stops
  its consumer at that event (`event=upcast_failed`) until fixed.

- **Streamed queries.** `QueryGateway.stream(query[, QueryOptions])` returns a `QueryStream<V>`
  (an `Iterable` + `AutoCloseable`, `stream()` for a `java.util.stream.Stream`) whose views are
  pulled as they are consumed. It works for every query handler, whatever it answers with;
  existing handlers need no change. On the wire: four sealed messages (`StreamData`,
  `StreamCredit`, `StreamCancel`, `StreamRedirect`), sent only between peers that negotiated
  `HandshakeProtocol.CAPABILITY_STREAM`. The caller's window (`QueryOptions.window`, default 16
  frames) is credit: the handler sends a frame only against it and the caller returns credit as
  it consumes, so no hop ever holds more than a window of frames of a stream, whatever the
  result's size. The broker relays the frames as it read them (zero copy) and keeps nothing but
  the forwarding entry. A stream's frames keep their order on every hop (one serial dispatch
  lane per connection for `StreamData`). Closing a stream early, the caller leaving, the handler
  leaving, or a caller that stops granting credit for the stream's timeout each end the stream
  on both sides and close the handler's source. A handler that cannot stream (a 2.5 bundle, or a
  session through a 2.5 broker) answers whole, and the stream iterates that answer.
- **`Streamed<T>`**, a lazy query answer (`Streamed.of(() -> repository.stream(...))`): produced
  only as fast as a streaming caller consumes it, its source closed at the end, on cancel or on
  failure; collected into one reply for a plain `query` caller.
- **`Page<T>` and `PageRequest`**: offset pages (with an optional total) and cursor pages
  (`Page.ofCursor`, `PageRequest.after`), `hasNext()` / `next()` for the following request.
- **Direct streams (bundle to bundle).** A bundle configured with
  `EventoBundle.Builder.setDirectStreamConfig(DirectStreamConfig.on(port, advertisedHost))`
  offers its streams on its own endpoint to callers that ask with `QueryOptions.direct()`: the
  broker routes the query and relays one `StreamRedirect` carrying a random, single-use ticket
  bound to the stream (10 s TTL); the caller connects, presents the ticket, and the data never
  crosses the broker. Unreachable endpoint, TLS mismatch, refused or expired ticket: the caller
  asks the stream again through the broker by itself. Optional TLS (`withTls`), NAT-friendly
  `advertisedPort`. Counters: `BundleClient.directStreamsServed()`, `directStreamsFollowed()`,
  `directStreamFallbacks()`.
- **Local query short-circuit.** A bundle answers the queries it handles itself, without the
  broker, once it is enabled (`setLocalQueryShortCircuit`, default true). The query takes the
  handler-side path of a remote one byte for byte (codec, consistency gate, interceptors,
  `TracingAgent`, performance metrics, handler and round-trip latencies), so tracing and
  dashboards see it as before and a view that does not serialize fails locally too. Commands
  always go through the broker. A bundle not yet enabled (projectors still catching up) or
  disabled does not serve itself. Streams short-circuit the same way, in process.
- **Read-your-writes.** A command's reply now says where its event was stored
  (`EventoResponse.eventSequenceNumber` / `eventContext`, set by the broker).
  `CommandGateway.sendTracked(...)` returns `Tracked<R>(result, token)`;
  `QueryGateway.queryWith(query, QueryOptions.defaults().after(token))` is answered only once the
  answering bundle's projectors have processed the write, `readYourWrites()` covers every write
  of this process, and `ConsistencyToken.encode()`/`parse()` carry a token across processes (an
  HTTP header). Tokens are per context: a projector of another context is not waited for. The
  answering bundle wakes its projectors instead of waiting out their idle pause, reads the
  shared checkpoint when another instance holds the lane, and fails the query with
  `ConsistencyTimeoutException` just before the caller's deadline rather than answer stale. With
  async (`executor=`) handlers the guarantee needs `CheckpointMode.WATERMARK` (or no task in
  flight).
- **End-to-end reply compression.** Requests list the encodings they accept
  (`Request.acceptEncodings`); handlers (and the broker for its own replies, event fetches
  included) DEFLATE a reply of 32 KiB or more when that saves at least an eighth. The broker
  relays compressed bytes untouched. JDK `Deflater`, no new dependency; a self-describing marker
  (`0xFF 'E' 'Z' 0x01`, never valid CBOR) survives an older broker's re-encoding.
  `setCompressionThreshold` / `setAcceptCompressedReplies` on the bundle builder.
- `QueryGateway.queryWith(query, QueryOptions)` (timeout, metadata, consistency); named apart
  from the `query` overloads so existing `query(q, null)` calls stay unambiguous. Gateways
  written before it refuse a consistency requirement instead of dropping it, and stream by
  asking the plain way.
- Broker counters: `BusLifecycle.streamsOpened()`, `activeStreams()`, `streamFramesRelayed()`,
  `streamBytesRelayed()`, `reconnectBufferBytes()`; `ForwardingDedupCache.retainedBytes()`.

### Changed

- **Idempotent reads are asked again after a lost session.** Queries and the consumers' event
  fetches still pending when a bundle's session ends are re-sent on the next session (same
  correlation id, the time they have left), on whatever broker it is, instead of failing with
  `BrokerChangedException` or waiting for a reply no broker keeps.
- **The broker's exactly-once cache holds only writes.** Server-local reads
  (`EventFetchRequest`, `EventLastSequenceNumberRequest`) and declared queries bypass it; a
  command reply over 1 MiB is remembered as answered without its bytes (a duplicate then gets
  `ForwardingDedupCache.DuplicateRequestException`, never a second execution), and the cache
  drops retained bytes least recently used first when over its budget.
- **Fewer copies of large messages.** The chunk reassembler holds the received chunks as a
  composite buffer instead of copying them into a growing array and copying that again; the
  encoder wraps the codec's array instead of copying it; chunking slices instead of copying.

### Changed

- Component scanning uses ClassGraph instead of Reflections (unmaintained since 2021): same semantics, annotated
  classes and their subclasses within the base package. The component managers' `parse` methods take a
  `ComponentScan`; `javassist` (invoker proxies) is now a direct dependency.

### Deprecated

- **APES (`evento.es.mode=APES`).** On one server CPES is as fast since group commit (benchmark B1: 3907 vs 3852
  cmd/s at 128 in flight); with several servers APES consumers can skip events (several clocks, MAC-derived node
  ids, a commit slower than `evento.es.fetch.delay`). Its one measured advantage, parallel commits under
  synchronous replication across AZs (B2: 2-3x CPES at low concurrency, because CPES holds `es-lock` through the
  replication wait), needs several servers to matter, where it is unsafe. Still works; switch with the upgrade
  note above; to be removed in a later major version.

### Fixed

- **The server stopped answering under load.** Responses from bundles ran on the bounded bus business executor
  (8 threads per CPU), behind the requests waiting for them: with as many commands in flight as threads, all of
  them holding a thread while waiting for the aggregate lock or for the handler bundle, no response was ever
  processed and every command expired after 30 s (a 4-CPU server stopped at 128 commands in flight, or 32 on one
  aggregate). Responses now run on virtual threads of their own (`evento-transport-netty`), in servers and
  bundles alike. Found by the Axon comparison benchmark (`benchmarks/axon-comparison`).
- **APES refused commands when the clock stepped back.** The Snowflake ids of APES events threw
  `IllegalStateException: Invalid System Clock!` whenever the system clock was a millisecond behind the last id
  (NTP, a virtual machine's time sync), and the command being stored failed. Ids now go on from the last one issued
  until the clock catches up. Found by the same benchmark (a few commands a minute on WSL2).
- **A server could starve another one on a contended lock.** Waiting for a lock held by another server polled
  `pg_try_advisory_lock` with a backoff of up to 200 ms, while the holder handed the key to its next local command
  within a round trip: with two servers on one hot aggregate, one served ~50 commands a second and the other 0-20,
  with waits up to the 30 s timeout. A waiter now queues in PostgreSQL (`pg_advisory_lock` under a `lock_timeout`),
  which grants the key to the first in line, so servers take turns; at most half the dedicated lock pool queues at
  once, the rest of the waiters poll as before. Found by the HA benchmark B2 (`benchmarks/axon-comparison`).
- The web console logged the user out on any 403: a user without `ADMIN` (normal with OIDC groups) who tried a
  dead-event action was sent back to the login page. Only 401 signs out now; a refused action shows "This
  action needs the ADMIN role.", and the retry toggle flips back.
- A page size over 1000 on `/api/system-state-store/event` and `/snapshot` answered 500 (an unmapped Tomcat
  exception); it answers 400.
- `JdbcConsumerStateStore` held a monitor while it waited for the pool, the network and Flyway; it is a
  `ReentrantLock` now, so a virtual thread waiting there does not pin its carrier on JDK 21–23.
- A response over the reconnect buffer's byte budget is no longer buffered
  (`event=reconnect_buffer_full`): a burst of disconnects can no longer exhaust the broker's heap.

## [2.5.2] — 2026-10-13

Bug-fix release on top of 2.5.1: no API, wire, schema or configuration change. Upgrade bundles
and servers in any order; the fix applies on whichever side receives the reply (the broker for
replies from handlers, the bundle for replies from the broker).

### Fixed

- **A reply written just before its sender closed the session could be lost.** The Netty
  transport hands every inbound frame to the business executor but saw the channel's close on
  the event loop, and announced the disconnect at once. A bundle that answered a request and
  closed straight after (the end of a fail-back drain does exactly that) raced the two: the
  broker failed the originator with `peer disconnected: <instance>` and dropped the reply that
  was already on its way, so a request the handler had executed surfaced as an error.
  `BundleClientFailbackIT.aLargeReplyWrittenDuringTheDrainArrivesWhole` failed this way on CI.
  The disconnect transition (server and client side) now waits until the responses already
  read from the channel have reached the listener, bounded by 2 s
  (`event=disconnect_after_delivery_grace` when a listener outlives it). Only responses are
  tracked: a request still being served when its sender leaves does not delay the disconnect.
  `NettyTransportIT` pins the order and the bound.

## [2.5.1] — 2026-10-13

Dependency patch release on top of 2.5.0: no API, wire or schema change, and no new
configuration. Servers and bundles of 2.5.0 and 2.5.1 interoperate in any mix.

### Changed

- **Runtime libraries.** Spring Boot 4.1.1 (server and lab services), Netty 4.2.17.Final,
  Jackson 2.22.2, Flyway 13.4.0, OkHttp 5.5.0,
  ClassGraph 4.8.194. The published library POMs carry these versions; applications that pin
  their own Netty or Jackson should align.
- **Build.** Gradle 9.7.1, Lombok 1.18.48 (`evento-bundle` now takes it from the root
  `lombokVersion` instead of a pinned 1.18.40), JUnit platform launcher 6.1.3.
- **GUI** (embedded in the server image). Angular 22.2.2, Capacitor 8.5.3, marked 18.1.1,
  mermaid 11.17.2; lint toolchain typescript-eslint 8.71.2, eslint-plugin-jsdoc 64.5.4.
- **CI.** actions/setup-java 5.7.0, Gradle wrapper-validation 6.3.0, CodeQL actions 4.37.6.

## [2.5.0] — 2026-10-09

Cluster-readiness release: several `evento-server` instances sharing one PostgreSQL event
store and catalog, bundles that fail over and fail back between them, exactly-once keyed
commands, bounded distributed locks. Wire changes are additive (older bundles and servers
interoperate); the database schema is migrated on the first boot of a 2.5.0 server.

### Upgrade notes

Read these before rolling 2.5.0 out. Details are in the entries below.

1. **Upgrade every server together, then the bundles.** Stop all 2.4.x servers that share
   the database, start the 2.5.0 servers, then roll the bundles at your own pace. The first
   2.5.0 server to boot re-keys the component catalog (N1); a 2.4.x server still running
   against the same database fails its catalog writes from then on (routing is in memory and
   keeps working, discovery and consumer registration do not). Mixed 2.4.x / 2.5.0 servers
   also weaken exactly-once: a keyed retry that lands on a 2.4.x server runs unkeyed. 2.4.x
   bundles work against 2.5.0 servers unchanged (verified: 2.4.6 bundles registered and ran
   aggregate, saga and service commands on a 2.5.0 server over a migrated 2.4.6 database), and
   2.5.0 bundles against 2.4.x servers run without exactly-once and without probe-gated
   fail-back.
2. **Schema migrations run on the first boot** when Boot runs `schema.sql`
   (`spring.sql.init.mode=always`, the shipped value), serialised across servers by a
   PostgreSQL advisory lock, each idempotent (a re-boot is a no-op):
   - `es__events` gains `PRIMARY KEY (event_sequence_number)`: first a full-table duplicate
     check, then `CREATE UNIQUE INDEX CONCURRENTLY` (no lock on appends), then an instant
     `ACCESS EXCLUSIVE` swap under `lock_timeout` 3 s, retried. **On a large event store the
     first server's boot lasts as long as that scan plus index build** (the other servers wait
     on the schema lock); to keep it out of the boot, apply
     `db/upgrade/es-events-primary-key.sql` (in `evento-server.jar`) by hand beforehand.
     Duplicate sequence numbers fail the boot with their count; resolve them, or boot with
     `evento.es.schema.enforce-primary-key=false`.
   - `es__events.idempotency_key` / `idempotency_command` (nullable, catalog-only change),
     the partial unique index `es__events_idempotency_key_uidx` (built `CONCURRENTLY`, covers
     only keyed rows, so existing data cannot violate it) and the table `es__idempotency_claims`.
   - `core__component` re-keyed per bundle (`id = bundle_id:component_name`), with
     `core__handler` / `core__consumer` moved to `component_id`: one transaction, a few hundred
     rows, `ACCESS EXCLUSIVE` on the three catalog tables for its duration.
   - `core__bundle_instance` (presence of each bundle instance per server).
   - With the schema managed outside the server (`spring.sql.init.mode=never`) nothing is
     changed: apply `db/upgrade/n1-component-id.sql` (required: the server refuses to boot over
     a pre-N1 catalog), `es-events-primary-key.sql` and `es-events-idempotency-key.sql`
     (optional, warned about) yourself.
   - PostgreSQL 12 or later is required; the server refuses an older one at boot.
3. **Connections: size `max_connections`.** Each server now runs an application pool of 20
   (`spring.datasource.hikari.maximum-pool-size`, 5 idle; was Hikari's 10) plus a dedicated
   lock pool of 10 (`evento.lock.pool.maximum-size`, `evento.lock.pool.connection-timeout-ms`
   1000). Peak 30 per server: three servers fit PostgreSQL's default `max_connections=100`;
   with more servers or other clients on the database raise it or lower the pools first. Lock
   waits are now bounded (`evento.lock.acquire.timeout.ms`, default 60000) and throw
   `LockAcquisitionTimeoutException` / `LockLostException` instead of hanging.
4. **New server properties and defaults:** `evento.cluster.name` (env `EVENTO_CLUSTER_NAME`,
   default `evento-server`, set it per deployment), `evento.server.bus.server-instance-id`
   (must be unique per server; a WARN is logged for the shipped id),
   `evento.server.presence.heartbeat-interval-ms` (30 s) and
   `evento.server.presence.stale-after-ms` (3 min), `evento.server.bus.pick-strategy`
   (`random`, or `locality-preferred` with bundle zones), `spring.task.scheduling.pool.size=2`,
   `evento.es.schema.enforce-primary-key` (true).
5. **Bundles now use every configured server address.** 2.4.x silently ignored addresses 2..n;
   a 2.5.0 bundle fails over along the list and, by default, **fails back** to the first one
   once it has answered probes for 30 s + up to 10 s jitter (`EventoBundle.Builder.setFailback*`,
   `BundleClientConfig.failbackEnabled`, default on with more than one address). List the
   bundle's local server first.
6. **Idempotency is on for commands sent from consumers.** Commands a saga, projector or
   observer handler sends get a derived key (`evento:<consumerId>:<seq>:<Command>:<n>`), so a
   redelivered event no longer re-executes them. Consequence: rewinding a consumer answers its
   re-sent commands as replays of the stored events; rewind under a new consumer id to run them
   again. Explicit keys: `CommandGateway.sendIdempotent`. Keys and claims never expire yet.
7. **Service command outcome store is opt-in.** The last exactly-once window (a service handler
   ran but its event was not stored) is closed only for bundles that set
   `EventoBundle.Builder.setServiceCommandOutcomeStore` (`JdbcServiceCommandOutcomeStore`,
   Flyway `V2__service_command_outcome` on PostgreSQL and MySQL, applied by the store's own
   migration) and record in the handler's transaction (`ServiceCommandOutcomes.recordWith`).
   The store must be shared by every instance that can receive the retry.
8. **Run on JDK 25.0.3 or later.** 25.0.1 and 25.0.2 carry JDK-8370887 (virtual-thread timer
   heap corruption, ~20 s stalls on timed waits); bundles and the server log
   `event=jdk_virtual_thread_timer_bug` at startup on those builds.
9. **Commands past their caller's deadline are no longer executed** (N3): the broker drops them
   with `RequestExpiredException` at the queue, lock-wait, forward and append stages
   (`evento.server.requests.expired{stage}`). Callers that relied on a timed-out command still
   being applied must retry it (with an idempotency key, safely).

### Added

- **Per-phase latency of aggregate commands.** The broker now times every executed aggregate
  command phase by phase, from its frame's arrival to its reply written:
  `evento.server.command.phase{phase=queue|codec|lock|story|forward|verify|append|snapshot|unlock|reply|total, keyed}`
  (Micrometer timer with p50/p95/p99 and a percentile histogram), plus one DEBUG line per command
  (`event=command_phases`, logger `com.evento.server.es.phases`). Bundles, which have no
  Micrometer, measure their side with plain accessors: `EventoBundle.getLatencies()` /
  `BundleClient.latencies()` give the round trip of every request by payload type and each
  handler's execution time by type (`aggregate|service|query`, with decode/encode), on a lock-free
  log-linear `LatencyHistogram`. `BusLifecycle.TimedRequestHandler` / `RequestTiming` hand a
  local handler its request's arrival, dispatch and reply-written marks. `CommandLatencyBenchmarkIT`
  (opt-in, `EVENTO_RUN_BENCH=true`) measures the breakdown on a real stack; the local and WAN
  numbers, and what to optimise next, are in `evento-lab-cluster/docs/command-latency.md`.

- **Service command outcome store: the last G5 window closed (inbox pattern).** A service handler
  that ran, and whose event the broker then could not store (the append failed, or the server
  died between the handler's answer and the append), used to run again on the broker's retry: the
  broker had no record of the run. The bundle that ran it now keeps that record.
  `EventoBundle.Builder.setServiceCommandOutcomeStore(ServiceCommandOutcomeStore)` (off by
  default): a keyed service command whose key has a recorded outcome is answered with it and the
  handler is not run (`event=service_outcome_replayed`); a key recorded for another command type
  is refused with `IdempotencyKeyMismatchException`; otherwise the handler runs and its outcome is
  recorded before the reply leaves. The broker needs nothing new: its retry claims the key (or
  takes an expired claim over), forwards, and stores the answer like any other.
  `ServiceCommandOutcomeStore` (`find`, `record`, `recordInTransaction`, `sweepBefore`),
  `InMemoryServiceCommandOutcomeStore` and `ServiceCommandOutcomes` live in
  `com.evento.common.messaging.outcome`; `JdbcServiceCommandOutcomeStore` (PostgreSQL and MySQL,
  table `evento_v2_service_outcome`, idempotent Flyway migration `V2__service_command_outcome`)
  in `evento-consumer-state-store-jdbc`. For exactly-once with local side effects, record in the
  handler's own transaction: inject `ServiceCommandOutcomes` into the `@CommandHandler` (or use
  `ServiceCommandOutcomes.current()` in a `MessageHandlerInterceptor`) and call
  `recordWith(connection, result)` before committing; the side effects and the outcome then commit
  together or not at all, and a second execution racing on the key gets
  `DuplicateServiceOutcomeException`, rolls back and is answered with the first outcome. Side
  effects outside that database (a payment provider) are not covered by any store: pass the key
  downstream (`ServiceCommandOutcomes.idempotencyKey()`). The store must be shared by every
  instance that can receive the retry. `ServiceOutcomeReplayPostgresIT` (real TCP and Postgres:
  failed append; server death with lease takeover on another server), `ServiceManagerOutcomeStoreTest`,
  the JDBC store on both dialects. Measured on `evento-lab-cluster` (new scenario S18, which cuts
  the server's link to the event store while committed payments are still answering): 3 payments
  caught in the window, 0 executed again; with the store switched off, 3 executed twice.
- **Startup warning on JDK 25.0.1 / 25.0.2.** Bundles (`EventoBundle.Builder.start`) and the
  bus (`BusLifecycle.start`) log `event=jdk_virtual_thread_timer_bug` on those builds: they carry
  JDK-8370887, where cancelling a timed park corrupts the virtual-thread timer heap and timed
  waits (sleep, timed get, tryAcquire) can stall for ~20 s. Bundle handlers, consumer engines and
  the connection supervisor run on virtual threads. Use JDK 25.0.3 or later.
- **Idempotency keys: exactly-once commands across retries and servers (G5).** A command can
  carry an idempotency key (`CommandMessage.getIdempotencyKey`, an additive wire field: older
  peers send none and skip it). `CommandGateway.sendIdempotent(command, key[, metadata,
  timeout, unit])` sends with an explicit key (keys starting with `evento:` are reserved and
  refused); existing `send` signatures are unchanged. Commands a saga, projector or observer
  handler sends without a key get `evento:<consumerId>:<event seq>:<CommandSimpleName>:<n>`, `n`
  counting the commands of that type in the handler invocation, so a redelivered event or a
  handler retry re-issues the same keys with no code change. Keys are derived only for gateways
  that declare `CommandGateway.supportsIdempotencyKeys()` (`CommandGatewayImpl`, unless a subclass
  overrides only the five-argument `send`); a pre-G5 decorator keeps receiving plain sends. The
  broker stores the key with the event, together with the command's identity
  (`es__events.idempotency_key` / `idempotency_command`, partial `UNIQUE` index, added online at
  boot by `AdvisoryLockedSchemaInitializer`; `db/upgrade/es-events-idempotency-key.sql` for
  schemas managed outside the server) and answers a command whose key is already stored for the
  same command type and aggregate with that event, before any lock or forward, on whichever
  server the retry lands (`event=idempotent_replay`, `evento.server.commands.idempotent.replayed`).
  A key recorded for a different command is refused with `IdempotencyKeyMismatchException`
  (`evento.server.commands.idempotent.mismatched`), never answered with the other command's
  event. Concurrent duplicates run the handler once: aggregate commands re-check the key under
  the aggregate lock (the unique index backs it up); service commands claim the key in
  `es__idempotency_claims` (a row with a lease, no connection held while executing or waiting,
  so a keyed command never needs a second lock-pool connection). A service handler that returns
  nothing records its key too, so its retries are answered instead of re-run. A keyed command
  that stops waiting on another execution of its key gets `IdempotencyKeyInProgressException`
  (indeterminate), never "expired"; one dropped at a deadline stage whose key is found stored is
  answered with the stored event. Keys are enforced only on PostgreSQL with the full schema in
  the `search_path` schema; otherwise (or if the index disappears later) keyed commands run
  unkeyed with a warning. Only successes are recorded (a refused command can be retried with the
  same key). A service handler that ran while its event could not be stored is covered by the
  service bundle's outcome store (below), not by the broker; during a rolling upgrade a
  keyed retry that lands on a pre-G5 server runs unkeyed (upgrade all servers first); keys never
  expire, so rewinding a consumer answers the commands it re-sends as replays (rewind under a new
  consumer id to run them again).

- **`Context.WILDCARD` (`"*"`).** The pattern that reads every context now has a name
  that says so. `Context.ALL` stays as a deprecated alias whose javadoc warns that it is
  the wildcard, not a context named `"ALL"`: a consumer configured with the literal
  `"ALL"` reads only events whose context is `"ALL"` (a natural name for cross-region
  events), and publishing an event with `Context.ALL` gives it the context `"*"`. The
  framework uses `WILDCARD` internally. `EventoBundle.Builder.setComponentContexts` now
  documents that it starts one consumer engine (own consumer id, checkpoint and lock) per
  context, that a component with no contexts reads every context (the javadoc used to
  claim `Context.DEFAULT`), and that `"ALL"` is an ordinary context. The
  `@Projector`/`@Saga`/`@Observer` javadoc examples no longer show the removed `context`
  attribute. A real-Postgres case pins that fetching `"ALL"` returns only `"ALL"` events.
- **Broker endpoint failover for bundles (G2).** `BundleClientConfig` / `BundleClient.Builder`
  take an ordered endpoint list (`endpoint(host, port)` / `endpoints(...)`, most preferred
  first); `host`/`port` keep working as a one-element list. `EventoBundle` now passes
  *every* address of `EventoServerMessageBusConfiguration` — before, addresses 2..n were
  silently ignored and a bundle whose broker died stayed stranded next to two healthy ones.
  `ConnectionSupervisor` moves to the next endpoint after a session that fails before READY,
  and restarts from endpoint 0 after a READY session drops (and, since N16 below, also moves
  back on its own once endpoint 0 is healthy again). Backoff counts passes over the list, so the
  second broker is tried after one base delay; `start()` fails only once every endpoint was
  tried. `event=handshake_complete` now logs `endpoint=` and `cluster=`.
- **Fail-back to the preferred broker (N16).** A bundle that failed over used to stay on the
  fallback broker until that session dropped, so after a server restart a whole cell kept
  crossing the WAN, and callers and handlers of one cell could end up on different servers
  (`no handler for`, G1). While READY on any endpoint but 0, `ConnectionSupervisor` now probes
  endpoint 0 every `failbackProbeInterval` (5 s) with a TCP connect and a transport `Ping`, which
  the broker's heartbeat handler answers below the message router: no `Hello`, so a probe never
  registers the bundle, publishes `NodeJoined`, replays buffered replies or supersedes a session.
  A broker answers the probe only once it is ready (`BusLifecycle.setProbeReadiness`; the Spring
  server follows Boot's `ReadinessState.ACCEPTING_TRAFFIC`, since its bus binds long before JPA
  and the event store are up); the gate applies only to a Ping on a connection that has sent
  nothing else, so live sessions and older bundles are unaffected, and an older server simply
  answers every Ping. Once endpoint 0 has answered every probe for `failbackStabilityWindow`
  (30 s) plus a random per-instance `failbackJitter` (up to 10 s) the bundle switches: new
  outbound requests refused with `SendFailedException` (definitely not sent), `evento:disable`
  on the current broker, a bounded drain (`failbackDrainTimeout`, 10 s) of inbound and outbound
  requests in flight (an inbound one counts until its reply is fully written), then the session
  is closed and endpoint 0 dialled without backoff. Replies and notifications still go out during
  the drain; `evento:enable` is deferred to the registration on endpoint 0. Requests still
  pending after the drain fail with `BrokerChangedException` at the new `Welcome`, as after a
  failover. A request that the current broker answers with "no handler for" while endpoint 0
  answers probes switches at once instead of after the window (`event=failback_reactive`): in an
  HA pair the fallback has no handler left once the cell's handlers are home. A failed probe
  restarts the window, so a flapping broker never gets the bundle back; a switch whose handshake
  fails (e.g. `expectedClusterName`) lands on the next endpoint and doubles the next window (up
  to 16x), and three refusals in a row (`BrokerRefusedException`: identity, protocol or `Reject`)
  suspend fail-back (`event=failback_suspended`, ERROR) until a session on endpoint 0 succeeds by
  other means or the process restarts. Bundles that handle no requests (gateways) wait the whole
  jitter range on top, so that a cell's handlers are back before its callers. Logged as
  `event=failback_started` / `failback_drained` / `failback_reactive` / `failback_completed` /
  `failback_failed` / `failback_suspended`; counted by `BundleClient.failbacksCompleted()` /
  `failbacksFailed()` / `reactiveFailbacks()`. Configurable on `BundleClientConfig` /
  `BundleClient.Builder` and on `EventoBundle.Builder` (`setFailbackEnabled`,
  `setFailbackProbeInterval`, `setFailbackStabilityWindow`, `setFailbackJitter`,
  `setFailbackDrainTimeout`); on by default with two or more endpoints, `failbackEnabled(false)`
  restores the previous behaviour. No wire change. `BundleClientFailbackIT` (two real brokers
  over TCP): fail-back after the window with a clean leave on the fallback, no registration from
  probes, no fail-back from a flapping or a not-ready broker, in-flight requests completed or
  failed with `BrokerChangedException`, a 40 MiB reply written during the drain arriving whole,
  identity refusal with a doubled window and then suspension, the reactive switch of a caller,
  `enable()` during the drain, `stop()` racing the switch; `ProbeConnectionIT` (transport);
  `StartupDuringFailbackIT` (evento-lab: an `EventoBundle` whose startup gate opens mid-drain).
- **`RequestNotSentException`** (`evento-transport-api`, an `IllegalStateException`). A request
  the bundle refused before writing anything (between sessions, or during a fail-back drain)
  now reaches the v1 gateways as this type instead of a plain `IllegalStateException`: nothing
  was executed, so it is always safe to retry, unlike a timeout.
- **`TransportServer.setPreSessionReadiness(BooleanSupplier)`** (default no-op; implemented by
  `NettyServerTransport`) and **`NettyEventLoops.newNioEventLoopGroup(int, ThreadFactory)`**
  (now public: the Netty 4.1-safe way to build an event loop outside the transport module).
- **`BrokerChangedException` (G4).** Each pending request remembers the broker it was sent
  through. When a new session's `Welcome` names a different `serverInstanceId`, those
  requests fail at once (a failure `Response` naming `BrokerChangedException`, rethrown as
  such by `EventoServerAdapter`) instead of hanging for the full request timeout: the old
  broker buffers their replies and replays them only to a bundle that comes back to *it*.
  It extends `RequestTimeoutException` because the outcome is equally indeterminate.
  Reconnecting to the same broker keeps the survive-reconnect behaviour.
- **Cluster identity in the handshake (G13).** `Welcome` gains an additive `clusterName`
  (from `evento.cluster.name`, `EVENTO_CLUSTER_NAME`; empty means not announced, and older
  peers decode it unchanged). The shipped `application.properties` sets it to
  `evento-server`, so every stock server announces the same name: set it per deployment,
  or requiring it protects nothing (the server logs `event=cluster_name_shipped_default`
  at WARN while it keeps the shipped value),
  and bundles gain `expectedClusterName` (`BundleClient.Builder`, `BundleClientConfig`,
  `EventoServerMessageBusConfiguration.withExpectedClusterName`). A broker announcing another
  cluster, or none, is refused before registration and the bundle keeps dialing the next
  endpoint (`event=cluster_name_mismatch`). With an endpoint list use this rather than
  `expectedServerInstanceId`, which would refuse every failover target; give each server a
  unique `evento.server.bus.server-instance-id`, since G4 relies on it.
- **Link settings on `EventoBundle.Builder` (G22).** `setTransportConfig`,
  `setHeartbeatReadIdle` (also lowers the Ping cadence to a third when needed),
  `setReconnectStrategy`, `setDefaultRequestTimeout`, `setMaxInFlightRequests`; plus
  `NettyTransportConfig.withHeartbeat` / `withReconnectStrategy` and
  `BundleClient.Builder.maxInFlightRequests`. The 45 s read-idle — how long a silent
  partition goes unnoticed — was not reachable from the bundle before.
- **Locality-aware handler selection (G12).** A bundle can declare where it runs with
  `EventoBundle.Builder.setZone("EU")` (or `BundleClient.Builder.zone(...)` /
  `BundleClientConfig.Builder.zone(...)`). The zone travels as a `zone:<ZONE>` entry in the
  existing `Hello.capabilities` set — no new wire message or field, and a broker that knows
  nothing about zones ignores it. A broker started with
  `evento.server.bus.pick-strategy=locality-preferred` (new `ClusterRegistry.PickStrategy.LOCALITY_PREFERRED`)
  routes each request to an available handler in the same zone as the bundle that sent it and
  falls back to any available handler when that zone has none, so a dead region costs latency
  rather than availability. Commands brokered by `CommandBrokerHandler` pick on behalf of the
  original sender through the new `BusLifecycle.OriginatorAwareRequestHandler`. The default
  stays `random`. The zones a server knows are listed at `GET /api/cluster-status/zones`.
  `LocalityPickIT` runs three handler replicas (EU, AM, APAC) and an AM caller over real TCP:
  every call stays in AM until the AM replica is killed, then spreads over EU and APAC.

### Changed

- **Idempotency keys no longer cost round trips of their own.** Measured on the lab (S00): a keyed
  command from a remote cell paid one or two extra WAN round trips to the event-store primary
  (p50 AM +~100 ms, APAC +~200–300 ms). Counted at the JDBC driver, a keyed aggregate command ran
  4 event-store statements against 2 unkeyed, a keyed service command 6 against 1. Aggregate
  commands now read the key in the story fetch under the aggregate lock (a `UNION ALL` branch of
  the events query; no lookup before the lock): **2 = 2**. Service commands claim or answer in
  one statement (a CTE reads event and claim and inserts the claim only when no event holds the
  key), and the append marks the claim completed in the same statement, so there is no re-check,
  no release on success, and no renewal unless an unbounded lock wait outlasted the first lease:
  **2 against 1** (the claim must precede a handler with side effects). Replays cost one statement
  either way. Claim rows are now kept (completed) after the event is stored: a claim can never be
  won for a key whose event exists, even one committed after the claimer's snapshot, which is what
  made the re-check unnecessary. Replay log stages: aggregate replays are now `stage=with_story`.
  `BrokerEventStore` gains `fetchAggregateStory(..., key)`, `claimOrFindIdempotencyKey` and a
  claim-completing `publishEvent(..., claimOwner)`, with defaults that compose the previous calls.
  Pinned by `IdempotentCommandPostgresIT.aKeyedFirstAttemptCostsTheRoundTripsOfAnUnkeyedOne`
  (CPES and APES). On the lab, keyed S00 p50 is back to the unkeyed range (AM 667–704 ms, APAC
  1135–1220 ms, against 834–879 ms and 1359–1472 ms before this change). Upgrade all servers
  together: a server from before this change deletes its claim after the append instead of
  completing it, which reopens, for the roll-out, a window of microseconds in which a newer
  server's claim can miss an event committed after its snapshot.
- **`PgDistributedLock` waits are bounded and a lost lock is an error.** `acquire` used to
  wait forever; it now throws `LockAcquisitionTimeoutException` after 60 s
  (`evento.lock.acquire.timeout.ms`, a Spring property like the pool settings, env
  `EVENTO_LOCK_ACQUIRE_TIMEOUT_MS`; the `-D` system property of the same name is the
  fallback). It logs `event=lock_acquire_timeout` at WARN only when that bound ran out; a
  wait cut short by a command's deadline is logged by the broker as its `lock_wait` drop.
  `lockedArea`/`tryLockedArea` throw
  `LockLostException` when the lock turns out to have been lost, even if the section
  succeeded (its effects are applied but may have overlapped another holder). The
  server's callers (bundle discovery, consumer registration) catch and log both, as they
  did any database error; a discovery that times out is logged instead of hanging a
  thread, and is redone on the bundle's next reconnect. `tryAcquire` never waits: it
  returns `false` at once when the key is held or every lock connection is in use.
- **Distributed locks use a connection pool of their own** (`evento.lock.pool.maximum-size`,
  default 10; `evento.lock.pool.connection-timeout-ms`, default 1000). It copies the
  application pool's settings. The lock is told the pool size (new
  `PgDistributedLock(DataSource, Duration, int maxConnections)`) and takes a permit per
  connection before borrowing, so an exhausted pool no longer makes each attempt wait the
  pool's connection timeout: telemetry's `tryLockedArea` fails at once instead of holding
  a bus dispatch thread for a second, and `acquire(key, maxWait)` keeps to `maxWait`.
- **Upgrade note, PostgreSQL connections:** the shipped application pool is now
  `maximum-pool-size=20` with `minimum-idle=5` (before: Hikari's default 10, all opened at
  boot), plus the lock pool (10, 2 idle). Peak per server is 30 connections, idle 7:
  three servers peak at 90, inside PostgreSQL's default `max_connections=100`. With more
  servers, or other clients on the same database, raise `max_connections` or lower these
  before upgrading.
- **Upgrade note, PostgreSQL 12 or later is required** (11 and older are end-of-life and
  untested; the ITs run 16 and 17). The server checks `server_version_num` at boot and
  refuses an older server with a clear message, instead of failing later on a statement.
- **Upgrade note, `es__events` primary key:** the first server of this version to boot
  builds a unique index on `es__events(event_sequence_number)` with
  `CREATE INDEX CONCURRENTLY` (reads and appends continue; it takes as long as a scan of
  the table), then attaches it as the primary key, which needs `ACCESS EXCLUSIVE` only
  for an instant (`lock_timeout` 3 s, retried). Other booting servers wait for it on the
  schema lock. This and the catalog re-key below run only when Boot runs `schema.sql`
  (`spring.sql.init.mode=always`, the shipped value). With the schema managed outside the
  server, apply the shipped scripts instead (`db/upgrade/es-events-primary-key.sql` and
  `db/upgrade/n1-component-id.sql` in `evento-server.jar`): the server then refuses to
  boot over a pre-N1 catalog, naming the script, and warns when the primary key is
  missing.

- **Upgrade note, component catalog re-keyed (N1):** the first server of this version to
  boot migrates an existing catalog in place, in one transaction under the schema lock
  (`event=catalog_component_rekeyed`): it adds `core__component.id`, moves
  `core__handler` / `core__consumer` from `component_component_name` to `component_id`,
  and swaps the primary and foreign keys. Rows the old keying had already mixed up keep
  pointing at the bundle that owned the row until that bundle's next discovery. Servers
  of an older version sharing the database fail their catalog writes after the
  migration (routing is in memory and unaffected): upgrade the servers together.

### Fixed

- **`EventoBundle` startup no longer dies when its gate opens between sessions.** When the last
  head-gating projector caught up while the bundle was reconnecting or draining for a fail-back,
  `enable()` threw, the startup thread logged "Error during startup" and stopped: sagas and
  observers never started, no stats, no started hook, although the latched enable made the
  bundle look healthy after the next reconnect. `BundleClient.enable()` now never fails for want
  of a connection (the next session sends it) and the consumer registration listener is
  installed before the first send, which no longer aborts startup.
- **`NettyClientTransport.close()` during a pending connect** leaked the channel that connected
  afterwards (it found no channel to close); it now aborts the connect, and a connect that
  completes after close is closed at once.
- **`ConnectionSupervisor.stop()` racing a reconnect** could be overwritten by the session's next
  state transition, leaving a supervisor that reconnected behind a stopped bundle; `CLOSING` and
  `CLOSED` are now final. The probe event loop is released on stop and on give-up, and a stop no
  longer logs WARNs of its own.
- **Commands were executed after their caller had timed out (N3).** The broker ignored
  the caller's deadline: a command queued behind a saturated dispatch executor or an
  aggregate lock was still forwarded, and the forward then got a fresh full timeout, so
  under load every timed-out command was stored anyway (92 of 92 in the lab) and a saga
  retrying a "failed" payment applied it four times. `Request.timeoutMillis`, which every
  sender has always filled from its request timeout, is now read as the caller's
  remaining budget: the broker derives a local deadline from it and the frame's arrival
  time (`Request.deadlineFrom`, `Frame.receivedAtNanos`, stamped on the I/O thread before
  any queueing; no cross-machine clock comparison, and a monotonic clock so an NTP step
  neither expires every queued command nor stretches deadlines), and drops the request unexecuted
  when the deadline has passed before routing (`queued`), while waiting for the command
  lock (`lock_wait`, the wait is now bounded by the deadline:
  `PgDistributedLock.acquire(key, maxWait)`), before the forward (`before_forward`), or
  when an aggregate handler has not answered in time (`before_append`; the forward budget
  is what is left of the deadline and no event is appended). A service command is dropped
  only before its lock and its forward: once its handler has run (with side effects of its
  own) its event is always stored, even when it answers after the deadline, and a failed
  forward after the deadline stays an indeterminate error. Each drop logs
  `event=request_expired_dropped stage=...`, counts in
  `evento.server.requests.expired{stage}` and answers `RequestExpiredException` (new),
  which `EventoServerAdapter` rebuilds as that type. A caller normally does not see it:
  the broker's deadline is never earlier than the caller's own timer, so the caller has
  already failed the request with the indeterminate `RequestTimeoutException`. The gain is
  that nothing runs for a caller that gave up (see the metric); do not branch on
  `RequestExpiredException`.
  Requests without a timeout (`timeoutMillis = 0`) keep the old behaviour, and no wire
  field was added. A command that passes the last check and outlives its deadline during
  the append itself is still indeterminate; that needs an idempotency key (G5). Tested
  over real TCP (`CommandDeadlineIT`): lock wait, late handler, late service handler,
  saturated dispatch queue, and the no-deadline path.
- **A rejected Hello could wipe a live instance's catalog rows and zone.** The broker
  registered a session before validating its Hello, so a Hello refused for a bad token or
  protocol that carried the instanceId of a live instance published `NodeJoined` then
  `NodeLeft`: the presence claim took the instance's row, the release found it owned
  here and not connected, and cleared the instance's consumers and, with no other
  instance, its whole bundle catalog (the G7 outcome). It also overwrote, then erased, the
  live session's zone. The Hello is now validated first (`HandshakeHandler.validate` /
  `reply`); a rejected one touches no shared state.
- **A reconnect could erase an instance's zone.** The zone was recorded before the new
  session's registration and forgotten by instanceId alone, so the old session's teardown,
  on another event loop, could erase the zone the new session had just recorded: that
  instance then routed as zone-less under `locality-preferred` until its next reconnect.
  Zones are now tagged with the connection token, recorded after registration and
  forgotten only by the session that recorded them (`ClusterRegistry.forgetZone(node, token)`).
- **A failed lock attempt leaked a lock-pool slot for good.** When something failed after
  `pg_try_advisory_lock` had granted the lock, the connection was aborted but never closed,
  and Hikari only recycles an entry on `close()`: each occurrence permanently lowered how
  many locks the server could hold (leak detection is off for that pool). It is now
  aborted and closed, so the pool evicts it.
- **A `lockId` whose hash collided with the event store lock hung its own append.** Command
  locks and the CPES append lock share the `hashtext` key space; a key colliding with
  `hashtext('es-lock')` held that lock on one session while its append waited for it on
  another, which PostgreSQL cannot detect, stalling every appender. Such a key is now
  locked as `hashtext(key || '#evento-lock')`; the key `es-lock` itself keeps mapping to
  the append lock, so servers of the previous version still exclude each other.
- **Presence timing was unchecked.** A `stale-after-ms` at or below
  `heartbeat-interval-ms` made every server's live presence rows go stale between
  refreshes, and a server then unregistered bundles still live elsewhere. Boot now fails
  in that case and warns below 3 x the heartbeat; the heartbeat gets its own scheduler
  thread (`spring.task.scheduling.pool.size=2`) so the hourly telemetry sweep cannot
  delay it.
- **The CPES append no longer relies on `WITH ... AS MATERIALIZED`.** The keyword is
  dropped: a CTE calling the volatile `pg_advisory_xact_lock` is materialised anyway, so
  the lock-before-`nextval` order holds without it, and an unsupported server now fails at
  boot (above) rather than on its first append.
- **GUI: a component name used by several bundles broke the component page.** The page
  read the `409` body as a component. It now lists the candidate ids as links, and the
  bundle page links each component by its id.
- **The catalog keyed components by class name across bundles (N1).** `core__component`
  had `component_name` as primary key, so the same class deployed by several bundles
  (one projector class run by a `query-<region>` bundle per region) shared one row: all
  but one bundle lost their `core__handler` rows, and one bundle's unregister deleted
  every other bundle's `core__consumer` rows with it (the lab lost all 7 regions'
  consumer rows on one JVM restart). Components are now keyed per bundle,
  `core__component.id = bundle_id || ':' || component_name` (`Component.idOf`), with a
  unique `(bundle_id, component_name)` index; handler and consumer rows reference
  `component_id`. Discovery, consumer registration, `BundleService.removeBundle`, the
  catalog/bundle/payload queries and the telemetry lookups all resolve components within
  their bundle. Consumer rows stay keyed by `instanceId_consumerId`, and the consumer id
  carries the context, so one bundle id run by several instances with different contexts
  (`CINA` in one JVM, `EUROPA,AFRICA` in another) lists every lane and a leaving instance
  removes only its own. REST: `ComponentDTO`, `HandlerDto`, `ConsumerDTO` and the
  component list gain `componentId`; `GET /api/catalog/component/{identifier}` takes the
  id, or a bare name when exactly one bundle has it (`409` with the candidate ids
  otherwise). The GUI links components by id. Tested on real Postgres with two servers
  (`MultiServerCatalogIT`) and the in-place migration (`SchemaInitializationPostgresIT`).
- **`WATERMARK` bookkeeping cost grew with the numeric width of a gap (G10).** Since
  2.4.6 the processor closes every sequence number a fetched batch proves absent — but it
  did so one number at a time into a `TreeSet<Long>`. A consumer filtered to one context
  of seven added ~6/7 of the shared sequence to the set on every batch, and a
  Snowflake-numbered store (APES, ids ~4.2 billion apart per millisecond) turned a single
  gap into a loop that hung the consumer or exhausted the heap. Resolved numbers above the
  watermark are now kept as disjoint intervals (`SequenceRangeSet`): each gap is closed in
  one O(log n) step and memory is bounded by the handlers still in flight, not by how
  sparse the stream is. Semantics are unchanged — every 2.4.6 `WatermarkCheckpointTest`
  case passes as is — and new cases pin a 1-in-7 context-filtered consumer with a held
  handler (≤ 2 intervals over ~70,000 resolved numbers) and Snowflake-scale gaps under a
  5 s timeout.
- **A consumer checkpoint could go backwards (G11).** A lock can be lost without its
  holder noticing (partition, database failover); the stale holder's next commit failed
  the version check, and the processor's drift path re-read the version and committed its
  older position anyway, rewinding the lane for the new owner. `ConsumerStateStore.commit`
  is now monotonic by contract: the JDBC store writes
  `last_sequence = GREATEST(last_sequence, ?)` (Postgres and MySQL), the in-memory store
  does the same, and the processor's drift path no longer commits at all when the stored
  position is already at or past its own. Only `delete` lowers a position. JDBC ITs on
  both databases show a stale replica with its own connection cannot rewind the row, and
  8 replicas retrying on drift converge on the highest position.
- **`PgDistributedLock`: one waiter could stall every lock of a server, and a lost lock
  went unnoticed (G3).** Each instance pinned a single connection and blocked in
  `pg_advisory_lock` on it, so a thread waiting on a key held by another server queued
  every other acquire *and release* of that instance behind it (two servers could
  deadlock each other until `socketTimeout`), and when that session died PostgreSQL
  dropped all its locks while the JVM still believed it held them. Each held lock now
  owns its own connection (borrowed on acquire, unlocked and returned on release) from
  a pool dedicated to locks, so holders can never pin the connections their own work
  needs (with a shared pool, as many locked commands as connections all timed out in
  `getConnection()` together); waiting polls `pg_try_advisory_lock` with jittered backoff, holds no
  connection between attempts, and is bounded (60 s default,
  `-Devento.lock.acquire.timeout.ms` or the new constructor) with
  `LockAcquisitionTimeoutException`, and a pool timeout counts as a failed attempt.
  `verifyHeld(key)` checks `pg_locks` on the owning session (not just that the connection
  is alive) and `release(key)` detects a lost lock; both log `event=lock_lost` and throw `LockLostException`;
  `CommandBrokerHandler` calls `verifyHeld` right before appending, so a command whose
  lock died while the bundle computed it is refused instead of forking the aggregate,
  and a release failure after a successful append no longer replaces the reply. The
  check narrows the fork window but cannot close it; that needs the G6 `aggregate_seq`
  fence. A failure right after the lock was granted no longer returns the lock-owning
  connection to the pool (which would have kept the key locked for good).
- **CPES appends cost one round trip instead of four (G9).** The global `es-lock`,
  `nextval` and the `INSERT` ran as separate autocommit statements on two connections.
  They are now a single statement (`pg_advisory_xact_lock` in a materialised CTE +
  `INSERT ... SELECT nextval(...) RETURNING`), i.e. one implicit transaction whose lock
  is released only after the commit is visible, so sequence order still equals commit
  order and the lock cannot outlive a dead session. Same lock key as before, so old and
  new servers exclude each other during a rolling upgrade. Measured through a 100 ms
  delay proxy: ~4.2 round trips before, ~1.1 after (`EventStorePostgresIT`).
- **`es__events` has a primary key (G6, PK part).** Added idempotently on boot, without
  blocking the event store: `CREATE UNIQUE INDEX CONCURRENTLY`, then
  `ADD PRIMARY KEY USING INDEX` under a short `lock_timeout` (see the upgrade note under
  Changed). Existing duplicate sequence numbers fail the boot with their count and the
  query to find them, rather than being skipped silently;
  `evento.es.schema.enforce-primary-key=false` is the explicit escape hatch.
- **Servers starting together no longer abort on `schema.sql` (G14).** Concurrent runs
  raced on the catalog (`pg_type_typname_nsp_index`, `tuple concurrently updated`);
  unlocked, 3 servers on a fresh database failed 10 rounds out of 10 in a probe. The SQL
  initializer now runs under a session advisory lock, taken by polling so a waiting
  server holds no snapshot the index build would wait for (`AdvisoryLockedSchemaInitializer`,
  replacing Boot's `DataSourceScriptDatabaseInitializer`; `spring.sql.init.*` unchanged).
- **Context filters no longer treat `_` and `%` as wildcards (G15).** `SUD_AMERICA`
  also matched `SUDXAMERICA`. LIKE metacharacters are escaped before `*` becomes `%`,
  and the fetch queries declare `ESCAPE '!'` (a backslash escape would depend on
  `standard_conforming_strings`).
- **Aggregate caches are thread-safe and honour their TTL (G19).** `ExpiringLruCache`
  was an unsynchronised access-ordered `LinkedHashMap` read through the TTL-bypassing
  `containsKey`/`getOrDefault`; it now wraps the map, synchronises every operation and
  only offers TTL-aware reads. The cached event list is copied, not appended to in place.
  Cross-server invalidation after `deleteAggregate` is still local only.
- **A stale `NodeLeft` on one server wiped a bundle instance that had failed over to
  another (G7).** Servers that share a catalog each see only their own connections. When
  instance I moved from server A to server B, A noticed the dead socket up to 45 s later,
  cleared I's `core__consumer` rows and unregistered the whole bundle cluster-wide,
  undoing B's fresh registration. A new `core__bundle_instance` table (created
  idempotently by `schema.sql`) records which server holds each instance's session: the
  server claims the row on `NodeJoined`, and a leave cleans up only when this server
  still owns it. The "another instance is still live" check now also counts instances
  held by other servers. The last instance out reclaims an ephemeral bundle whichever
  instance the row names, so a bundle no longer leaks when the recorded instance leaves
  first. A restarted server purges the rows it held before. Each server refreshes its
  rows every 30 s (`evento.server.presence.heartbeat-interval-ms`). Rows older than
  3 min (`evento.server.presence.stale-after-ms`) no longer count as live, so a server
  that crashes and never returns under the same id cannot keep its bundles in the catalog
  for ever. A leave re-checks the local view after releasing: a channel-close `NodeLeft`
  of an old session can be published after the new session of the same instance has
  already claimed, and it must not delete that live row. Ownership needs a unique
  `evento.server.bus.server-instance-id` per server. The server logs a WARN at startup
  when it runs with the shipped `iris-evento-server` id.
- **Concurrent discovery on several servers failed on `duplicate key` (G8).** Discovery
  and consumer registration created bundle, component and payload rows find-then-insert,
  under different lock keys. Payload and component names are shared across bundles, so no
  per-bundle lock covered them, and a mass reconnect aborted passes half-written on
  `core__*_pkey`. Those rows are now created with `INSERT ... ON CONFLICT DO NOTHING`, and
  consumer rows are upserted. Consumer registration takes the same `DISCOVERY:<bundleId>`
  lock as discovery and leave. Each discovery pass, leave cleanup and consumer
  registration runs in one transaction. `BundleService.unregister` scans only the leaving
  bundle's handlers; before, it raced the removal of other bundles and hit handlers whose
  component was already gone. The orphan-payload sweep runs after the commit, one
  best-effort delete at a time. Payload rows are shared across bundles, so the discovery
  transaction never writes them: every referenced payload is created up front in name
  order, one statement each, and type corrections and `payloadInfo` metadata are applied
  after the commit, again in name order and only when a value changes. Inside the
  transaction the reused payload rows are pinned `FOR KEY SHARE`, so a concurrent orphan
  sweep cannot delete one before the new handler rows are flushed. A pass that still
  loses a race (deadlock, serialization failure, foreign key violation, or a row that
  vanished) is retried up to three times.
- **The consumers page cut contexts that contain `_` (G16).** `ConsumerController` split
  the consumer id on `_`, so `SUD_AMERICA` showed as `SUD`, and a bundle id containing
  `_` shifted every field. `ConsumerIds` strips the known `bundleId_component_` prefix
  and reads `version_context` from the rest. The page now lists one entry per consumer id
  rather than per component, so each context lane of a multi-context component shows up.
  After a component version bump, the consumers of the old and the new version show as
  separate entries until the old instances leave; before, they were merged per component.
- **Every server ran the hourly telemetry TTL sweep.** It is now guarded by an advisory
  try-lock, so one server per hour runs it.

### Documentation

- **G1 is documented as a design choice.** Each server routes requests only to the bundles
  attached to it, so a request reaches a handler only on the same server (`no handler for`
  otherwise). The cluster readiness notes, the lab findings and the cluster-mode guide now
  describe this as intended rather than as an open blocker: connectivity is regional, a client
  may be connected to only some servers, and a cell with a complete set of handlers next to its
  server is the intended topology. A server peer mesh stays an option for partial cells.
- **Context naming and matching (G15, client side).** `EventoBundle.Builder.setComponentContexts`
  now documents that `*` is the only wildcard in a context value and that servers without the
  G15 fix treat `_` and `%` as `LIKE` wildcards too (`SUD_AMERICA` also matches `SUDXAMERICA`).
- **Commands carry no framework-level context (G23).** The stale `DomainCommand` javadoc that
  described a `PayloadWithContext` base class is replaced by what is true: only `Event` has a
  context, and an aggregate that emits region-scoped events copies the context from a command
  field into the event.

---

## [2.4.6] — 2026-09-05

### Fixed

- **A burned sequence number pinned every `WATERMARK` projector for ever.** The event
  store's sequence is non-transactional: an insert that rolls back keeps the number it
  drew and the store never returns it. `InFlightTracker.advanceWatermark()` walked a
  strictly contiguous prefix, so the first such hole stopped the persisted checkpoint at
  the number before it — while the in-memory fetch cursor went on. Read models stayed
  current, so nothing looked wrong; but every restart replayed the whole gap (124k events
  x 12 projectors in the case that surfaced it), which was enough to OOM-kill a 2 GB
  broker, and the checkpoint never recovered without a manual `UPDATE`. The processor now
  closes, from each fetched batch, every number between the cursor and the batch's last
  event that the store did not return — a hole is proven by the batch that skips it, and
  nothing else will ever complete it. Numbers above a batch are not assumed.
  `WatermarkCheckpointTest` pins the cold-start shape (checkpoint N, first existing row
  N+2), holes mid-batch, holes revealed only by a later fetch, and that a hole never
  releases an event that is still running. The `CheckpointMode` javadoc no longer claims
  inline consumers behave identically in both modes — they do not, in exactly this case.

### Added

- **`expectedServerInstanceId` on `BundleClient` / `EventoServerMessageBusConfiguration`
  (`withExpectedServerInstanceId`).** A bundle dials a host name; two brokers reachable
  under the same name — two compose projects on one Docker network, each calling its
  broker `evento-server` — are indistinguishable at the transport, and in production a
  bundle registered with a demo's broker during a 22-second outage of its own and spent
  an hour and forty minutes persisting a shop's events into the wrong event store while
  looking perfectly healthy. The `Welcome` carries the broker's `serverInstanceId`; when an
  expected id is configured and the announced one differs, the supervisor logs
  `event=server_instance_mismatch`, refuses **before** registration, and keeps dialing
  with the usual back-off — a wrong broker is treated like an unreachable one. Unset, the
  behaviour is unchanged. Give each broker its own `evento.cluster.node.server.id` for this
  to mean anything. `BundleClientIdentityIT` covers refusal, acceptance and the unset case
  over real TCP.

---

## [2.4.5] — 2026-08-06

### Fixed

- **2.4.4 broke bundle startup under Spring Boot's dependency management.** Boot's BOM pins
  `io.netty:*` to the 4.1 line inside user applications regardless of what
  `evento-transport-netty` declares, and 2.4.4's internal migration to Netty 4.2 APIs used
  `MultiThreadIoEventLoopGroup`, which does not exist in 4.1 — every unpinned Boot app died
  at startup with `NoClassDefFoundError` while constructing the bundle. Event-loop
  construction is back on the 4.1-compatible `NioEventLoopGroup` (deprecated in 4.2 but
  present and functional in both lines), and the transport test suite now also runs with
  Netty forced to 4.1 in CI (`netty41Test`) so a 4.2-only API can never ship again.
  Workaround on 2.4.4: `ext['netty.version'] = '4.2.16.Final'` (Gradle) or the
  `<netty.version>` property (Maven).

---

## [2.4.4] — 2026-08-05

### Fixed

- **`PgDistributedLock.release()` race** — under concurrent publishing through one lock key
  (the event store funnels every service command through `"es-lock"`), a thread that
  legitimately held the lock could get `IllegalMonitorStateException: No lock held for key`
  on release: the decrement of the wrapper's queue count and its removal from the shared map
  were two separate steps, so a concurrent `acquire` could join a wrapper about to be
  unmapped. Thrown from `lockedArea`'s cleanup, the error also replaced the critical
  section's real outcome (a successfully published event surfaced to the caller as a
  failure) and leaked the PostgreSQL advisory lock. Decrement-and-unmap is now atomic, and
  `lockedArea` no longer lets a cleanup failure mask the critical section's result — it is
  attached as suppressed to an in-flight exception, or logged after a success.
- The release scripts read the version with a pattern broken by the Gradle assignment-syntax
  change below; both now tolerate either style.

### Changed

- **GUI build system** — `evento-gui` moved from the deprecated webpack `browser` builder to
  Angular's esbuild `application` builder: production build 64s → 16s, initial bundle
  ~2 MB → 265 kB raw (Ionic components now lazy-load as ~115 code-split chunks). No
  behaviour change for the embedded GUI; output still lands in the server's `public/`.
- Internal API modernisation, no user-facing change: Netty 4.2 non-deprecated APIs
  (`MultiThreadIoEventLoopGroup`, `WRITE_BUFFER_WATER_MARK`), Spring `JdbcTemplate` varargs
  query overloads, Gradle 10 assignment syntax across all build files, Angular 22.1 /
  Ionic 8.8.17 / jsdom 30 dependency bumps.

---

## [2.4.3] — 2026-07-28

### Changed

- **The projector head-reached startup gate is now opt-in.** The bundle no longer waits for
  projectors to align with the event-stream head before enabling: default is
  `@Projector(waitForHeadReached = false)` — the bundle enables immediately after
  registration and projectors catch up in the background. Annotate
  `@Projector(waitForHeadReached = true)` to restore the old gate for projectors whose
  queries must not be served from a behind-head read model.

### Fixed

- `EventoBundle` component scanning now respects `setBasePackage` exactly: previously every
  class sharing the base package's classpath *roots* was scanned too, so two bundles in one
  jar could never be isolated.

---

## [2.4.2] — 2026-07-27

Re-cut of 2.4.1 after its release pipeline run failed; no code changes.

---

## [2.4.1] — 2026-07-27

### Fixed

- **A broker OOM now kills the process instead of leaving a zombie** (production incident
  2026-07-27: an `OutOfMemoryError` during event-fetch handling killed the Netty worker
  event loops; every `catch (Throwable)` swallowed it, and the broker survived three hours
  accepting TCP connections it could only force-close — the container restart policy never
  fired because the JVM never exited). Three layers:
  - `-XX:+ExitOnOutOfMemoryError` in the evento-server Docker entrypoints;
  - a default uncaught-exception handler in `EventoServerApplication` that halts on
    `VirtualMachineError` (covers threads whose infrastructure swallows errors internally);
  - `FatalErrors.escalateIfFatal(t)` (new, `evento-common`) called first in the broker's
    catch-all handlers (`BusLifecycle`, `BusEventBus`, `ConnectionRegistry`) — walks the
    cause chain, halts with exit code 3 on a fatal error; disable with
    `-Devento.fatal.halt=false` (tests).
- **Stale `EventFetchRequest`s are dropped instead of served** — a consumer abandons a
  fetch after its client-side timeout and immediately retries, so a request older than
  `evento.es.fetch.max.age.ms` (default 30000; ≤0 disables) is rejected after the
  concurrency permit is acquired rather than doing the full result-set + encode work for a
  reply nobody reads. Under memory pressure that retry storm repeated the broker's heaviest
  work exactly when it could least afford it. Unstamped requests (older clients) are never
  dropped.
- **Reconnecting no longer trips an FK violation in discovery** — `Consumer.component` was
  mapped `@ManyToOne(cascade = ALL)`, so deleting an instance's consumer rows (e.g.
  `ConsumerService.clearInstance` when a connection is superseded) cascaded a REMOVE to the
  shared `core__component` row while other instances' consumer rows still referenced it:
  every rejoin logged `violates foreign key constraint
  "core__consumer_component_component_name_fkey"` and rolled back. The cascade is removed;
  component lifecycle stays with discovery/`BundleService`, which already deletes consumers
  before components.

---

## [2.4.0] — 2026-07-26

### Added

- **Parallel consumers** — an `@EventHandler` may name a bounded *consumer executor*
  registered on `EventoBundle.Builder`, dispatching its events in parallel instead of one
  at a time. `executor = ""` (the default) keeps the existing sequential path, so nothing
  changes for a bundle that does not opt in.
  - New SPI `ConsumerExecutor` + factories `ConsumerExecutors.virtual(name, n)` /
    `pooled(name, n)` / `partitioned(name, lanes)`; register with
    `Builder.addConsumerExecutor(...)`. A name is a capacity budget shared bundle-wide.
  - **The checkpoint advances when a task starts, not when it completes.** That bounds how
    far a consumer can run ahead of completion (never more than the executor's capacity),
    so no unbounded prefix of the event store is ever enqueued, and it makes the crash-loss
    window the set of *running* tasks rather than a queue depth. Admission is time-boxed:
    a saturated executor ends the cycle and releases the consumer lock.
  - **Guarantees relaxed** — no ordering between parallel events, and at-most-once for
    events in flight when the process is killed abruptly (a graceful stop drains). Intended
    for idempotent or overwrite handlers; both guarantees can be taken back, below.
  - **`retry = -1` is coerced to `0` under an executor**, because retrying forever inside a
    task pins a concurrency permit and starves the executor. Set an explicit `retry` for a
    parallel handler that calls a remote dependency.
  - Referencing an unregistered executor **fails bundle start-up** rather than silently
    degrading to sequential execution. Not available on `@SagaEventHandler`.
- **`ConsumerExecutors.partitioned(name, lanes)`** — per-aggregate ordering with cross-
  aggregate parallelism: events sharing an aggregate id are pinned to one lane and applied
  in sequence order. Makes parallel consumption usable by read-model projectors that are
  per-aggregate sequential rather than idempotent. No handler change needed.
- **`CheckpointMode.WATERMARK`** (`Builder.setCheckpointMode` /
  `setComponentCheckpointMode`) — persists the highest *contiguous completed* sequence
  instead of the dispatch frontier, restoring at-least-once delivery at the cost of
  replaying the in-flight window after a crash. The fetch cursor still follows the dispatch
  frontier, so nothing is reprocessed within a run.
- **Transient-failure backoff for parallel consumers** — async handlers fail on their own
  threads, so their failures never surfaced to the fetch loop; a downed dependency was met
  by the loop pulling at full speed and dead-lettering the whole stream. Consumers now
  track a transient-failure streak and back off on the same curve as a channel error.
- **Observability**
  - Discovery publishes each handler's `executor`, so the GUI's Component Catalog marks
    parallel handlers and Cluster Status → Consumers shows a *Parallel* row (executor
    names, in-flight, saturation, transient failures).
  - Bundles push counters to the server, exposed on `/actuator/prometheus`:
    `evento.consumer.executor.{capacity,in.flight,admitted,rejected,completed,failed}` and
    `evento.consumer.async.{in.flight,submit.timeouts,transient.failures}`. **`rejected` is
    the alerting signal** — the executor reporting it is the bottleneck. Meters are removed
    when a node leaves. Bundles with no executor push nothing.

- **Request capacity is now observable and bounded on the request path**, matching what
  parallel consumers already did for the consume path.
  - `RequestTimeoutException` and `TooManyPendingRequestsException` (both
    `com.evento.transport`) replace the generic `IllegalStateException` a bundle used to
    receive for *any* failed request. The distinction is load-bearing: a timeout means the
    caller stopped waiting, **not** that the handler rejected anything — the work may have
    been applied in full. Applications can now report that as indeterminate (504) instead
    of a definite failure (500), and retry only what is safe to retry.
  - `BundleClientConfig.Builder.maxInFlightRequests(n)` (default 2048, `0` disables) refuses
    a request outright once `n` are outstanding, instead of queueing work that will expire
    unsent. The refusal is definite — nothing was transmitted — so it is always retryable.
  - New meters `evento.server.bus.executor.{pool.size,max,active,queue.depth}` and the
    counter `evento.server.bus.executor.saturated`. **`saturated` is the alerting signal**:
    it only moves when the pool is at max *and* the queue is full.
  - Expiries now log at `WARN` (they were `INFO`), and the bundle-side line carries a
    per-payload-type breakdown — `byType={CatalogProductAddCommand=12}` — so the message
    type that is drowning is named rather than merely counted.

### Changed

- **The bus business executor grows before it queues.** A stock `ThreadPoolExecutor` only
  starts a thread beyond `core` once its queue is *full*, so the shipped
  `core=cores×2, max=cores×8, queue=1024` ran on the core size alone until 1024 requests
  were backed up — the configured maximum was unreachable under exactly the load it existed
  for. Because every request carries a client deadline, that backlog did not merely add
  latency: requests expired in the queue and were then served for a caller that had gone,
  so throughput collapsed rather than degrading. `BusBusinessExecutor` inverts the order
  (grow to max, then queue, then `CallerRuns` back onto the event loop), and the queue
  default drops `1024 → 256` because a queue deeper than `deadline ÷ service time` is dead
  work by construction. Observed in production: 20 concurrent writers against an 8-core
  server fell from 16 writes/s to 0.07 writes/s with ~250 timeouts/hour; the same load at 8
  writers sustained 25 writes/s with none.
- **Observers now fan out with a bound.** They always dispatched asynchronously and
  checkpointed on submit, but through an *unbounded* executor — an observer catching up
  from a cold checkpoint submitted its whole backlog as fast as it could fetch it. The
  default is now a bounded executor (32). `ConsumerProcessor.Builder.observerExecutor(Executor)`
  remains as a compatibility overload. **This is the only behaviour change for existing
  bundles.**
- Observer dead-event replay runs inline instead of on the observer executor; replay is
  operator-driven and low-volume, and sharing the executor let a replay storm starve live
  consumption.
- `MessageHandlerInterceptor` documents the thread-affinity guarantee interceptor-managed
  transactions rely on: for one event, `before → handler → after/onException` always run on
  one thread (the executor's task thread under parallel dispatch), so `ThreadLocal`-bound
  transaction managers are unaffected. Implementations must now be thread-safe.

### Fixed

- **Both CBOR codecs ignore unknown properties** (`JacksonCborCodec`,
  `JacksonCborPayloadCodec`), matching `AdminPayloadCodec` and `ObjectMapperUtils`. A peer
  one version ahead previously had its *entire* payload rejected, and the failure was
  near-silent: a bundle would handshake, register, enable and consume normally while all of
  its handler metadata vanished from the dashboard. On the message codec the same skew would
  have broken the handshake itself. This is not a relaxation of the deserialization
  hardening — the gadget-chain defence is `MessageTypeRegistry` / the
  `PolymorphicTypeValidator`, which bound *which types* may be instantiated, and both are
  untouched.

---

## [2.1.0] — 2026-06-06

### Added
- **Confinement check** (`evento-bundle`): registration-time detection of gateway
  call sites that are invisible to static interaction-graph extraction.
  - New `ConfinementScanner`: ASM sweep over every class in the scanned packages
    that is not a registered component; each `CommandGateway.send/sendAndWait` /
    `QueryGateway.query` call site found there is reported (class, method, line,
    kind). Such "gateway leaks" (e.g. an injected helper class issuing commands on
    a handler's behalf) previously under-approximated the extracted emit set
    silently.
  - `AsmInvocationScanner` now also reports gateway calls whose payload is typed
    as the abstract `Command`/`Query` base — the concrete payload type is
    statically unresolvable, so the call is surfaced instead of silently dropped.
  - New `EventoBundle.Builder` flag **`strictConfinement`** (default `false`):
    findings are logged as warnings by default; when set, registration fails with
    `IllegalStateException` on either kind of finding.

### Changed
- `EventoBundle` configures Reflections with an unfiltered `SubTypes` scanner so
  the confinement sweep can enumerate every class in the base package (component
  discovery behavior is unchanged).

---

## [2.0.0] — 2026-05-30

First general-availability release of the v2.0 ground-up rewrite, promoting `2.0.0-rc1` to GA.
Wire-format compatibility with v1 remains **intentionally broken**.

### Added

- **Self-description parity with the (removed) CLI**: bundles publish full discovery metadata at
  startup — component/handler/payload source paths and line numbers, `repositoryUrl` + `linePrefix`
  for clickable source links, and the `@EventoDescription` annotation for human-readable
  descriptions — so the static-analysis publish step is no longer needed.

### Removed

- **`evento-cli`** module deleted — bundle discovery is fully automatic via ASM self-discovery at
  startup; the static-analysis publish / version-bump step is gone.
- **`evento-parser`** module deleted — last consumer of the parser-based `BundleDescription` removed.
- **Deploy-by-upload / autoscaling** surface removed: server no longer accepts JAR uploads, the
  `/spawn` + `/kill` endpoints, `docker-spawn.py`, per-bundle env/VM-options, `autorun`/`deployable`
  flags, and the autoscaling protocol are all gone. Deployment and scaling are owned by the external
  orchestrator (k8s / Nomad); the framework emits performance metrics only.

### Changed

- `TracingAgent` default is now an honest no-op (telemetry/metrics only); wire a custom agent or
  `SentryTracingAgent` for real distributed tracing.
- Public documentation (docs.eventoframework.com) realigned to v2: CLI / deploy-script / autoscaling
  pages removed, consumer-state-store pages rewritten to the five-SPI + JDBC-module model.

### Fixed

- `JdbcConsumerStateStore`: framework-provided `ObjectMapper`; improved optimistic locking on version
  promotion. Docker API-version handling refined.

---

## [2.0.0-rc1] — 2026-05-21

First release candidate of the v2.0 ground-up rewrite. Wire-format compatibility with v1 is
**intentionally broken** — this is a major release.

### Breaking Changes

- `evento-bundle`: v1 transport classes deleted (`EventoSocketConnection`, `EventoServerClient`,
  `ClusterConnection`, `EventoSocketConfig`, `MessageHandler`, `ResponseSender`, `RequestHandler`,
  `EventoResponseSender` — 1 634 LOC removed)
- `evento-server`: `MessageBus.java` (1 099 LOC) deleted; replaced by composable `BusLifecycle`
- Consumer state store: v1 abstract `ConsumerStateStore` class and v1 Postgres/MySQL modules
  removed; replaced by five focused SPIs + JDBC module
- Autoscale protocol removed: `BundleDeployService`, `ClusterNodeIs{Bored,Suffering,Kill}Message`,
  `/spawn` + `/kill` REST endpoints. The external orchestrator (k8s / Nomad) now owns scaling.
- Spring property prefix: `evento.server.bus.v2.` → `evento.server.bus.`
- Gradle module rename: `evento-consumer-state-store-jdbc-v2` → `evento-consumer-state-store-jdbc`

### Added

**Transport layer (`evento-transport-api` + `evento-transport-netty`)**
- Netty 4.1 pipeline: CBOR encoding, transparent chunking (no hard message-size limit), optional TLS
- Sealed `Message` record hierarchy: `Hello`, `Welcome`, `Reject`, `Ping`, `Pong`, `Request`,
  `Response`, `Notification` — compiler-enforced exhaustive dispatch
- `MessageTypeRegistry`: byte-tag whitelist; adding a wire type = extend `permits` + register a tag
- `ConnectionStateMachine`: atomic state transitions; `canSend()` is `true` for `CONNECTED` and `DEGRADED`
- `ExponentialBackoffWithJitter`: base 500 ms, max 30 s, jitter 20%
- `InMemoryTransport`: test double for unit testing without Netty
- `ChunkingEncoder` / `ChunkReassembler`: transparent chunking by stream UUID — no message size limit
- Zero-copy forwarding: `Transport.sendRaw(byte[])` — broker relays without CBOR re-encoding

**Server bus (`evento-server`)**
- `BusLifecycle` orchestrator with explicit `start(port)` / `stop(Duration)` lifecycle
- `ConnectionRegistry`: atomic supersede + token-guarded unregister (prevents post-reconnect routing loss)
- `ClusterRegistry`: `payloadType → Set<NodeAddress>` with RANDOM / FIRST pick strategy
- `CorrelationStore`: bounded shutdown, scheduler-driven expiry
- `ForwardingTable`: `drainByDestination()` preserves originator-side entries for QoS reconnect delivery
- `ForwardingDedupCache`: LRU exactly-once at the broker (50k entries, 5 min TTL)
- `TokenValidator` SPI: `acceptAll()` (default) + `sharedSecret(token)` (constant-time compare)
- `BusEvent` sealed hierarchy replacing v1's four parallel listener lists
- `BusFacade` SPI + `BusLifecycleFacade` adapter unifying server-side consumers
- `CommandBrokerHandler`: restores aggregate/service command interception on top of `BusLifecycle`
- `BrokerEventStore` interface: decouples `CommandBrokerHandler` from JPA `EventStore`
- Split bundle registration: lean `evento:bundle-registration` (bundleVersion + handlerPayloadTypes) +
  rich `evento:bundle-discovery` (handlers + payloadInfo schemas) sent post-enable
- `reconnectBuffer`: buffers in-flight responses for disconnected originators (2-min TTL)

**Bundle client (`evento-bundle`)**
- `BundleClient` façade: Builder, `start/stop`, `request/notify`, handler registration
- `BundleClientState` machine: `INITIAL → CONNECTING → HANDSHAKING → REGISTERING → READY → …`
- `ConnectionSupervisor`: Netty transport + reconnect loop
- `BundleCorrelationTracker`: futures survive disconnect; `failAll()` only on shutdown
- `ProcessedRequestCache`: inbound exactly-once (Claimed / InFlight / Replay)
- `EventoServerAdapter`: implements `EventoServer` over `BundleClient`
- `BundleAdminRequestHandler`: closes dashboard / consumer round-trip on v2 wire

**Consumer engines (`evento-bundle`)**
- `ProjectorEngine`, `SagaEngine`, `ObserverEngine`: compose on `ConsumerProcessor` + SPIs
- `EngineSupervisor`: virtual-thread executor, deadlined `shutdown(Duration)`
- `ConsumerHandle` interface: uniform admin surface for all engines
- `DispatchContext` record: groups tracing + telemetry + interceptor (reduces constructor arity)
- `MessageHandlerInterceptor`: all 24 methods are `default` (ISP fix); implementors override only what they need

**Consumer state SPI (`evento-common`)**
- `ConsumerStateStore`: checkpoint read/commit (optimistic versioning), enabled flag, error history
- `ConsumerLock`: cross-JVM exclusive zone; `LockHandle: AutoCloseable`
- `SagaStateStore`: saga instance lookup by association + insert/update/delete
- `DeadEventQueue`: per-consumer DLQ with retry flag
- `DedupeStore`: observer dedupe with sweep windows
- In-memory impls for all five SPIs (test-friendly, zero setup)

**JDBC state store (`evento-consumer-state-store-jdbc`)**
- Full implementations of all five SPIs for Postgres and MySQL
- Flyway V1 migrations (schema: `evento_v2_consumer_state`, `evento_v2_saga_state`,
  `evento_v2_dead_event`, `evento_v2_dedupe`)
- `JdbcConsumerLock`: Postgres `pg_try_advisory_lock(hashtext(id))` / MySQL `GET_LOCK(id, 0)`
- `JdbcSagaStateStore`: JSON(B) state + flat `associations` column for fast `->> ?` / `JSON_EXTRACT` lookups
- Testcontainers ITs gated on `EVENTO_RUN_JDBC_IT=true`

**Integration test harness**
- `evento-lab`: in-process single-bundle IT harness (`EmbeddedBroker`, `TestEventStoreBundleClient`,
  `CommandAwareEmbeddedBroker`); 31 tests across connectivity, RTT, failure matrix, stress, interceptors
- `evento-lab-microservices`: multi-bundle RECQ microservices scenario (command, query, saga,
  observer, notification, payment saga); 22 tests

### Fixed

- **Fix A**: `onTransportDisconnected` superseded-session race — handlers wiped on bundle reconnect
- **Fix B**: FK violation (`core__handler_return_type_name_fkey`) in `BundleService` orphan-payload
  cleanup; added `HandlerService.isPayloadReferenced()` guard
- **Fix C**: DEGRADED channel dropped in-flight responses — `ConnectionState.canSend()` now
  returns `true` for DEGRADED (TCP socket is alive; Netty backpressure is advisory)
- **Fix D**: TCP disconnect + reconnect lost in-flight responses — `ForwardingTable.drainByDestination()`
  + `reconnectBuffer` + `deliverPendingResponses()` on re-handshake
- `ReflectionUtils.invoke()`: propagates checked exceptions directly instead of wrapping in `RuntimeException`
- `LogTracesMessageHandlerInterceptor`: fixed 4 swapped before/after log messages for Saga/Observer handlers
- `ProjectionReference.invoke()`: fixed silent bug passing `Class<?>` instead of the actual projection instance
- CBOR 20 MB string cap removed from `AdminPayloadCodec` (Jackson 2.15+ default)
- `SerializedQueryResponse` eliminated from transport path — ~40% peak heap reduction for large responses
- `PgDistributedLock`: null-DataSource falls back to JVM-only semaphore (safe for single-JVM ITs)

### Changed

- Toolchain bumped: Java 21 → **25**, Gradle 8.5 → **9.0**, Spring Boot 3.2 → **3.5.5**,
  Jackson 2.15 → **2.18.2** (+ CBOR), Netty **4.1.118.Final**
- `HandlerMetadataBuilder`: extracted from `EventoBundle.start()` into a focused class
- `EventoBundle.Builder`: consumer engines path is now always via `ConsumerEngineConfig`; in-memory
  default via `ConsumerEngineConfig::inMemory`

---

## [1.15.5] — 2025-01-01

Last stable v1.x release. The v1 source history is preserved in the git log.

---

[Unreleased]: https://github.com/EventoFramework/evento-framework/compare/v2.5.2...HEAD
[2.5.2]: https://github.com/EventoFramework/evento-framework/compare/v2.5.1...v2.5.2
[2.5.1]: https://github.com/EventoFramework/evento-framework/compare/v2.5.0...v2.5.1
[2.5.0]: https://github.com/EventoFramework/evento-framework/compare/v2.4.6...v2.5.0
[2.4.6]: https://github.com/EventoFramework/evento-framework/compare/v2.4.5...v2.4.6
[2.4.5]: https://github.com/EventoFramework/evento-framework/compare/v2.4.4...v2.4.5
[2.4.4]: https://github.com/EventoFramework/evento-framework/compare/v2.4.3...v2.4.4
[2.4.3]: https://github.com/EventoFramework/evento-framework/compare/v2.4.2...v2.4.3
[2.4.2]: https://github.com/EventoFramework/evento-framework/compare/v2.4.1...v2.4.2
[2.4.1]: https://github.com/EventoFramework/evento-framework/compare/v2.4.0...v2.4.1
[2.4.0]: https://github.com/EventoFramework/evento-framework/compare/v2.1.0...v2.4.0
[2.1.0]: https://github.com/EventoFramework/evento-framework/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/EventoFramework/evento-framework/compare/v2.0.0-rc1...v2.0.0
[2.0.0-rc1]: https://github.com/EventoFramework/evento-framework/compare/v1.15.5...v2.0.0-rc1
[1.15.5]: https://github.com/EventoFramework/evento-framework/releases/tag/v1.15.5
