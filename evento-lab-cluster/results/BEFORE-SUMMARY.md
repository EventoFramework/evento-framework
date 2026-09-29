# Chaos scenarios on `:before` (evento 2.4.6, `FRAMEWORK_REF=e7b823fe`), 2026-09-29

Every scenario S00..S17 (S15 included) ran once against the per-bundle topology with
`LAB_TAG=before` and `EVENTO_LAB_LABEL=before`. Each has `results/<S>/before/{summary.json,report.md}`.
The runs were one at a time, through `scripts/run-scenario.sh`, on a Windows host with Docker Desktop
(8 CPU, 15.6 GiB). Findings ids (N*, L*) refer to [`../FINDINGS.md`](../FINDINGS.md). Gap ids (G*)
refer to [`.claude/CLUSTER-READINESS.md`](../../.claude/CLUSTER-READINESS.md).

**How to read the verdicts.**

| Verdict | Meaning |
|---|---|
| AS_EXPECTED | The system behaved correctly. |
| KNOWN_DEFECT_REPRODUCED | The documented pre-fix defect showed up. |
| UNEXPECTED | The behaviour was wrong in a way the scenario did not predict. |
| INCONCLUSIVE | The fault did not exercise the question. |

## Stack lifecycle

| # | When | How | G8 hit (lost on first registration, fixed by `repair-catalog.sh`) |
|---|---|---|---|
| 1 | before S00 | `down -v; up` | consumers of `order-query-wildcard`, `order-saga-eu`. The observer's handler rows were also lost, but the repair script did not check them (L14, fixed). |
| 2 | after S10 (cold start) | `down -v; up` | consumers of `order-observer`, `order-query-eu`, `order-saga-eu` |
| 3 | after S04 | `down -v; up` | The S04 damage could not be repaired: 3 passes did not converge (N12). G8 on the new start: consumers of `order-observer`, `order-saga-eu`. |
| 4 | S08 cleanup | `Cluster.recreateStack()` | The repair was skipped because the driver ran WSL `bash` (L16, fixed). Checked by hand: the observer's handlers and the wildcard consumer were missing, and the repair fixed them. |
| 5 | S09 cleanup | `Cluster.recreateStack()` | consumers of `order-observer`, `order-query-eu`, `order-saga-eu` |

G8 hit every one of the 5 fresh starts, 2 to 3 JVMs of 14 each time. `order-saga-eu` was hit in 4 of
the 5 starts and `order-observer` in 4 of the 5. After S13 (server kill and restart) `--check` found
the catalog complete.

## Scenario → verdicts → gaps

