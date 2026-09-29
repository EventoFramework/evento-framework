# `:before` vs `:after`: what the cluster fixes changed, measured

- `:before`: evento 2.4.6 (`FRAMEWORK_REF=e7b823fe`).
- `:after` (pre-G5/N16): the cluster fixes on `feat/evento-lab-cluster`, before idempotency keys and
  fail-back were merged (`results/<S>/after/`).
- `:after` + G5 + N16 (`after-g5n16`): the same branch with idempotency keys (G5) and fail-back (N16)
  and their review fixes; eight scenarios re-run on fresh stacks (`results/<S>/after-g5n16/`), the others
  were not re-run and keep only the pre-G5/N16 column.

Both image sets run the same lab source, topology and harness. Scenarios whose harness changed during
the `:after` run (S03, S07, S08, S12, S13) were re-run on `:before` with the new harness, so every pair
below is comparable. The per-image summaries are [`BEFORE-SUMMARY.md`](BEFORE-SUMMARY.md) and
[`AFTER-SUMMARY.md`](AFTER-SUMMARY.md); the raw evidence is in `results/<S>/{before,after}/`. Each pair
is one run per image set. The differences below are large enough not to need repetitions; where they
are not, the text says so.

The S03, S07, S13 and S14 harness changed again for G5/N16 (fail-back wait, separate exactly-once
observations, the gateway passing `X-Idempotency-Key` and deriving a keyed create's order id from it), so
those four were re-run on `:before` too (2.4.6 rebuilt with the current lab source, `before-g5n16`); the
`:before` cells quote both. Details of the re-run, including the log verification, are in
[`AFTER-SUMMARY.md`](AFTER-SUMMARY.md) §"Re-run with idempotency keys".

## Per scenario

| Scenario | `:before` | `:after` (pre-G5/N16) | `:after` + G5 + N16 (`after-g5n16`) | Change |
|---|---|---|---|---|
| S00 baseline 7×20/min | 227/319 acked (71 %). p50 EU 10.3 s, AM 25–27 s, APAC 30 s (timeout-bound); p99 ≈ 30.5 s. 5.2 appends/s. I6 13.1 s. 83 unacked commands stored anyway, 3 double payments. | **492/492 (100 %)**. p50 EU 0.13–0.27 s, AM 0.73–0.76 s, APAC 1.16–1.18 s; p99 ≤ 2.0 s. **9.35 appends/s**. I6 3.5 s. 0 unacked stored, 0 duplicates. | **482/482**. p50 EU 176–180 ms, AM 834–879 ms, APAC 1359–1472 ms; p99 ≤ 2.7 s. 9.02 appends/s. I6 3.5 s. 0 unacked stored, 0 duplicates; every event keyed | fixed (G9, N3); keys cost remote cells about one RTT (p50 APAC +0.2–0.3 s) |
| S01 AMERICA handler stopped | 0/5, `no handler`, ≤ 89 ms | 0/5, `no handler`, ≤ 14 ms | not re-run | **unchanged (G1)** |
| S02 kill a handler replica | 487/487; p50/p99 after the kill 166/296 ms | 486/486; 176/283 ms | 490/490; 174/274 ms | same (already worked) |
| S03 kill `evento-apac` | APAC **0/18** while its server was down; 3 in-flight calls failed after ≥ 25 s; I6 25.9 s (`before-g5n16`: 0/18 again, fail-back INCONCLUSIVE: 2.4.6 never fails over; I6 22.6 s) | APAC **44/44**, first ack **10.3 s** after the kill; 0 hung calls (bundle side: `BrokerChangedException`, run 1); I6 0.36 s | APAC **46/46**, first ack **10.9 s**; **8/8 JVMs failed back** with no restart, last 55.7 s after `evento-apac` was ready; 0 events and 0 payments twice (3 `BrokerChangedException` on the bundle side); I6 0.38 s | fixed (G2, G4, **N16**, **N14**) |
| S04 server returns / paused 60 s | variant B wiped 10 consumer ids in every cell (N12); probes during the outage 0/3; handshake loop afterwards (N13) | 0 unregisters, catalog intact; probes during the outage 3/3; no loop | not re-run | fixed (G7, N12, N13 not reproduced) |
| S05 same aggregate EU+APAC | 112/120; 8 cross-server `deadlock detected`; p50 2.3 s, p99 4.8 s | **120/120**; 0 deadlocks; p50 0.93 s, p99 2.8 s | **120/120**; 0 deadlocks; p50 1.16 s, p99 3.0 s (EUROPA 212 ms, CINA 1317 ms) | fixed (G3); CINA p50 +0.25 s (key read, one run each) |
| S06 WAN sweep, appends/s at 0/50/150/300 ms | 7.0 / 7.0 / 4.05 / 1.65 | 7.0 / 7.0 / 7.0 / 6.55 | not re-run | much better (G9) |
| S06 APAC p50 at 0/50/150/300 ms | 32 ms / 12.3 s / 30.1 s / 30.2 s | 29 ms / 0.43 s / 2.7 s / 22.8 s | not re-run | better; 300 ms still saturates (N5, N15) |
| S06 failed / stored after timeout | 226 failed / 136 stored anyway | 31 failed / 31 stored anyway | not re-run | better; residual N15 |
| S07 APAC silent partition | EU+AM **0/16** acked; no append cluster-wide for ~90 s (N11) (`before-g5n16`: 0/16 again) | EU+AM **41/41**, p50 585 ms | EU+AM **44/44**, p50 666 ms | fixed (N11) |
| S07 lanes, I2 | `*` lanes idle (no events: the whole cluster had stopped); no duplicates (`before-g5n16`: the same) | `*` lanes ≤ 5 s behind; 2 payments ran twice, 1 event stored twice (N14) | `*` lanes stalled 0 s; **0 events twice, 0 payments twice**; the residual window was not hit | **N14 fixed (G5)**; residual window documented, not observed |
| S08 ES primary killed, sync standby promoted | writes resumed in **7.6 s**; 105/106 after the kill | writes resumed in **15.1 s**; 103/106 after the kill | not re-run | both work once L24 is fixed; `:after` is slower (N17) |
| S09 app primary promoted | checkpoints monotonic, 58 samples | monotonic, 59 samples | not re-run | same |
| S10 cold start ×3 | 1/3 iterations failed; 20 schema-race lines | 0/3; 0 lines | not re-run | fixed (G14) |
| S11 underscore contexts | 2 rows leaked (`SUDXAMERICA`, `AUSTRALIAXNZ`); page shows 0/7 regional lanes (N10); G16 untestable | 0 leaked; page 7/7 with the `_` contexts right | not re-run | fixed (G15, G16, N10) |
| S12 WATERMARK cost | no load-attributable CPU (254 foreign events per phase, capped by G9) | no load-attributable CPU (743 foreign events) | not re-run | G10 not measurable with CPES on either |
| S13 identity | C: a bundle expecting a foreign cluster **attaches** (no identity); B: failover works with **no guard at all** (2/3); A: single-id guard 0/3 (`before-g5n16`: C attaches; B 0/3 before the kill (`no handler`, likely the EU handlers not yet re-registered on the restarted server; not investigated) and 3/3 after; fail-back INCONCLUSIVE) | C: **refused** (40 `cluster_name_mismatch`); B: 3/3 **with the guard on**; A: 0/3 (contract) | C refused (40 `cluster_name_mismatch`); B 3/3 with the guard; A 0/3 (contract); **7/7 EU JVMs failed back** 53.3 s after `evento-eu` was ready, 0 restarts | fixed (G13, **N16**) |
| S14 retry storm (kill 2000 ms; `after-g5n16` main run 3000 ms) | 24/24 first attempts failed only after ≥ 25 s (timeout); 1/24 stored before the retry; 1 key stored twice (`before-g5n16`, new gateway: kill 2000 ms 24/24 after ≥ 25 s, 1 stored before the retry; kill 3000 ms 24/24 after ≥ 25 s, 2 stored. 0 keys twice in both, because a keyed create now addresses the same order id, but every stored-then-retried create was refused with 409: 0/1 and 0/2 answered 2xx) | 13/24 failed, all `broker_changed`, **0 after ≥ 25 s**; 9/13 stored before the retry; **8 keys stored twice** | kill **3000 ms**: 18/24 failed, all `broker_changed`, 0 after ≥ 25 s; 4/18 stored before the retry; **0 keys stored twice; 4/4 retries 2xx** with the stored event (`idempotent_replay`). Kill 2000 ms: 22/24 failed, none stored (INCONCLUSIVE, `after-g5n16-kill2000`) | G4 fixed; **G5 fixed** |
| S15 clock −40 s | every fetch dropped (`age=40004ms > max=30000ms`); 0/5 projected | same (120 stale drops in 60 s) | not re-run | **unchanged** |
| S16 ALL lane | fan-out 2.1 s, all from `global-eu`; takeover 7.7 s | fan-out **10.2 s**, writers split EU 9 / APAC 6; takeover 7.3 s (integration 5.2 s) | fan-out **8.1 s**, writers APAC 9 / EU 6; takeover **4.4 s**; convergence after healing 16.3 s | G24 held; N4 still open (fan-out and takeover vary per run) |
| S17 context routing | 8/8 | 8/8 | not re-run | same |
| Fresh starts needing the catalog repair (G8) | 7 of 7 (`before-g5n16`: 5 of 5) | 0 of 7 | 0 of 9 checked | fixed |

## Per gap

| Gap | `:before` evidence | `:after` evidence (pre-G5/N16; `after-g5n16` where re-run) | Status after the runs |
|---|---|---|---|
| G1 inter-server routing | S01 `no handler` 0/5 | S01 identical | **open** (by design: cell architecture) |
| G2 failover to another address | S03 APAC 0/18; S04 probes 0/3 | S03 44/44, 10.3 s; S04 3/3; S13 B 3/3; `after-g5n16` S03 46/46, 10.9 s | **fixed**, with fail-back since N16 |
| G3 lock pins one connection | S05 8 deadlocks; S07/N11 cluster-wide write stop | S05 0; S07 41/41 | **fixed** |
| G4 in-flight calls on broker change | S14 24/24 after ≥ 25 s; S03 3 hung | S14 0 after ≥ 25 s (`broker_changed`); S03 0 | **fixed** |
| G5 exactly-once across servers | S14 1 key twice; S00 3 double payments (N3); `before-g5n16` S14: 0 keys twice, but the stored create's retry refused with 409 | pre-G5: S14 8 keys twice; S03/S07 double payments (N14). `after-g5n16`: S14 0 keys twice, 4/4 stored retries 2xx; S03/S07 0 events and 0 payments twice | **fixed, measured**. Residual: a service handler that ran while its event could not be stored runs again on retry (not hit in S07's run); keys never expire |
| G6 append fence | no fork in S05/S08 | no fork in S05/S08 | **partly fixed**; the lab cannot tell (no fork on either) |
| G7 stale NodeLeft | S04 B: 10 consumer ids wiped (N12) | S04 B: 0 | **fixed** |
| G8 registration race | 7/7 fresh starts (`before-g5n16`: 5/5 more) | 0/7 (`after-g5n16`: 0/9 more) | **fixed** |
| G9 append cost | S00 5.2 appends/s, 71 % acked; S06 1.65/s at 300 ms | S00 9.35/s, 100 %; S06 6.55/s at 300 ms | **fixed**; the per-append WAN cost remains (N5, N9) |
| G10 WATERMARK O(gap) | not measurable | not measurable | **untested** by this lab (CPES gaps are small) |
| G11 checkpoint regressions | S09 monotonic | S09 monotonic | held on both |
| G12 locality | not available | S05 EUROPA p50 186 ms vs CINA 1070 ms; reads served in the cell (integration run) | **fixed** (zone) |
| G13 server identity | S13 C attaches to the foreign cluster | S13 C refused, B works with the guard | **fixed** |
| G14 concurrent `schema.sql` | S10 1/3 failed | S10 0/3 | **fixed** |
| G15 LIKE `_` | S11 2 leaks | S11 0 | **fixed** |
| G16 consumer id split on `_` | untestable (N10) | S11 page correct | **fixed** |
| G24 one lane per context | S16 one checkpoint | S16 one checkpoint | held on both |
| G25 orphaned consumer lock | S07 no stalled lane | S07 ≤ 5 s | not observed on either |
| N3 commands executed after timeout | S00 83, S06 136 stored after timeout | S00 0, S06 31 | **mostly fixed**; residual N15 |
| N4 lane placement | S16 holder did not hop | S16 split EU 9 / APAC 6; fan-out 10.2 s | **open** |
| N11 partition stops all appends | S07 0/16 | S07 41/41 | **fixed** |
| N12 bundle-wide consumer wipe | S04 B | not reproduced | **fixed** (with G7 + N1) |
| N13 handshake supersede loop | after S04 | not reproduced after S04 | not reproduced |
| N14 saga retries duplicate a payment | not visible (the cluster stopped, N11) | pre-G5: S03 run 1 and S07, 2 payments twice; `after-g5n16`: 0 in S03 and S07 | **fixed** by G5 (derived keys) |
| N16 no fail-back after failover | nothing to fail back (2.4.6 never fails over; `before-g5n16` INCONCLUSIVE) | pre-N16: S03 8 and S13 7 JVMs stayed remote; `after-g5n16`: 8/8 and 7/7 home without restarts in 55.7 s / 53.3 s, 0 failed | **fixed, measured**; residual: per-bundle return (no `no handler for` observed) |
| Fetch staleness gate vs clock skew | S15 0/5 | S15 0/5 | **open** |

