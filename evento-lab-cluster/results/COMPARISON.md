# `:before` vs `:after`: what the cluster fixes changed, measured

- `:before`: evento 2.4.6 (`FRAMEWORK_REF=e7b823fe`).
- `:after`: the cluster fixes on `feat/evento-lab-cluster`.

Both image sets run the same lab source, topology and harness. Scenarios whose harness changed during
the `:after` run (S03, S07, S08, S12, S13) were re-run on `:before` with the new harness, so every pair
below is comparable. The per-image summaries are [`BEFORE-SUMMARY.md`](BEFORE-SUMMARY.md) and
[`AFTER-SUMMARY.md`](AFTER-SUMMARY.md); the raw evidence is in `results/<S>/{before,after}/`. Each pair
is one run per image set. The differences below are large enough not to need repetitions; where they
are not, the text says so.

## Per scenario

| Scenario | `:before` | `:after` | Change |
|---|---|---|---|
| S00 baseline 7×20/min | 227/319 acked (71 %). p50 EU 10.3 s, AM 25–27 s, APAC 30 s (timeout-bound); p99 ≈ 30.5 s. 5.2 appends/s. I6 13.1 s. 83 unacked commands stored anyway, 3 double payments. | **492/492 (100 %)**. p50 EU 0.13–0.27 s, AM 0.73–0.76 s, APAC 1.16–1.18 s; p99 ≤ 2.0 s. **9.35 appends/s**. I6 3.5 s. 0 unacked stored, 0 duplicates. | fixed (G9, N3) |
| S01 AMERICA handler stopped | 0/5, `no handler`, ≤ 89 ms | 0/5, `no handler`, ≤ 14 ms | **unchanged (G1)** |
| S02 kill a handler replica | 487/487; p50/p99 after the kill 166/296 ms | 486/486; 176/283 ms | same (already worked) |
| S03 kill `evento-apac` | APAC **0/18** while its server was down; 3 in-flight calls failed after ≥ 25 s; I6 25.9 s | APAC **44/44**, first ack **10.3 s** after the kill; 0 hung calls (bundle side: `BrokerChangedException`, run 1); I6 0.36 s | fixed (G2, G4); run 1 shows N14 |
| S04 server returns / paused 60 s | variant B wiped 10 consumer ids in every cell (N12); probes during the outage 0/3; handshake loop afterwards (N13) | 0 unregisters, catalog intact; probes during the outage 3/3; no loop | fixed (G7, N12, N13 not reproduced) |
| S05 same aggregate EU+APAC | 112/120; 8 cross-server `deadlock detected`; p50 2.3 s, p99 4.8 s | **120/120**; 0 deadlocks; p50 0.93 s, p99 2.8 s | fixed (G3) |
| S06 WAN sweep, appends/s at 0/50/150/300 ms | 7.0 / 7.0 / 4.05 / 1.65 | 7.0 / 7.0 / 7.0 / 6.55 | much better (G9) |
| S06 APAC p50 at 0/50/150/300 ms | 32 ms / 12.3 s / 30.1 s / 30.2 s | 29 ms / 0.43 s / 2.7 s / 22.8 s | better; 300 ms still saturates (N5, N15) |
| S06 failed / stored after timeout | 226 failed / 136 stored anyway | 31 failed / 31 stored anyway | better; residual N15 |
| S07 APAC silent partition | EU+AM **0/16** acked; no append cluster-wide for ~90 s (N11) | EU+AM **41/41**, p50 585 ms | fixed (N11) |
| S07 lanes, I2 | `*` lanes idle (no events: the whole cluster had stopped); no duplicates | `*` lanes ≤ 5 s behind; 2 payments ran twice, 1 event stored twice (N14) | new visible defect (N14 = G5), because APAC now keeps working locally |
| S08 ES primary killed, sync standby promoted | writes resumed in **7.6 s**; 105/106 after the kill | writes resumed in **15.1 s**; 103/106 after the kill | both work once L24 is fixed; `:after` is slower (N17) |
| S09 app primary promoted | checkpoints monotonic, 58 samples | monotonic, 59 samples | same |
| S10 cold start ×3 | 1/3 iterations failed; 20 schema-race lines | 0/3; 0 lines | fixed (G14) |
| S11 underscore contexts | 2 rows leaked (`SUDXAMERICA`, `AUSTRALIAXNZ`); page shows 0/7 regional lanes (N10); G16 untestable | 0 leaked; page 7/7 with the `_` contexts right | fixed (G15, G16, N10) |
| S12 WATERMARK cost | no load-attributable CPU (254 foreign events per phase, capped by G9) | no load-attributable CPU (743 foreign events) | G10 not measurable with CPES on either |
| S13 identity | C: a bundle expecting a foreign cluster **attaches** (no identity); B: failover works with **no guard at all** (2/3); A: single-id guard 0/3 | C: **refused** (40 `cluster_name_mismatch`); B: 3/3 **with the guard on**; A: 0/3 (contract) | fixed (G13); N16 seen |
| S14 retry storm (kill 2000 ms) | 24/24 first attempts failed only after ≥ 25 s (timeout); 1/24 stored before the retry; 1 key stored twice | 13/24 failed, all `broker_changed`, **0 after ≥ 25 s**; 9/13 stored before the retry; **8 keys stored twice** | G4 fixed; **G5 worse in practice** (below) |
| S15 clock −40 s | every fetch dropped (`age=40004ms > max=30000ms`); 0/5 projected | same (120 stale drops in 60 s) | **unchanged** |
| S16 ALL lane | fan-out 2.1 s, all from `global-eu`; takeover 7.7 s | fan-out **10.2 s**, writers split EU 9 / APAC 6; takeover 7.3 s (integration 5.2 s) | G24 held on both; N4 makes the fan-out slower |
| S17 context routing | 8/8 | 8/8 | same |
| Fresh starts needing the catalog repair (G8) | 7 of 7 | 0 of 7 | fixed |

