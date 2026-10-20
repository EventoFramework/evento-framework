# Chaos scenarios on 3.0 (`v3`), 2026-10-20

Every scenario S00..S18 ran against the per-bundle topology with images built from branch
`enterprise/2.6-readiness` (the 3.0 release candidate: optimistic aggregate commands, group commit,
Flyway, presence and admin relay through the database, N4 lane affinity, N15 append deadline, F9, F10),
`LAB_TAG=v3`, `REPLICA=true`, one scenario at a time in the README's order, the stack recreated after
S10, S04 and S09. Windows host, Docker Desktop. Before the lab, the whole Gradle build with the
Testcontainers ITs (PostgreSQL 16/17, MySQL 8.4, the server image) passed: 1088 tests, then 1091 after
the fix below.

## Result

| Scenario | `v3` | Notes |
|---|---|---|
| S00 baseline | 9 AS_EXPECTED | 490/490 acknowledged, 0 lost, 0 duplicates; p50 481 ms, p99 1301 ms over the 7 regions |
| S01 routing miss | 2 AS_EXPECTED, 1 KNOWN_DEFECT | Same as every earlier run (no handler in the cell, by design of the scenario) |
| S02 handler replica killed | 3 AS_EXPECTED | |
| S03 server killed | 11 AS_EXPECTED, 1 INCONCLUSIVE | Inconclusive: no gateway command in flight at the kill (timing, as in `after`, `after-versioning`) |
| **S04 server returns** | **3 AS_EXPECTED, 1 KNOWN_DEFECT** | **Regression, fixed** (below). After the fix: 4 runs, 4 × 4 AS_EXPECTED (`v3fix2-1..4`) |
| S05 contention | 4 AS_EXPECTED | |
| S06 WAN sweep | 6 AS_EXPECTED | |
| S07 partition | 6 AS_EXPECTED | Again after the fix: 6 |
| S08 ES primary failover | 5 AS_EXPECTED | |
| S09 app primary failover | 3 AS_EXPECTED | |
| S10 cold start | 1 AS_EXPECTED | |
| S11 underscore contexts | 5 AS_EXPECTED | |
| S12 watermark cost | 1 INCONCLUSIVE | CPU under load not above the idle rate, as in every `after*` run |
| S13 wrong broker | 4 AS_EXPECTED, 1 KNOWN_DEFECT | Same as every `after*` run (the legacy single-id guard) |
| S14 retry storm | 2 AS_EXPECTED, 2 INCONCLUSIVE | Kill at 150, 500, 1000 ms (`v3`, `v3-kill500`, `v3-kill1000`): 24 first attempts failed each time, none had been stored before its retry; at 3000 ms (`v3-kill3000`) no command was in flight any more. 0 keys stored twice, 0 acknowledged commands missing in every run. With 3.0 commands the stored-but-unanswered window is too short to hit by chance; S18 hits it on purpose (AS_EXPECTED) |
| S15 clock skew (libfaketime) | 1 AS_EXPECTED | |
| S16 global fan-out | 7 AS_EXPECTED | |
| S17 context routing | 8 AS_EXPECTED | |
| S18 G5 residual window | 3 AS_EXPECTED | |

## The S04 regression

Variant B pauses `evento-apac` for 60 s. During the pause the APAC bundles fail over to the other cells
and keep trying to fail back to APAC; those attempts time out and their sockets close. When APAC
resumed, it read those Hellos. The Q4 fix of the same day (`d9df035c`) registered such a Hello and
unregistered it at once. Its NodeJoined claimed the instance in the presence table, taking it from the
server where it was live. APAC's own old sessions of those instances then left, found the rows theirs,
and deleted the instances' consumer rows (`order-query_OrderProjector_1_INDIA`, `..._CINA`...). Three
runs out of four lost consumers; every `after*` run before Q4 had been clean.

A first fix (a session closed before its first message leaves as `handshake_aborted` and keeps the
rows) still failed (`v3fix-1`, `v3dbg-1`): most of those Hellos are handled before the close is
noticed, so the claim, not the departure, was the problem. The fix in `2d688350` moves the claim to
`BusEvent.NodeEstablished` (the session's first message after its Hello) and makes a Hello from a
closed connection touch nothing. Four S04 runs clean, S00, S02, S03, S07, S13 as before.

Raw logs of the runs were kept out of the repository.

## Mixed versions: 2.5.2 and 3.0

Images built against tag `v2.5.2` (`FRAMEWORK_REF=v2.5.2 LAB_TAG=v252`), mixed with `v3`:

| Run | Servers | Bundles | Result |
|---|---|---|---|
| M1 servers first | 3.0 | 2.5.2 | S00 9, S17 8, S03 12 AS_EXPECTED |
| M2 bundles first (wrong order) | 2.5.2 | 3.0 | S00 9 AS_EXPECTED |
| M3-0 rolling upgrade, start | 2.5.2 | 2.5.2 | S00 9 AS_EXPECTED (database created by 2.5.2) |
| M3-1 one server upgraded | eu 3.0, am/apac 2.5.2 | 2.5.2 | S00 9 AS_EXPECTED; the 3.0 server baselined the 2.5.2 database and ran V1-V7 online while the others served |
| M3-2 all servers upgraded | 3.0 | 2.5.2 | S00 9 AS_EXPECTED |
| M3-3 bundles upgraded one by one | 3.0 | 3.0 | S00 9, S03 11 AS_EXPECTED + 1 INCONCLUSIVE (same as pure 3.0) |

Each server and each of the 22 bundle containers was replaced in the running stack
(`docker compose up --no-deps` with an image override), as a rolling upgrade does.

