# Evento vs Axon Framework 5: command write throughput

B1 of `.claude/ENTERPRISE-2.6.md`: how many aggregate commands per second one Evento cell
sustains, next to Axon Framework 5.4 on the same machine, with the same workload and the same
CPUs. A standalone Gradle build, outside the main build and its CI.

## What is compared

| System | What runs | Event store |
|---|---|---|
| `evento-CPES` | a caller bundle → evento-server → the aggregate bundle (three JVMs) | PostgreSQL, CPES (global append lock, gapless commit order) |
| `evento-APES` | the same | PostgreSQL, APES (no global lock, Snowflake ids, consumers read behind a delay) |
| `axon5-jpa` | Axon Framework 5.4 open source (Apache-2.0), caller and entity in one JVM: the open-source framework has no distributed command bus | PostgreSQL through `AggregateBasedJpaEventStorageEngine` |
| `axon5-jpa-pool64` | the same with 64 database connections instead of the Spring Boot default of 10 | the same |
| `axon5-axonserver` | Axon Framework 5.4 + Axoniq Framework connector, caller app → Axon Server → entity app | Axon Server 2026.1.7, standalone, DCB context |

**Axon Server results are never published.** The Axoniq Terms of Service, which cover Axon Server
and the Axoniq Framework connector, say: *"Results of any benchmark tests on the Services run by
You may not be disclosed outside of Your organization without the prior written consent of
AXONIQ."* The `axonserver` runs write to `results-private/`, which is git-ignored; keep them out
of the repository, the documentation, the website and the thesis unless Axoniq consents in
writing. The core of Axon Framework is Apache-2.0 and outside those Terms ("these Terms do not
apply"), so the `axon5-jpa` results can be published.

## Workload

One domain, written idiomatically for each framework: an order created by `CreateOrder`
(`OrderCreated`) and grown by `AddItem` (`ItemAdded`). Every command appends exactly one event.
Framework defaults everywhere: no snapshots, no caches configured, default pools.

- **create**: every command creates a new order.
- **spread**: 1000 orders are created first (not measured), then each command adds an item to
  one at random.
- **hot**: every command adds an item to the same order.

Closed loop: N callers, each sends its next command when the previous one is answered, so N
commands are in flight (N = 1, 8, 32, 128). Per level: 20 s warm-up, 60 s measured, fresh
aggregates; latency is recorded for commands that start and end in the measured window
(HdrHistogram), throughput counts the commands answered successfully in it. A refused command
(a concurrency conflict, a timeout) is counted by exception type and never as throughput.

How each system handles two commands on the same aggregate at the same time is part of what is
measured: Evento queues them on the aggregate lock (and, since C1, the database fences the
append); Axon Framework 5 runs them optimistically and refuses the loser
(`AppendEventsTransactionRejectedException` with JPA, `CommandExecutionException` from Axon
Server). Axon applications usually retry such refusals; the benchmark does not, for any system.

## Fairness

- Same machine, same Docker, one system at a time on a fresh stack (volumes removed).
- Infrastructure on CPUs 0–3 (PostgreSQL and evento-server, or Axon Server), applications on
  CPUs 4–7 (handlers and load generators), memory capped per container; every JVM is Temurin 25
  with `-XX:MaxRAMPercentage=60`.
- Durable writes on both sides: PostgreSQL 16 defaults (`fsync`, `synchronous_commit`), Axon
  Server defaults.
- Evento runs the working tree of this repository (the server image is built from it).

## Running it

```
scripts/build.sh                          # jars and images
scripts/run.sh evento-cpes spread         # one system, some scenarios
scripts/run-all.sh                        # the whole matrix (about 100 minutes)
python scripts/report.py results/results.jsonl > results/REPORT.md
```

`BENCH_CONCURRENCY`, `BENCH_WARMUP_S`, `BENCH_DURATION_S`, `BENCH_AGGREGATES` override the
defaults (see `docker/compose.yaml`).

## Results

See [`results/REPORT.md`](results/REPORT.md) (in Italian: machine, date and commit in its header,
units and how to read the tables first).

## B2: high availability across two AZs

The same workload on a highly available deployment, simulated on one machine
(`docker/compose.ha.yaml`): PostgreSQL primary in AZ a, a **synchronous** standby in AZ b (every
commit waits for it), one server (or Axon instance) with its bundle and load in each AZ, and
toxiproxy adding a round trip between the AZs (replication stream and the AZ b database
connections). The two loads share the spread and hot aggregates, so commands for one aggregate
arrive through both servers.

```
scripts/run-ha.sh evento-cpes 2           # one system at 2 ms between AZs, all scenarios
python scripts/report-ha.py results/results-ha.jsonl > results/REPORT-HA.md
```

Results: [`results/REPORT-HA.md`](results/REPORT-HA.md) (Italian; throughput split per AZ).

What it shows: with synchronous replication PostgreSQL keeps a transaction's locks until the
standby confirms, so CPES holds its global `es-lock` for the local fsync plus the round trip
between AZs on every batch, while APES commits in parallel. APES is 2-3x faster at low
concurrency or 10 ms between AZs (create, 8 in flight: 286 vs 91 cmd/s) and even at 0 ms with
128 in flight, where CPES keeps the better p99 on spread. B2 measures commands only: APES with
more than one server remains unsafe for consumers (they read behind a 69 ms window and skip a
later commit; several clocks; MAC-derived node ids), so CPES stays the default and the cluster
mode, and APES is deprecated since 2.6. On a hot aggregate both modes now serve the two AZs evenly (29/29 cmd/s at 0 ms); Axon
Framework (JPA) collapses on shared aggregates (spread ~300 cmd/s, hot <= 7 with refusals).
Caveats: one machine, so it measures what replication and AZ latency cost each design, not
what more hardware gives; the standby writes to tmpfs (its own fsync, ~1 ms on cloud SSD, is not
modelled); Axon at 128 in flight on spread still saw "Entity was missing" errors in AZ b, whose
load ran ahead of the AZ a load that creates the shared aggregates (a harness artefact, counted
among its refusals). B2 found and led to the fix of a lock starvation between servers (see the
changelog).
