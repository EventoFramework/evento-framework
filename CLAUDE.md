# Evento Framework — Claude operating notes

Project-level notes that Claude sessions should read first. The two
load-bearing docs live in [`.claude/`](.claude/):

- [`.claude/PLAN.md`](.claude/PLAN.md) — the approved v2.0 rewrite
  plan, 3 PRs (transport / server / bundle). Authoritative for design
  decisions.
- [`.claude/STATUS.md`](.claude/STATUS.md) — current snapshot: what's
  committed, what's left, where to pick up. **Update this at the end
  of every session.**

## Quick orientation

- Active branch: `next` (v2.0 rewrite). `main` is the v1 production
  line; do not push to `main` until v2.0 is RC-ready.
- Toolchain: **JDK 25**, Gradle 9.0, Spring Boot 3.5.5. Use
  `/usr/libexec/java_home -v 25` on macOS.
- Test command:
  ```
  JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
    :evento-transport-api:test \
    :evento-transport-netty:test \
    :evento-server:test --tests 'com.evento.server.bus.v2.*'
  ```
- v2 code lives under `com.evento.transport.*` (new modules) and
  `com.evento.server.bus.v2.*` (inside `evento-server`). The v1
  `MessageBus` is untouched and still the only Spring bean at
  startup; v2 is gated behind `evento.server.bus.v2.enabled=true`.

## Working agreements

- Wire-format compat with v1 is intentionally broken (this is a major
  release). The v2 wire is CBOR + sealed `Message` records, framed
  with a 4-byte length prefix.
- Server is payload-agnostic: it routes by `payloadType: String` and
  carries the body as opaque `byte[]`. Bundles deserialize locally.
- Sealed types + records + closed registries are the OCP idiom — add
  a new wire message by extending `permits` + registering a tag in
  `MessageTypeRegistry`. The compiler enforces exhaustive dispatch.
- Commits follow Conventional Commits with rich bodies (the why, not
  just the what). `git show <hash>` should be enough to recover
  context.
- Tests at the boundary, not the implementation. Disconnect / failure
  paths get real-TCP IT in `BusLifecycleDisconnectIT`.

## When in doubt

Read `STATUS.md` first, then `git log --oneline next ^main --reverse`
to see what landed in order. `PLAN.md` documents the larger plan
behind each commit.
