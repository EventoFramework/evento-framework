# Chaos scenarios on `:after` (cluster fixes, branch `feat/evento-lab-cluster`), 2026-09-29

Every scenario S00..S17 (S15 included) ran against the per-bundle topology with `LAB_TAG=after` and
`EVENTO_LAB_LABEL=after`. Each has `results/<S>/after/{summary.json,report.md}`. The runs went one at a
time through `scripts/run-scenario.sh`, on a Windows host with Docker Desktop (8 CPU, 15.6 GiB). The
side-by-side view with `:before` is in [`COMPARISON.md`](COMPARISON.md). Findings ids (N\*, L\*) refer to
[`../FINDINGS.md`](../FINDINGS.md), and gap ids (G\*) to
[`.claude/CLUSTER-READINESS.md`](../../.claude/CLUSTER-READINESS.md).

Verdicts: AS_EXPECTED means the system behaved correctly. KNOWN_DEFECT_REPRODUCED means the documented
defect still shows. UNEXPECTED means the behaviour was wrong in a way the scenario did not predict.
INCONCLUSIVE means the fault did not exercise the question.

## Re-run with idempotency keys (G5) and fail-back (N16): `after-g5n16`, 2026-09-29

After G5 and N16 (and their review fixes) were merged, the images were rebuilt from the working tree
(`LAB_TAG=after`) and eight scenarios ran again, each on a fresh stack (`down -v` + `up`), one at a
time, with `EVENTO_LAB_LABEL=after-g5n16`: the two that measure the new code (S03 and S13 for fail-back,
S14 plus S03 and S07 for exactly-once) and regressions (S00, S02, S05, S16; S02 and S05 exercise the lock
paths now that service commands claim their key with a row instead of taking a lock). The harness was not
changed in this pass. The S03/S07/S13/S14 harness had changed since the `:before` runs (fail-back wait,
exactly-once observations, the gateway passing `X-Idempotency-Key`), so those four were also re-run on
`:before` (2.4.6 rebuilt with the current lab source) as `before-g5n16`. Everything else in this file below
this section is the earlier `:after` run, now called "after (pre-G5/N16)" in
[`COMPARISON.md`](COMPARISON.md).

