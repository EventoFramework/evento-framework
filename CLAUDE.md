# Evento Framework — Claude operating notes

Project-level notes that Claude sessions should read first. Three
load-bearing docs live in [`.claude/`](.claude/):

- [`.claude/ARCHITECTURE.md`](.claude/ARCHITECTURE.md) — **start here**: module map,
  wire protocol, key classes, design decisions, test strategy, conventions. The
  single authoritative reference for agents and developers.
- [`.claude/STATUS.md`](.claude/STATUS.md) — session-level delta: what changed,
  what's in flight, what's next. **Update this at the end of every session.**
- [`.claude/PLAN.md`](.claude/PLAN.md) — original v2.0 rewrite plan (all 3 PRs shipped;
  useful for historical rationale and design intent).

## Quick orientation

- Active branch: **`main`** (latest release tagged: `v2.5.0`).
- Toolchain: **JDK 25.0.3 or later**, Gradle 9.7.1, Spring Boot 4.1.1. Use
  `/usr/libexec/java_home -v 25` on macOS; on the Windows dev host
  `C:/Users/Gaborando/.jdks/jdk-25.0.4.1+1`. Never 25.0.1/25.0.2: JDK-8370887 can stall
  virtual-thread timed waits for ~20 s (bundle handlers, consumer engines, flaky ITs).
- Full test command:
  ```
  JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
    :evento-transport-api:test \
    :evento-transport-netty:test \
    :evento-server:test --tests 'com.evento.server.bus.*' \
    :evento-common:test \
    :evento-bundle:test \
    :evento-consumer-state-store:evento-consumer-state-store-jdbc:test \
    :evento-lab:test \
    :evento-lab-microservices:evento-lab-ms-it:test
  ```
  The Testcontainers/Postgres ITs (e.g. `MultiServerCatalogIT`) are skipped unless
  `EVENTO_RUN_JDBC_IT=true` is set; prefix the command with it when touching the catalog.

## Working agreements

- Wire format: CBOR + sealed `Message` records, 4-byte length-prefixed frames,
  transparent chunking (no message size limit). Wire compat with v1 is intentionally broken.
- Server is payload-agnostic: routes by `payloadType: String`, carries body as opaque `byte[]`.
- Sealed types + records + closed registries are the OCP idiom — adding a wire
  message = extend `permits` + register a byte tag in `MessageTypeRegistry`.
- Commits follow Conventional Commits with rich bodies (the why, not the what).
- Tests at the boundary: integration tests use real TCP transports, not mocks.

## When in doubt

Read `ARCHITECTURE.md` first. Then `STATUS.md` for the current session delta.
`git log --oneline -20` shows recent work; `git show <hash>` recovers full context.