| Scenario | Verdicts | Gaps confirmed | Gaps refuted / not observed | Key numbers |
|---|---|---|---|---|
| **S00** baseline, 7×20/min | 6 AS_EXPECTED, 3 UNEXPECTED (commands acked, I2, I5) | G9 (saturation); N3 (timed-out commands executed, saga payments duplicated) | I3, I4 held; the catalog was complete after the repair | Ack 227/319 (71 %); every failure a 30 s timeout. p50 / p99: EU 10.3 s / 17.9 s, AM 25.0–26.6 s / 30.5 s, APAC 30.0–30.1 s / 30.5 s. **5.2 appends/s**; I6 converged in 13.1 s. 83 unacked ops were stored anyway, and 3 payments ran 2×. The I5 mismatch was one AddItem stored after its caller timed out (late append, N3). |
| **S01** cross-cell routing miss | 1 KNOWN_DEFECT, 2 AS_EXPECTED | G1 | - | AMERICA 0/5, `no handler for CreateOrderCommand` in ≤ 89 ms. Recovered 1.2 s after the restart. EUROPA 5/5. |
| **S02** kill a handler replica | 3 AS_EXPECTED | - | intra-server failover works | All 487 ops acked; the 316 sent ≥ 5 s after the kill: p50 166 ms, p99 296 ms. 1 command in flight at the kill, which succeeded. 7.0 appends/s (EU only). I6 3.8 s. |
| **S03** kill `evento-apac` | 2 KNOWN_DEFECT, 7 AS_EXPECTED | G2 (APAC stranded), G4 (3 in-flight calls time out after 30 s) | G8 during the reconnect storm: 0 duplicate-key lines; I7 held | APAC 0/18 acked while its server was down. EU+AM 63/63, p50 193 ms, p99 2.0 s. I6 22.9 s. |
| **S04** server returns / pause 60 s | 1 KNOWN_DEFECT, 3 AS_EXPECTED | G7 (variant B: 10 consumer ids wiped) | variant A (restart) left the catalog intact | Variant B lost `PaymentSaga` of all 7 regions and `OrderProjector` CINA/INDIA/AUSTRALIA_NZ. Afterwards the damage was not repairable (N12), and `order-saga-apac`/`order-query-apac` looped on failing handshakes (N13). |
| **S05** same aggregate from EU and APAC | 3 AS_EXPECTED, 1 KNOWN_DEFECT | G3 (8 `deadlock detected` across servers) | no fork (I3), no loss or duplicate | 112/120 acked; p50 2.3 s, p95 3.4 s, p99 4.8 s. The 8 failures were `Failed to acquire advisory lock RESOURCE:…` (deadlock). |
| **S06** WAN sweep to the ES primary | 6 AS_EXPECTED (invariants only) | G9 | I1..I6 held across the sweep | Appends/s: 7.0 at 0 ms, 7.0 at 50 ms, 4.05 at 150 ms, 1.65 at 300 ms. p50 at 0 / 50 / 150 / 300 ms: EU 34 ms / 0.44 s / 16.9 s / 30.0 s, AM 49 ms / 4.5 s / 30.1 s / 30.2 s, APAC 32 ms / 12.3 s / 30.1 s / 30.2 s. 136 unacked ops stored anyway. |
| **S07** APAC partition 72 s | 2 AS_EXPECTED, 1 KNOWN_DEFECT, 1 UNEXPECTED | G25 (the `*` lanes stall); **N11** (the whole cluster stops appending) | G11 (no duplicate after healing) | EU+AM **0/16** acked during the partition: 10 timeouts, 5 client timeouts, 1 `Failed to acquire advisory lock for key: es-lock`. APAC 12/12 got 504 from the lb. **Zero appends** from 13:24:30 to 13:25:59; the `*` lanes stalled ≥ 50 s. I6 0.2 s after healing. |
| **S08** ES primary killed, `es-pg-am` promoted | 4 AS_EXPECTED, 1 UNEXPECTED | G3 (servers keep the dead lock and primary session); no write resumed | I1, I2, I3 held; no checkpoint above the new head | **0/42** acked in the 100 s after the kill; 40 timeouts, 2 lock failures. Recovery required recreating the stack. |
| **S09** app primary `app-pg-eu` killed, replica promoted | 3 AS_EXPECTED | - | G11 (checkpoints monotonic over 58 samples) | Every lane resumed on the promoted primary; no duplicate side effects. |
| **S10** cold start, 3 iterations | 1 KNOWN_DEFECT | G14 | - | 1/3 iterations failed: `evento-am` exited on `pg_type_typname_nsp_index` in `schema.sql`. 20 schema-race lines, including `deadlock detected` on `evento-apac`. |
| **S11** underscore contexts | 4 KNOWN_DEFECT, 1 INCONCLUSIVE | G15 (SUDXAMERICA and AUSTRALIAXNZ each leak 1 row, I4 fails); N10 | G16 not testable (the pre-fix page lists no `_` context, L15) | The GUI shows 0 regional consumer rows, while `core__consumer` has all 7 (grouped per component, N10). |
| **S12** WATERMARK cost | 1 AS_EXPECTED | - | **G10 not observed** | CPU per 1k global events: DEFAULT 4538 ms, WATERMARK 3881 ms (×0.86). This is weak evidence: G9 saturation capped the global volume at 238–286 events per 60 s phase. |
| **S13** wrong broker (`gateway-europa` via VIP) | 1 AS_EXPECTED, 1 KNOWN_DEFECT | G13 | - | 3/3 acked before `evento-eu` was killed, 0/3 after. 7 `server_instance_mismatch` lines in 60 s. |
| **S14** retry storm (kill 2000 ms) | 2 KNOWN_DEFECT, 1 AS_EXPECTED | G5 (1 key stored twice, create with a gateway id); G4 (24/24 in-flight calls fail only after ≥ 25 s) | no acked command lost | 1 of 24 failed first attempts had been stored. `before-kill150` (the default 150 ms kill) caught none: INCONCLUSIVE (L17). |
| **S15** clock skew −40 s on `order-query-eu` | 1 KNOWN_DEFECT | fetch staleness gate | a clock +40 s ahead is harmless (`before-ahead40`: AS_EXPECTED) | 0/5 AFRICA orders projected in 60 s; the server logged `EventFetchRequest expired: age=40004ms > max=30000ms` on every fetch. |
| **S16** global ALL lane | 7 AS_EXPECTED | trade-off: one lane for N targets stalls EU and AM behind APAC's database | G24 (one lane, one checkpoint); duplicates (0) | Fan-out to 3 DBs in 2.1 s. EU/AM got 1 of 4 prices during the APAC DB outage and converged 2.1 s after healing; 6 replays absorbed. **Lane takeover 7.7 s** (`:after` 5.2 s). The holder did not hop in phase 1: all 15 writes came from `global-eu`. |
| **S17** context routing | 8 AS_EXPECTED | - | cross-context rows (0), `ALL` rows in order views (0) | One JVM runs 2 engines (EUROPA, AFRICA). 16 checkpoints are in the right databases. The `*` lane sees 14/14 orders. I6 3.4 s. |