| Scenario | Verdicts | Key numbers (`after-g5n16`) | after (pre-G5/N16) |
|---|---|---|---|
| **S00** baseline 7×20/min | 9 AS_EXPECTED | **482/482** acked. p50/p99: EUROPA 176/449 ms, AFRICA 180/434 ms, AMERICA 834/1586 ms, SUD_AMERICA 879/1556 ms, CINA 1472/2673 ms, INDIA 1359/2486 ms, AUSTRALIA_NZ 1472/2521 ms. 9.02 appends/s, I6 3.5 s, 62/62 payments, 0 unacked stored, 0 duplicates. All 544 events carry a key (62 derived by the saga). | 492/492; APAC p50 1156–1178 ms, AM 733–761 ms; 9.35 appends/s |
| **S02** kill a handler replica | 3 AS_EXPECTED | 490 ops acked; after the kill 315/315, p50 174 ms, p99 274 ms; 2 in flight, 0 failed | 486/486; 176/283 ms |
| **S03** kill `evento-apac` | **12 AS_EXPECTED** | First APAC ack **10.9 s** after the kill; APAC after the kill 46/46 (p50 449 ms, p99 686 ms); EU+AM 57/57. 8 APAC JVMs failed over to `evento-eu`; **all 8 failed back without a restart**, the last 55.7 s after `evento-apac` was reported ready (8 `failback_completed`, 0 failed, 0 suspended). Bundle side: 3 `BrokerChangedException`. **0 events stored twice, 0 payments executed twice.** I6 0.38 s. | 44/44, first ack 10.3 s; no fail-back (N16); `after-run1`: 1 payment twice (N14) |
| **S05** same order from EU and APAC | 4 AS_EXPECTED | 120/120; 0 deadlocks; p50 1159 ms, p99 3.0 s (EUROPA p50 212 ms, CINA 1317 ms); at most 1 advisory-lock waiter | 120/120; p50 933 ms (EUROPA 186, CINA 1070) |
| **S07** APAC partition 62 s | **6 AS_EXPECTED** | EU+AM **44/44** during the partition (p50 666 ms, p99 1.1 s); APAC 0/12 (lb 504, expected); `*` lanes stalled 0 s; I6 0.2 s. **0 events stored twice, 0 payments executed twice.** The residual window (a payment executed while its event could not be stored) was not hit in this run. | 41/41; 2 payments twice, 1 event twice (N14) |
| **S13** wrong broker / identity | 4 AS_EXPECTED, 1 KNOWN_DEFECT (A, legacy guard by contract) | C refused (40 `cluster_name_mismatch`); B 3/3 with the guard. A: 7 EU JVMs failed over to `evento-am` and **all 7 failed back** without a restart, 53.3 s after `evento-eu` was ready; the safety-net restore found nothing (`A.bundlesRestartedIntoPlace` empty). | same C/B/A; 7 EU JVMs still on `evento-am` (N16) |
| **S14** retry storm, kill 3000 ms | **4 AS_EXPECTED** | 18/24 first attempts failed, all `broker_changed`, 0 after ≥ 25 s. 4 of 18 had been stored before the retry; **0 keys stored twice**; **4/4 of those retries answered 2xx** with the stored event (`event=idempotent_replay stage=before_lock` ×4 on `evento-eu`: 2 creates, 2 add-items). | kill 2000 ms: 13/24 failed, 9 stored, **8 keys twice** |
| S14 kill 2000 ms (`after-g5n16-kill2000`) | 2 AS_EXPECTED, 2 INCONCLUSIVE | 22/24 failed `broker_changed`, none had been stored: the kill came before any probe reached the append (below), so G5 was not exercised | – |
| S14 kill 150 ms (`after-g5n16-kill150`) | 2 AS_EXPECTED, 2 INCONCLUSIVE | 24/24 `broker_changed`, none stored; every retry stored once | same (INCONCLUSIVE) |
| **S16** global ALL lane | 7 AS_EXPECTED | Fan-out to 3 DBs **8.1 s**; writers split `global-apac` 9 / `global-eu` 6 (N4 unchanged); EU/AM 1 of 4 prices during the APAC DB outage; converged 16.3 s after healing, 8 replays absorbed; **lane takeover 4.4 s**. The `SetPriceCommand` service path (key claim rows) left 0 claims behind. | fan-out 10.2 s, takeover 7.3 s, convergence 0.07 s |

**Why S14 moved from 2000 to 3000 ms.** On a fresh stack the first burst of 24 keyed CINA commands was
slower than on the pre-G5 images: in the 2000 ms run only 2 had been appended when `evento-apac` died (event
timestamps), against 11 acked + 9 stored before. A controlled burst on a warm stack afterwards (24
concurrent CINA creates through the lb, twice each way) showed the steady cost is small: keyed 1.46–3.34 s
against unkeyed 1.17–3.21 s per command, and 3.7 s against 3.5 s for the burst; serial creates 1.47–1.77 s
keyed against 1.16–1.48 s unkeyed (one extra read of the key on the ES primary, 150 ms RTT away). The very
first burst after the stack had settled was slower (keyed: first ack 2.1 s, burst 7.0 s; the unkeyed burst
right after it: 1.2 s and 3.7 s), which matches the 2000 ms run on a fresh stack.
`S14_KILL_DELAY_MS` is the documented knob for exactly this (L17), so the main run uses 3000 ms; the
2000 ms run is kept as evidence.

**Latency cost of the keys (S00, S05).** Every lab command now carries a key. Remote cells pay about one
more round trip to the ES primary: S00 p50 AM 834–879 ms against 733–761 ms, APAC 1359–1472 ms against
1156–1178 ms; S05 CINA 1317 ms against 1070 ms. EU, next to the primary, is unchanged (176–180 ms against
131–265 ms). Appends/s at the same offered load: 9.02 against 9.35 (one run each).

