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

- Active branch: **`main`** (`next` merged; `v2.0.0` released / tagged).
- Toolchain: **JDK 25**, Gradle 9.3, Spring Boot 3.5.5. Use
  `/usr/libexec/java_home -v 25` on macOS.
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
