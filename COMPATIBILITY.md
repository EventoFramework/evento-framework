# Evento Framework — Compatibility Policy

What a new Evento version may change, which versions work together, in which order to upgrade, and
how long a version is supported. It applies from **2.6.0**. Each release's upgrade guide
([docs](https://docs.eventoframework.com/evento-server/evento-server-cluster)) lists the details of
that release; this page is the rule they follow.

## 1. Version numbers

Evento uses semantic versioning, `MAJOR.MINOR.PATCH`, for the server, the web console and the
client libraries together: one version number for everything released at once.

| Release | May contain | Must not contain |
|---|---|---|
| **Patch** (2.6.0 → 2.6.1) | Bug and security fixes | New features, new configuration, schema changes, changed defaults |
| **Minor** (2.6 → 2.7) | New features, new optional configuration, additive wire messages and fields, additive schema migrations, deprecations | Removals, incompatible wire changes, schema changes an older server cannot run on, changed defaults that alter behaviour or security |
| **Major** (2.x → 3.0) | Removals of what was deprecated, incompatible wire or API changes, changed defaults (such as the `prod` security profile in 3.0) | — |

## 2. What stays compatible within a major version

- **Wire protocol.** The bus protocol version (`HandshakeProtocol.PROTOCOL_VERSION`) changes only in
  a major version. A minor adds messages, fields or behaviour behind a **capability** both sides
  announce in the handshake (`stream:1`, `revision:1`, ...); a peer that does not announce it gets
  the previous behaviour.
- **Client libraries** (`evento-bundle`, `evento-common`, the transports, the JDBC consumer store,
  the Spring Boot starter, `evento-opentelemetry`, `evento-test`). Public API is source and binary
  compatible within a major: classes and methods are deprecated first (`@Deprecated`, with the
  replacement in the Javadoc) and removed only in the next major. Packages named `internal` may
  change in a minor.
- **Configuration.** Properties and environment variables keep their meaning within a major. A
  replaced property keeps working, with a warning at boot, until the next major.
- **Event store and catalog schema.** Changes are Flyway migrations (`evento_es_schema_history`),
  forward only and, within a major, **additive**: new tables, nullable columns, indexes built
  `CONCURRENTLY`. A server of the previous minor keeps working on a migrated database, which is what
  makes a rolling upgrade and a rollback of the server binaries possible.
- **Stored events and application payloads.** Events are never rewritten. A payload class that
  changes shape gets a new `@Revision` and an upcaster for the old ones; consumers and aggregates
  always see the current shape.

## 3. Which versions work together

| | Bundles N | Bundles N-1 | Bundles N+1 |
|---|---|---|---|
| **Servers N** | Supported | Supported | Not supported |

N is a minor version (2.6, 2.7, ...); patch versions of the same minor always mix.

- **Servers of two versions in one cluster** (N and N-1, sharing the database) are supported only
  for the length of a rolling upgrade. A feature that needs every server (the aggregate fence and
  idempotency keys in 2.6, for example) takes full effect once the last server is upgraded; the
  upgrade guide says which.
- **Bundles newer than their server** often work, since new behaviour is negotiated, but this is not
  tested and not supported: a bundle feature may need the server of its version (a `@Revision`
  payload needs a 2.6 server).

## 4. Upgrade order

1. Read the upgrade guide of every minor between the current and the target version.
2. **Servers first, one at a time**: each migrates the schema at boot if needed (servers that start
   together still migrate once), then rejoins the cluster.
3. **Then the bundles**, at the pace of each team: bundles N-1 keep working with servers N.

Skipping minors is supported for servers (the migrations of every skipped minor run in order) as
long as no server of a version older than N-1 is running against the database during the upgrade.

**Rolling back** a server to the previous minor is supported: the schema stays migrated (it is
additive). Rolling back across a major is not.

## 5. Platforms

| Component | Supported |
|---|---|
| Client libraries | Java 21 or later |
| Server | Java 25.0.3 or later (the container image ships its JVM) |
| Spring Boot starter | Spring Boot 3.5 and 4.x |
| Database | PostgreSQL 12 or later (the server refuses older ones; tested on 16 and 17) |

A platform version leaves the supported list only in a minor release, announced in the changelog of
the previous minor.

## 6. Deprecation

A feature, API or property to be removed is first **deprecated in a minor release**: marked
`@Deprecated` or documented as deprecated, logged as a warning when used (`event=..._deprecated`),
listed under *Deprecated* in the changelog with what replaces it. It is removed **no earlier than the
next major**. Example: APES (`evento.es.mode=APES`) is deprecated in 2.6, with a way to move a store to
CPES, and may be removed in 3.0.

## 7. Support

- **Patches** (bug fixes) go to the **latest minor** of the current major.
- **Security fixes** go to the latest minor of the current major line ([SECURITY.md](SECURITY.md)).
- **Longer support** for an older minor, or help with an upgrade, can be arranged by agreement
  ([SUPPORT.md](SUPPORT.md)).