**Log verification** (every run, `docker logs` of the 3 servers and 21 bundle JVMs after the scenario):
- Fail-back events only where a server returned: S03 8 × `failback_probe_healthy` / `failback_started` /
  `failback_drained` / `failback_completed` (APAC); S13 7 × each (EU). **0** `failback_failed`, **0**
  `failback_suspended`, **0** `failback_reactive` in every run: the gateways came home 7–11 s after the
  last handler (S03: `order-*-apac` switched 38–44 s after `evento-apac` had started, the three gateways 51–55 s),
  so no caller ever met `no handler for` (0 on every server in every run). Each switch took 0.50–0.68 s,
  of which the drain 0.26 s.
- `event=idempotent_replay`: only S14 (4, all `stage=before_lock`). **0** `idempotent_duplicate_execution`,
  `idempotency_key_mismatch`, `idempotency_key_in_progress`, `idempotency_key_ignored`,
  `idempotency_claim_lost`, `service_event_stored_after_deadline` in every run; `es__idempotency_claims`
  empty at the end of every run; the index was built at boot (`es_events_idempotency_index_built`).
- No leaked probe sessions: after every run the established connections on each server's `:3030` equal the
  bundles attached to it (EU 8, AM 5, APAC 8 at steady state; S03: `evento-apac` 8 after 93 closed probe
  connections), and `handshake_accepted` counts only real sessions (probes never send `Hello`).
- Lock pool: **0** `lock_acquire_timeout`, **0** `Failed to acquire advisory lock` on `:after`. The only pool
  errors are S07's `evento-apac` data pool (`HikariPool-1 ... total=0`, 41 lines) and 2 `LockLostException`
  while its ES connection was partitioned away, as designed.

## How the run went (read this before the numbers)

The first pass looked plausible, but most of it was measured on the wrong topology. Four harness
defects came out of it. They are fixed in `d2565cce` plus the `replica-entrypoint.sh` change, and the
affected scenarios were re-run on fresh stacks:

1. **No failback (N16, L22).** After S03 killed `evento-apac`, the APAC bundles failed over to
   `evento-eu` (G2 working) and stayed there after `evento-apac` came back. Every later scenario
   then ran with APAC's handlers, consumers and gateways on EU's server. S05 is the clearest case:
   its "cross-server" contention was measured inside one server (p50 0.49 s against 0.93 s in the
   clean run). Every scenario now starts by putting misplaced JVMs back on their own cell's server
   (`placementRestoredAtStart` in each report).
2. **lb misrouting (L23).** Two scenario runs overlapped once: a stopped Gradle driver kept going in
   the background. In the overlap, S07's network heal failed on the lb ("address already in use"),
   and afterwards the lb served **every** `X-Region` from `gateway-cina` with 200s. Events of all
   regions went into context CINA with no error anywhere. Every scenario now checks each region's
   `/whoami` at start and recreates the lb if one is wrong (`lbRoutingAtStart`).
3. **S08 could never resume writes (L24).** The ES replicas inherit the primary's
   `synchronous_standby_names = FIRST 1 (es_pg_am)` through `pg_basebackup`, so a promoted
   `es-pg-am` waits forever for itself as sync standby. The `:before` result "no write resumed
   (G3)" came from this lab defect, not from G3. The fix is in `docker/pg/replica-entrypoint.sh`;
   S08 was re-run on both image sets.
4. **Three scenarios could not show a fix (L19, L20, L21).** S12's CPU ratio was noise, S07 read the
   idle ALL lane as stalled, and S13 only ran the legacy single-id guard. S03's in-flight check was
   also widened to the bundle-side calls. All five changed scenarios (S03, S07, S08, S12, S13) were
   re-run on `:before` too, so each pair uses the same harness.

The contaminated runs were deleted. The runs kept for their evidence are `S03/after-run1` (N14, clean
placement), `S13/after-n16-evidence` (the first sighting of N16), `S07/after-run1` and
`S12/after-run1` (the earlier harness), and `S08/{before,after}-l24` (L24).

Fresh stacks: 6 on `:after` (plus the integration run). Every one had a complete catalog at the first
`repair-catalog.sh --check`, so G8 did not recur. The 2 fresh `:before` starts of this session both
lost consumer rows (`order-observer`, `order-query-eu`, `order-saga-eu` in the S08 recreate), which
makes G8 7 out of 7 on `:before`.

## Scenario → verdicts → gaps