## What is still broken (honest list)

1. **G1, no server mesh.** A command reaches its handler only if both are attached to the same
   server. S01 fails in ≤ 14 ms with `no handler` on both image sets. The cell layout hides this
   only while every bundle stays on its own cell's server; since N16 a failed-over cell comes back on
   its own, but one bundle at a time, so a caller can briefly meet `no handler for` during the return
   (not observed in S03/S13 `after-g5n16`: the handlers were home before the gateways moved).
2. **N16, fail-back: fixed and measured** (`after-g5n16`). S03: 8 of 8 APAC JVMs back on `evento-apac`
   without a restart, the last 55.7 s after it was ready; S13: 7 of 7 EU JVMs back on `evento-eu` in
   53.3 s; 0 failed, 0 suspended, 0 reactive switches. What stays open: the return is per bundle, not per
   cell (see 1), a bundle behind a single-address VIP has no endpoint 0 to probe, and the stability window
   means about a minute of cross-WAN traffic after a server returns.
3. **G5 and N14, idempotency: fixed and measured** (`after-g5n16`). S14 (kill 3000 ms): 4 of 18
   fast-failed commands had been stored, their retries were answered 2xx with the stored event, 0 keys
   stored twice (pre-G5: 8 twice). S03 and S07: 0 events stored twice, 0 payments executed twice. What
   stays open: a service handler that ran while its event could not be stored (a partitioned cell) runs
   again on the retry, so its side effect must be idempotent on the key it receives (not hit in this S07
   run); keys never expire; a keyed retry that lands on a pre-G5 server runs unkeyed. Cost: remote cells
   pay about one more RTT per keyed command (S00 p50 APAC 1.36–1.47 s against 1.16–1.18 s).