## Per gap

| Gap | `:before` evidence | `:after` evidence | Status after the runs |
|---|---|---|---|
| G1 inter-server routing | S01 `no handler` 0/5 | S01 identical | **open** (by design: cell architecture) |
| G2 failover to another address | S03 APAC 0/18; S04 probes 0/3 | S03 44/44, 10.3 s; S04 3/3; S13 B 3/3 | **fixed**, but no failback (N16) |
| G3 lock pins one connection | S05 8 deadlocks; S07/N11 cluster-wide write stop | S05 0; S07 41/41 | **fixed** |
| G4 in-flight calls on broker change | S14 24/24 after ≥ 25 s; S03 3 hung | S14 0 after ≥ 25 s (`broker_changed`); S03 0 | **fixed** |
| G5 exactly-once across servers | S14 1 key twice; S00 3 double payments (N3) | S14 8 keys twice; S03/S07 double payments (N14) | **open**. Failing fast makes the duplicate window easier to hit: the caller retries while the first attempt is still committing. |
| G6 append fence | no fork in S05/S08 | no fork in S05/S08 | **partly fixed**; the lab cannot tell (no fork on either) |
| G7 stale NodeLeft | S04 B: 10 consumer ids wiped (N12) | S04 B: 0 | **fixed** |
| G8 registration race | 7/7 fresh starts | 0/7 | **fixed** |
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
| Fetch staleness gate vs clock skew | S15 0/5 | S15 0/5 | **open** |

## What is still broken (honest list)

1. **G1, no server mesh.** A command reaches its handler only if both are attached to the same
   server. S01 fails in ≤ 14 ms with `no handler` on both image sets. The cell layout hides this
   only while every bundle stays on its own cell's server, and N16 breaks that.
2. **N16, no failback.** G2 moves bundles away from a dead server but never brings them back. After
   S03 or S13 the stranded cell's JVMs stay on the remote server until they are restarted. The
   cell's commands and consumers then cross the WAN for good, and a gateway that lands on the
   restarted server gets `no handler` (G1). The lab now restarts misplaced JVMs before each
   scenario; production would need a periodic fail-back probe (planned in G2, not implemented).
3. **G5 and N14, no idempotency across retries.** `:after` fails fast and correctly
   (`BrokerChangedException`: "may or may not have been applied"), but nothing lets a retry be
   applied once. In S14, 9 of 13 fast-failed commands had been stored and 8 keys ended up stored
   twice. In S03 run 1 and in S07, the saga's automatic retry ran payments twice. An idempotency key
   carried from the caller (gateway or saga) into the append is still missing.
4. **G6, no `aggregate_seq` fence.** No fork showed up (S05, S08), but the fence that would rule one
   out is open.
5. **N4, global lane placement.** The ALL lane's lock is taken per fetch cycle, so the holder hops
   between `global-eu` and `global-apac` (writers 9/6 in S16). The APAC replica writes to the EU and
   AM databases across the WAN, and the fan-out took 10.2 s against 2.1 s on `:before`, where the
   holder happened to stay put. A lane lease with affinity, or placing the lane in the primary's
   cell, is still to do.
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