| Scenario | Verdicts | Gaps confirmed (still open) | Gaps refuted / fixed | Key numbers |
|---|---|---|---|---|
| **S00** baseline 7×20/min | 9 AS_EXPECTED | - | G9, N3 (0 unacked stored), G8 (I7 complete) | **492/492 acked (100 %)**. p50/p99 EU: EUROPA 265/611 ms, AFRICA 131/449 ms; AM: AMERICA 761/1202 ms, SUD_AMERICA 733/1390 ms; APAC: CINA 1168/1958, INDIA 1178/1927, AUSTRALIA_NZ 1156/1980 ms. **9.35 appends/s**, 8.2 acked cmd/s. I6 converged in 3.5 s. 72/72 payments. |
| **S01** cross-cell routing miss | 1 KNOWN_DEFECT, 2 AS_EXPECTED | **G1** | - | AMERICA 0/5, `no handler for CreateOrderCommand` in ≤ 14 ms; back 0.88 s after restart; EUROPA 5/5 |
| **S02** kill a handler replica | 3 AS_EXPECTED | - | intra-server failover | 486 ops all acked. After the kill, 313/313 with p50 176 ms, p99 283 ms. 2 in flight at the kill, 0 failed. I6 3.7 s. |
| **S03** kill `evento-apac` | 8 AS_EXPECTED, 1 INCONCLUSIVE | G5 via N14 (in `after-run1`) | **G2, G4, G8** | First APAC ack **10.3 s** after the kill. APAC after the kill 44/44, p50 448 ms, p99 570 ms; EU+AM 62/62, p50 192 ms, p99 1.0 s. I6 0.36 s. `after-run1`: a saga's `ProcessPaymentCommand` got `BrokerChangedException` and was retried, so the payment ran twice (N14). |
| **S04** server returns / paused 60 s | 4 AS_EXPECTED | - | **G7, N12, N13** | Probes during the outage 3/3 in both variants (`:before` 0/3). 0 `Unregister` calls, catalog intact after the pause, no handshake loop afterwards. |
| **S05** same order from EU and APAC | 4 AS_EXPECTED | - | **G3** (0 deadlocks), G6 fork not seen | 120/120 acked; p50 933 ms, p99 2.8 s (EUROPA p50 186 ms, CINA p50 1070 ms); at most 1 advisory-lock waiter |
| **S06** WAN sweep 0/50/150/300 ms | 6 AS_EXPECTED | N5/N9 (global append serialisation), **N15** | G9 up to 150 ms | Appends/s 7.0 / 7.0 / 7.0 / 6.55. p50 EU 23 ms / 75 ms / 1.7 s / 13.0 s; AM 22 ms / 0.43 s / 2.9 s / 16.5 s; APAC 29 ms / 0.43 s / 2.7 s / 22.8 s. Failures 0/0/0/31 (all APAC at 300 ms), and 31 unacked commands were stored anyway (N15). I6 0.21 s. |
| **S07** APAC partition 62 s | 3 AS_EXPECTED, 1 KNOWN_DEFECT (I2) | G5 via **N14** | **N11**, G25 (no lane stalled), G11 | EU+AM **41/41** acked during the partition, p50 585 ms, p99 866 ms (`:before` 0/16). APAC 0/12 (lb 504). `*` lanes stalled ≤ 5 s. I6 0.2 s after healing. 2 payments ran twice, and 1 `PaymentProcessedEvent` was stored twice (N14). |
| **S08** ES primary killed, `es-pg-am` promoted | 5 AS_EXPECTED | N17 (slower resume) | G3, I1–I3, no checkpoint above the head | Writes resumed **15.1 s** after the kill (`:before` 7.6 s). 103/106 acked after the kill, p50 874 ms. 0 lost, 0 duplicates, 0 forks. |
| **S09** app primary killed, replica promoted | 3 AS_EXPECTED | - | G11 (59 samples monotonic) | every lane resumed; 0 replays needed |
| **S10** cold start ×3 | 1 AS_EXPECTED | - | **G14** | 0/3 iterations failed, 0 schema-race lines (`:before` 1/3, 20) |
| **S11** underscore contexts | 5 AS_EXPECTED | - | **G15, G16, N10** | 0 leaked rows. The page shows the `_` contexts correctly, and 7/7 regional consumer rows. |
| **S12** WATERMARK cost | 1 INCONCLUSIVE | - | G10 not measurable on CPES | CPU under load was at or below the idle rate in both modes. DEFAULT 1400 ms over 100 s for 743 foreign events; WATERMARK 1310 ms for 731; idle 14 ms/s. |
| **S13** wrong broker / identity | 3 AS_EXPECTED, 1 KNOWN_DEFECT (legacy guard, by contract) | **N16** | **G13** | C: a bundle expecting another cluster is refused (40 `cluster_name_mismatch`, never READY). B: cluster-name guard on, 3/3 after `evento-eu` is killed. A: the single-id guard refuses failover (0/3, 7 mismatch lines), as its contract says. After `evento-eu` returned, 7 EU JVMs were still on `evento-am` (N16). |
| **S14** retry storm (kill 2000 ms) | 1 KNOWN_DEFECT, 2 AS_EXPECTED | **G5** | **G4** | First attempts: 13/24 failed, all `broker_changed`, **0 after ≥ 25 s** (`:before` 24/24 after ≥ 25 s). 9 of 13 failed first attempts had been stored; 8 keys stored twice. `after-kill150`: 24/24 `broker_changed`, none stored, so INCONCLUSIVE for G5. |
| **S15** clock −40 s on `order-query-eu` | 1 KNOWN_DEFECT | fetch staleness gate | - | 0 AFRICA orders projected in 60 s; `evento-eu` logged 120 stale-fetch drops |
| **S16** global ALL lane | 7 AS_EXPECTED | **N4** (lane hops), one-lane stall (by design) | G24, duplicates | Fan-out to 3 DBs **10.2 s** (`:before` 2.1 s). Writers split `global-eu` 9 / `global-apac` 6. EU/AM got 1 of 4 prices during the APAC DB outage. Converged 0.07 s after healing; 6 replays absorbed. **Lane takeover 7.3 s** (integration run 5.2 s, `:before` 7.7 s). |
| **S17** context routing | 8 AS_EXPECTED | - | cross-context rows (0), `ALL` rows in order views (0) | one JVM runs 2 engines; 16 checkpoints in the right DBs; the `*` lane sees 14/14; I6 3.4 s |