4. **G6, no `aggregate_seq` fence.** No fork showed up (S05, S08), but the fence that would rule one
   out is open.
5. **N4, global lane placement.** The ALL lane's lock is taken per fetch cycle, so the holder hops
   between `global-eu` and `global-apac` (writers 9/6 in S16). The APAC replica writes to the EU and
   AM databases across the WAN, and the fan-out took 10.2 s against 2.1 s on `:before`, where the
   holder happened to stay put. The `after-g5n16` run split the same way (APAC 9 / EU 6, fan-out
   8.1 s, takeover 4.4 s, convergence after healing 16.3 s). A lane lease with affinity, or placing the
   lane in the primary's cell, is still to do.
6. **N5, N9, N15: every append serialises on one lock across the WAN.** At 150 ms RTT to the ES
   primary even EU's p50 is 1.7 s; at 300 ms every cell is in the tens of seconds, and 31 commands
   committed after their caller had timed out. The deadline is not checked while the append waits
   on the `es-lock` inside the statement.
7. **Fetch staleness gate vs clock skew (S15).** A consumer whose clock is more than 30 s behind the
   server never gets an event. The request age should be measured from the server's own receive
   time, or the gate should be relaxed for fetches.
8. **N17, slower ES failover on `:after`.** 15.1 s against 7.6 s (one run each). The likely cause
   is that the separate lock pool's connections to the dead primary have to time out; this is not
   verified.
9. **G10, G17, G18, G20, G21 were not exercised or not measurable** here: CPES only, a single
   dashboard, discovery on the bus thread, and no read datasource.