## Key numbers, `:before` vs `:after`

| Metric | `:before` | `:after` (where measured) |
|---|---|---|
| S00 commands acked, 7×20/min | 227/319 (71 %) | 498/498 (100 %) |
| S00 p50 AM / APAC | 25–27 s / 30 s (timeout-bound) | 0.72 s / 1.12 s |
| S00 appends/s | 5.2 (saturated) | 9.6 |
| Unacked commands stored anyway (S00) | 83 | 0 unacked |
| Lane takeover (S16) | 7.7 s | 5.2 s |
| ALL fan-out to 3 DBs (S16) | 2.1 s | < 1 s (integration probe) |
| Convergence I6 | 0.2–22.9 s (S00 13.1 s, S03 22.9 s, S06 19.4 s) | - |
| Fresh starts that need the catalog repair | 5/5 | 0/1 (integration run) |

## New criticalities (details in FINDINGS.md)

| Id | Criticality |
|---|---|
| N11 (High) | A silent partition of one remote cell stops appends in the whole cluster for as long as it lasts (S07). |
| N12 (High) | On 2.4.6 a stale NodeLeft deletes the consumer rows of a bundle id in **every** cell (S04 B). Removing a repair helper can do the same, so the catalog repair cannot converge. |
| N13 (Medium) | After the 60 s pause of `evento-apac`, two APAC JVMs loop on handshake timeouts (supersede waits 5 s for the old channel). Docker still reports them healthy. |
| L14–L18 | Harness defects found and fixed during the run. |

## Not run / limits

- S15 needs a derived image (`docker/faketime/Dockerfile`, tag `order-query:before-faketime`). The
  compose default image is restored after the run.
- S12's `:before` numbers cannot refute G10 under real volume: the appender saturates first (G9).
- S08 and S09 end with `Cluster.recreateStack()`. Their catalog repair output is in the
  `stack actions` section of each report.