## Key numbers at a glance

| Metric | `:after` |
|---|---|
| Ack rate, S00 (7×20/min) | 492/492 = 100 % |
| p50 / p99 per cell, S00 | EU 131–265 / 449–611 ms · AM 733–761 / 1202–1390 ms · APAC 1156–1178 / 1927–1980 ms |
| Appends/s | 9.35 (S00); 7.0 → 6.55 over the S06 sweep |
| Convergence I6 | 0.2–3.7 s in every scenario (S00 3.5 s, S03 0.36 s, S06 0.21 s, S07 0.2 s) |
| Server kill → first ack from the stranded cell (S03) | 10.3 s |
| ES primary kill → writes resume (S08) | 15.1 s |
| Lane takeover (S16) | 7.3 s (integration 5.2 s) |
| ALL fan-out to 3 DBs (S16) | 10.2 s (integration 4.1 s) |
| Fresh starts needing the catalog repair | 0 of 7 |

## New criticalities (details in FINDINGS.md)

| Id | Criticality |
|---|---|
| N14 (High) | A saga retries a command whose outcome is indeterminate (`BrokerChangedException`, partition timeout), so the payment side effect runs twice. Seen in S03 run 1 and S07. This is G5 at application level. |
| N15 (Medium) | Under global append contention (S06 at 300 ms RTT), 31 commands committed after their caller timed out, although N3 dropped 0 at its stages. The time is spent after the last deadline check. |
| N16 (High) | No failback: a bundle that failed over stays on the remote server after its own server returns. Traffic keeps crossing the WAN, and with G1 a gateway that reaches the returned server finds no handler. |
| N17 (Low) | The ES promotion outage is 15.1 s on `:after` against 7.6 s on `:before` (one run each). |
| L19–L24 | Harness and lab defects found during this run, all fixed (above). |
