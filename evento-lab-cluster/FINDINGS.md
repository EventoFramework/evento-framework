# evento-lab-cluster: findings (images `:before` = evento 2.4.6 and `:after` = the cluster fixes, 2026-09-29)

| Id | What | Severity | Lab status |
|---|---|---|---|
| N1 | `core__component` keyed by simple class name across bundles | High (catalog) | workaround removed: regions are contexts of one class (lab/bundles) |
| G8 | discovery / consumer-registration insert race | High (catalog) | fixed: `:after` starts complete with `REPAIR_CATALOG=false`; `:before` still needs `repair-catalog.sh` (integration run) |
| G9 (+G3) | about 14 sequential WAN round trips per command from a remote cell | High (throughput) | `:after` S00: 498/498 acked, p50 AM 0.72 s, APAC 1.12 s; residual in N9 |
| N2 | connection liveness probes on the command path, each one a WAN round trip | Medium | recorded |
| N3 (+G4/G5) | commands still executed after the caller timed out; saga retries duplicate the payment | High (correctness) | fixed up to the append on `:after` (S00: 0 stored after timeout, `:before` 83); residual in N15, retries in N14 |
| N4 (+G24/G12) | the single global consumer lane hops between cells and projects over the WAN | Medium | **open**: `:after` S16 writers split EU 9 / APAC 6, fan-out 10.2 s (`:before` 2.1 s) |
| N5 | the CPES `es-lock` favours the cell nearest the ES primary | Medium | recorded |
| L7 | toxiproxy OOM-killed at 64m during base backups | Blocker | fixed (256m) |
| L8 | no restart policy; no healthcheck on cell-core and saga | Medium | fixed |
| L9 | `LAB_TAG` defaulted to `dev` while the stack ran `before` | Medium | fixed |
| N6 | consumer registration lands up to ~25 s after the bundle is healthy | Low (ops) | recorded; lab tooling waits for it |
| N7 | `evento_v2_consumer_state.updated_at` never moves after the first commit | Low (observability) | recorded |
| N8 | a bundle without handlers gets no `core__bundle` row | Low (catalog) | recorded; harness adjusted |
| N9 | a remote-cell command still costs about 7-9 WAN round trips after G9 | Medium (throughput) | recorded |
| N10 | pre-fix dashboard lists one consumer per component, hiding the per-context lanes | Medium (admin) | fixed in the working tree; seen on `:before` |
| L10 | `repair-catalog.sh --check` said "complete" when its query failed | High (lab) | fixed |
| L11 | harness catalog read (I7) used the pre-N1 column | Medium (lab) | fixed |
| L12 | I7 expected a catalog row for the send-only gateway | Low (lab) | fixed |
| L13 | lab-api did not compile against the pre-fix framework | High (lab) | fixed |
| N11 | a silent partition of one remote cell stops appends cluster-wide | High (availability) | fixed on `:after` (S07: EU+AM 41/41, `:before` 0/16) |
| N12 | a stale NodeLeft on 2.4.6 wipes a shared bundle id's consumers in every cell; the repair cannot converge | High (catalog) | not reproduced on `:after` (S04 B: 0 unregisters) |
| N13 | handshake supersede loop after a paused server returns | Medium (availability) | not reproduced on `:after` (after S04) |
| L14 | `repair-catalog.sh` ignored lost handler rows of sagas and the observer | Medium (lab) | fixed |
| L15 | S11 marked G16 AS_EXPECTED on a page that shows no `_` context, and blamed G8 for N10 | Medium (lab) | fixed |
| L16 | the chaos driver ran the catalog repair with WSL `bash` on Windows | High (lab) | fixed |
| L17 | S14 marked G5 AS_EXPECTED with no command in the ambiguous window | Medium (lab) | fixed |
| L18 | S15 skewed the clock ahead, which can never trip the staleness gate | Medium (lab) | fixed |
| N14 (+G5) | a saga retries an indeterminate command (broker change, partition) and the payment runs twice | High (correctness) | **open** (`:after` S03 run 1, S07) |
| N15 (+N3/N5) | under `es-lock` contention commands commit after their caller's deadline; N3 drops none | Medium (correctness) | **open** (`:after` S06: 31 at 300 ms) |
| N16 (+G2/G1) | no failback: bundles stay on the remote server after their own server returns | High (availability, cost) | **open** (`:after` S03, S13) |
| N17 | ES promotion outage 15.1 s on `:after` vs 7.6 s on `:before` | Low (availability) | recorded, one run each |
| L19 | S12's per-event CPU ratio was idle-cost noise | Medium (lab) | fixed |
| L20 | S07 read a lane with nothing to consume as stalled | Medium (lab) | fixed |
| L21 | S13 ran only the legacy single-id guard; S03's G4 check ignored bundle-side calls | Medium (lab) | fixed |
| L22 | scenarios after a server kill ran with bundles on the wrong cell's server (N16) | High (lab) | fixed |
| L23 | a failed lb re-attach misrouted every region to gateway-cina, silently | High (lab) | fixed |
| L24 | ES replicas inherited `synchronous_standby_names`; a promoted one blocked every commit | High (lab) | fixed |

## N1: components keyed by simple class name across bundles
- **Cause:** `core__component.component_name` is the primary key. The seven `query-<r>` bundles all ran `RegionOrderProjector`.
- **Evidence, handler rows:** in `before-attempt1`, 6 of the 7 query bundles had no `core__handler` rows.
- **Evidence, consumer rows:** restarting only `region-africa` logged `[BundleService] Unregister called for bundleId=query-africa`. `BundleService.unregister` (`:48-88`) deletes the bundle's components and `deleteAllByComponentIn` their consumers. That wiped the `core__consumer` rows of all 7 regional bundles, and the repair check afterwards listed all 7 regions as missing.
- **Impact:** a wrong catalog, admin UI and consumer administration, and one JVM restart erases unrelated bundles' entries cluster-wide. Routing is not affected, because it is in-memory.
- **Lab workaround (removed):** one projector class per region. Since the bundle split the lab no longer works around it: every region is a context of the one `OrderProjector` of bundle `order-query`, and all order-query deployments are one bundle, so no two bundles share a component class. The per-region classes and `RegionOrderProjection` are deleted.
- **Fix proposal:** key components by `(bundle_id, component_name)`, and have unregister delete only rows of that bundle.

## G8: discovery and consumer registration race inside one bundle's first registration
- **Frequency:** it happened on every fresh start.

  | Start | JVMs with missing catalog rows | What was lost |
  |---|---|---|
  | run 1 | 3 of 12 (`cell-core-eu`, `region-africa`, `saga-eu`) | consumer rows |
  | run 3 | 7 of 12 | 3 lost consumer rows; 7 bundles lost all handler rows |
  | run 4 | 6 of 12 | 3 lost consumer rows; 3 lost handler rows |

- **Log counts, run 4:**

  | Server | `core__bundle_pkey` | `core__component_pkey` | registration failed | discovery failed |
  |---|---|---|---|---|
  | evento-eu | 13 | 5 | 2 | 2 |
  | evento-am | 0 | 5 | 0 | 1 |
  | evento-apac | 4 | 10 | 1 | 2 |

- **Mechanism:** `AutoDiscoveryService.onNodeJoin` (lock key `DISCOVERY:<bundleId>`) and `ConsumerService.registerConsumers` (lock key `CONSUMER_DISCOVERY:<instanceId>`) run in parallel for the same bundle. Both find-then-insert `core__bundle`, and `ConsumerService.resolveComponent` inserts an "ephemeral" component.
- **If ConsumerService loses:** that instance gets no `core__consumer` rows.
- **If AutoDiscovery loses** (on `core__component_pkey`): one `try` around the handler loop aborts the whole pass, so the bundle gets no `core__handler` rows.
- **A restart does not repair it:** NodeLeft unregisters the single-instance bundle first. Three restarts of `region-africa` lost the race again each time.
- **Lab workaround:** start a temporary copy of the JVM (`compose run`, instance id `<c>-helper`) so the bundle stays live and its rows exist, restart the JVM, then remove the copy. It converged in one pass every time.

## G9 (+G3): cost of one command, measured
- **Unloaded single create:**

  | Cell | Latency | RTT to ES primary | Equivalent round trips |
  |---|---|---|---|
  | EU | 0.10 s | local | - |
  | AM | 1.18–1.26 s | 80 ms | about 15 |
  | APAC | 2.19–2.23 s | 150 ms | about 14.6 |

- **SQL trace of one AMERICA create:**
  - `RESOURCE` advisory lock;
  - a snapshot read-only transaction;
  - the aggregate event load;
  - the `es-lock` advisory lock (plus a parallel `pg_try_advisory_lock`);
  - `nextval`;
  - the `INSERT` (paying the sync commit wait);
  - two more short transactions;
  - three unlocks.

  The command spans 1.13 s. The `es-lock` is held about 414 ms (5 round trips) per AM append, and about 750 ms from APAC.
- **Capacity:**
  - At 20/min/region the cluster saturates at 4.6–4.9 appends/s, with 92 of 309 (and 94 of 320) commands timing out.
  - At 3/min/region it runs at 1.45 appends/s with every command acknowledged. Per-region p50 is EU 0.17 s, AM 2.4 s, APAC 3.6 s.
  - The assessment's S0 load (about 9 events/s offered) is roughly twice capacity.

## N2: liveness probes on the command path
- **Evidence:** the trace shows empty statements (pgjdbc `isValid()`) before every lock, unlock and borrow. They come from `PgDistributedLock` (`:28-31`) and from Hikari's check on borrow after 500 ms idle. That is at least 4 of the about 14 round trips.
- **Fix proposal:** validate only after an I/O error, or rely on keepalive. The problem also goes away with the G3 and G9 fixes.

## N3 (+G4/G5): commands executed after the caller timed out
- **Evidence:**
  - All 92 of 92 timed-out commands in `before` were stored anyway (94 of 94 in `before-attempt1`).
  - Order `s00-before-c48984-europa-14` got `PaymentProcessedEvent` 4 times (seq 235, 312, 313, 314), and the `payments` table shows 4 attempts. In `saga-apac`, `PaymentSaga` got `RequestTimeoutException` after 30 s on attempts 1 to 3, the consumer retried, and every command ran in the end.
- **Fix proposals:**
  - propagate a deadline on `Request` so the server drops expired requests;
  - an idempotency key (G5), which a saga could derive from `(sagaId, eventSeq)`;
  - a typed exception for indeterminate outcomes (G4).

## N4 (+G24/G12): the global lane is placed without locality
- **Mechanism:** the `cluster-query-global` lane hops between cell-core EU, AM and APAC (visible in `order_view_global.updated_by`). When an AM or APAC JVM holds it, every projection writes to the EU app-DB primary over the WAN, plus the sync standby commit.
- **Throughput:** about 1 event/s while held by APAC, about 1.7 events/s by AM.
- **Lag:** seq 335 was committed at 07:54:18 and projected at 07:55:22, 64 s later. I6 failed in `before-attempt1` and converged in 71 s in `before`.
- **Proposals:** a lane-affinity hint for `JdbcConsumerLock`, batched checkpoints, or documenting that global consumers belong in the primary's cell.

## N5: the `es-lock` is unfair to remote cells
- **Evidence:** under saturation, EU commands were 100% acknowledged (p50 about 10 s), while APAC acknowledged 35–41% at the 30 s timeout. A remote server pays 2–3 round trips per lock cycle, and its own requests queue behind its single pinned lock connection (G3).
- **Fix:** G9 (a single short transaction) removes most of the hold time.

## Not observed at baseline
- **I4 held:** no foreign-context rows in any regional table, including SUD_AMERICA and AUSTRALIA_NZ.
- **Left to their scenarios:** G15 (S11; the app agent's local smoke test saw `SUDXAMERICA` leak), G13 (S13) and G2 (every JVM is on its own server).

## Lab defects fixed
- **L7:** toxiproxy was OOM-killed (exit 137) during the four concurrent base backups, which cut every WAN link; the standbys logged `could not translate host name "toxi-am"` and `up` failed. Fixed by raising its limit to 256m (it idles at about 27 MiB). A toxiproxy restart also drops the baseline toxics; `scripts/wan.sh apply` reinstalls them.
- **L8:** the app services had no `restart:` policy, and `cell-core` and `saga` had no healthcheck. Fixed with `restart: on-failure` and the actuator probe for every role.
- **L9:** the scripts defaulted to `LAB_TAG=dev` while the stack ran `before`. Fixed by using the running stack's tag when `LAB_TAG` is unset.

## Resources (idle after S00)
- **Totals:** about 3.7 GiB used, against about 8.5 GB of limits.
- **Per container (used / limit):**

  | Container | Memory |
  |---|---|
  | servers | 330–350 of 512 MiB |
  | cell cores | about 210 of 384 MiB |
  | regions | about 200–215 of 320 MiB |
  | sagas | about 195–215 of 320 MiB |
  | es-pg-eu | about 198 of 384 MiB |
  | app-pg-eu | about 194 of 512 MiB |
  | replicas | about 24 MiB |
  | toxiproxy | about 27–33 of 256 MiB |

- **Stability:** `RestartCount=0` for every container.

## Bundle split (lab/bundles): local end-to-end smoke, framework at `e7b823fe`
One evento-server jar, one Postgres, ten bundle JVMs on the host over real TCP (not the compose stack).
- **Contexts:** `order-query-eu` with `EVENTO_CONTEXTS=EUROPA,AFRICA` ran two engines
  (`order-query_OrderProjector_1_EUROPA` / `_AFRICA`); the `*` deployment filled `order_view_global` with
  every region and ran no `CatalogProjector` engine; no row with context `ALL` in any `order_view`.
- **Global lane:** two `ALL` replicas sharing the `consumers` database left one checkpoint
  (`order-query_CatalogProjector_1_ALL`); every price reached both regional databases with
  `applied_events = 1`. With one target database refusing connections the head event reached the healthy
  target, the checkpoint stayed put, and after healing both converged with `applied_events = 1` (the healthy
  target counted one refused replay in `stale_skips`).
- **Lane hop:** after the first holder had processed a few events, the other replica took the next one
  without any fault: the JDBC consumer lock is taken per fetch cycle, so the holder of the single lane is not
  sticky (N4). One lane, one checkpoint, but no locality of the holder.
- **G8 again:** on the first registration the server logged `core__component_pkey` / `core__payload_pkey`
  duplicates and `Consumer registration failed for bundle: order-saga`, so the saga's `core__consumer` rows
  were missing; routing and consumption were unaffected.
- **Locality before G12:** with every bundle on one server and `RANDOM` pick, a `FindOrderQuery` from the EU
  gateway was sometimes answered by the AM deployment (404: the order is in another cell's database). In the
  compose topology each cell has its own server (islands), so queries stay in-cell; after G12 the
  `locality-preferred` pick keeps them in-cell on a meshed cluster too.

## Integration on the per-bundle topology (images `:after` and `:before`, 2026-09-29)
Evidence: [`results/_integration/after/checks.md`](results/_integration/after/checks.md),
[`results/_integration/before/checks.md`](results/_integration/before/checks.md), and the scenario
results `results/S00/after-smoke`, `results/S16/after` and `results/S17/after`.

- **Topology:** both image sets start healthy at the first `up`. Every deployment registers on
  its cell server. Consumer ids are one per (component, context): `order-query_OrderProjector_1_EUROPA`
  and `_AFRICA` come from the single JVM `order-query-eu`. There is exactly one
  `order-query_CatalogProjector_1_ALL`, and the wildcard lanes are `order-query_OrderProjector_1_*`
  and `order-observer_NotificationObserver_1_*`. There is at most one holder of the ALL lane lock
  at any sample, a price reaches the three regional databases once, and reads stay in-cell.
- **G8 closed end to end:** with `REPAIR_CATALOG=false`, `:after` had a complete catalog (checked
  again after S16 killed and restarted a lane holder). `:before` lost the consumer rows of
  `order-query-wildcard` and `order-saga-eu`, and `repair-catalog.sh` fixed them in one pass.
- **S00 at the offered load that saturated `:before`** (7 x 20 orders/min):
  - 498 of 498 commands were acknowledged, against 217 of 309 before.
  - Appends ran at 9.6/s; the payment saga completed 78 of 78 orders; I1..I6 passed.
  - S16 and S17 pass with every observation as expected.

## N6: consumer registration lands after the bundle reports healthy
- **Evidence (`:after`):** the bundle health turns UP at the handshake. `ConsumerService` logged
  `Discovering consumers ... order-query-global-apac` 25 s after that instance's
  `handshake_accepted` (11:29:36.8 to 11:30:01.7). The CINA row appeared about 19 s after its
  handshake. A catalog check right after `up --wait` therefore saw rows missing that were not lost.
- **Cause:** consumer discovery is queued on the bus business executor behind the discovery passes
  of every bundle that joined at the same time (G18: discovery on the bus threads).
- **Impact:** tooling or operators that read the catalog right after a rollout see a transient
  gap that looks like G8.
- **Lab:** `repair-catalog.sh` waits `REPAIR_SETTLE_S` (90 s) for the rows before calling anything
  missing.
- **Proposal:** expose registration completion (for example a readiness detail, or a
  `consumers_registered` event on the bundle) so health can mean "in the catalog".

## N7: consumer checkpoints carry no update time
- **Evidence:** `order-query_CatalogProjector_1_ALL` moved from `last_sequence` 1 to 116 while
  `updated_at` stayed at the row's creation time (11:31:32).
- **Cause:** `JdbcConsumerStateStore.updateExisting` sets `last_sequence` and `version` but not
  `updated_at`.
- **Impact:** the table cannot show a stalled lane (for example S16's APAC outage), which is the
  first thing an operator of the single ALL lane needs.
- **Proposal:** `updated_at = now()` in both update statements (MySQL too).

## N8: a send-only bundle is absent from the catalog
- **Evidence:** `order-gateway` (7 live instances) has presence rows in `core__bundle_instance`
  but no `core__bundle` row, on `:before` and `:after` alike.
- **Cause:** `AutoDiscoveryService` writes the bundle only when its registration carries handlers.
  This has been unchanged since 2.x.
- **Impact:** the GUI bundle list and I7 do not show a bundle that sends commands and queries.
- **Proposal:** register the bundle row whatever its handlers are.
- **Lab:** I7 skips `BUNDLES_WITHOUT_HANDLERS` in the bundle check.

## N9: remote-cell commands after G9
- **Evidence:** in `:after` S00, p50 per command is:

  | Cell | p50 | RTT to the ES primary | Round trips |
  |---|---|---|---|
  | EU | 116-267 ms | local | - |
  | AM | 718-729 ms | 80 ms | about 9 |
  | APAC | 1109-1122 ms | 150 ms | about 7.5 |

  A single create + item, unloaded, took 1.3-1.5 s (AM) and 2.3-2.5 s (APAC), against 2.4-2.5 s
  and 4.2-4.4 s on `:before`.
- **Assessment:** G9 roughly halved the WAN cost of a command, which is what took S00 below
  saturation. The append itself is about one round trip now, so most of the remaining 7-9 are
  elsewhere on the path: the `RESOURCE` lock, the aggregate event load, the `verifyHeld` query
  (N2), Hikari's borrow validation, and the separate lock-pool connection.
- **Proposal:** repeat the G9 SQL trace on `:after` to attribute them, then fold the lock and the
  load into one statement where possible.

## N10: the pre-fix dashboard hides the per-context lanes
- **Evidence (`:before`):** `/api/consumer` lists 4 entries per server, one per component, instead
  of 17 consumer ids. The `OrderProjector` entry shows the first context found
  (`order-query_OrderProjector_1_AFRICA`) with the instances of every context on that server.
- **Fix:** the working tree keys the list by consumer id, and `:after` shows all 17. The instance
  lists are still per server (G17).

## Lab defects fixed during the integration
- **L10:** the `repair-catalog.sh` check joined `core__handler.component_component_name`, a column
  N1 removed. The psql error ended the listing subshell (errexit), which read as "nothing missing",
  so the check reported "complete". Three fixes:
  - the join now follows the schema;
  - a failed listing exits 2;
  - the check waits `REPAIR_SETTLE_S` for N6 before declaring anything missing.
- **L11:** `Db.catalog` (I7) used the same column, so I7 was ERROR on `:after`. It is schema-aware now.
- **L12:** I7 expected `core__bundle` for `order-gateway` (see N8).
- **L13:** `Contexts.WILDCARD = Context.WILDCARD` broke the `FRAMEWORK_REF=e7b823fe` build, since
  that constant exists only after the fixes. It is the literal `"*"` again, pinned by
  `ContextsTest`. The build's temporary worktree was also named with a trailing `_` (the `echo`
  newline went through `tr -c`), and a stale unregistered copy was left in `.claude/worktrees/`.
  The copy is removed and the name is fixed.
- **New tool:** `scripts/integration-checks.sh` prints the whole evidence report
  (`results/_integration/<tag>/checks-raw.md`) for either image set.

## Full scenario run on `:before` (S00..S17, 2026-09-29)
Summary table and key numbers: [`results/BEFORE-SUMMARY.md`](results/BEFORE-SUMMARY.md). Each scenario is
in `results/<S>/before/`.

- **Gaps confirmed on 2.4.6:** G1 (S01), G2 and G4 (S03), G3 (S05 cross-server deadlocks; the
  S08 "no write resumed" was L24, and the re-run resumes in 7.6 s),
  G5 (S14), G7 (S04 B), G8 (every fresh start), G9 (S00, S06), G13 (S13), G14 (S10), G15 (S11),
  G25 (S07), N3 (S00), N10 (S11), and the fetch staleness gate (S15).
- **Not observed:** G10 in S12 (WATERMARK ×0.86 of DEFAULT, but at a global volume capped by G9),
  G11 in S07 and S09, G6 forks in S05 and S08, G16 (the pre-fix page gives it no chance, L15).

## N11: one partitioned cell stops the whole cluster's appends
- **Evidence (`:before` S07):**
  - APAC was cut off for 72 s (`net-apac` detached from toxiproxy and the lb).
  - EU and AM acknowledged **0 of 16** commands in that window: 10 timeouts, 5 client timeouts,
    1 `Failed to acquire advisory lock for key: es-lock`.
  - `es__events` shows no append at all from 13:24:30 to 13:25:59, which is the partition, and
    13 appends in the first 10 s after healing.
  - The `*` lanes (`OrderProjector_1_*`, `NotificationObserver_1_*`) stood still for ≥ 50 s,
    for lack of events rather than because of a lock.
- **Likely mechanism:**
  - `evento-apac` holds the CPES `es-lock` (and `RESOURCE:` locks) on its pinned lock session to
    `es-pg-eu`, reached through the WAN proxy (G3).
  - The partition drops packets silently, so Postgres keeps that backend and its advisory locks
    until TCP keepalive or healing ends the session.
  - Every other server queues behind the orphaned `es-lock`. This is G25 applied to the append
    lock instead of a consumer lock.
- **Impact:** a failure in one remote region takes writes down for every region, including the
  cell that hosts the ES primary. It is the opposite of the cell isolation the topology is meant
  to give.
- **Proposals:**
  - aggressive TCP keepalive / `tcp_user_timeout` on the lock connections and on the server
    side (`tcp_keepalives_*`);
  - hold the append lock only inside one short transaction (G9's single statement), so a
    partition can orphan at most one in-flight append;
  - repeat S07 on `:after` to measure how much of this G3 + G9 already remove.

## N12: on 2.4.6 a stale NodeLeft wipes a bundle id's consumers in every cell
- **Evidence (`:before` S04 variant B, `evento-apac` paused 60 s):**
  - Afterwards `core__consumer` had lost 10 consumer ids: `order-saga_PaymentSaga_1_<R>` for all
    7 regions, and `order-query_OrderProjector_1_{CINA,INDIA,AUSTRALIA_NZ}`. EU and AM instances
    of `order-saga` were among them, although they never touched `evento-apac`.
  - `evento-apac` logged `[BundleService] Unregister called for bundleId=order-saga`.
  - `repair-catalog.sh` could not converge in 3 passes. Pass 2 was down to 2 JVMs, but pass 3
    was back to 5, because each helper removal or restart of an instance of a shared bundle id
    raised another bundle-wide unregister. The stack had to be recreated.
- **Cause:** `BundleService.unregister` deletes by bundle id. In the per-bundle layout every
  order-query (or order-saga) deployment of every cell shares one bundle id, so a NodeLeft that
  decides "no live instance here" erases the rows of the others as well. This is G7 amplified by
  N1's bundle-wide delete.
- **Status:** fixed by the presence table (G7) and the N1 catalog key on `:after`; S04 on
  `:after` has to confirm it.

## N13: handshake supersede loop after a paused server returns
- **Evidence (`:before`, after S04 and the attempted repair):**
  - `order-saga-apac` logged `supervisor_session_failed cause=TimeoutException` 46 times in
    40 min, and `order-query-apac` 29 times.
  - On the server, `evento-apac` logged `event=child_close_timeout timeout_ms=5000` before
    `hello_will_supersede`, then `listener_error type=Hello ... SendFailedException: not in
    CONNECTED state: CLOSED` (7 in 5 min).
  - The server was idle (1–2 % CPU), yet the Docker health of both JVMs stayed `healthy`.
  - The saga's engines kept logging `bundle not ready: state=RECONNECTING/HANDSHAKING`.
- **Mechanism:**
  - A new Hello supersedes the previous session, and the server first waits up to 5 s for the
    old channel to close.
  - The client's handshake times out within that window and reconnects, so the next Hello
    supersedes the session that was just abandoned. The loop sustains itself.
- **Impact:** a bundle can stay "healthy" while it hardly ever holds a session, so its
  consumers make almost no progress.
- **Proposals:** close the superseded channel asynchronously without blocking the new Hello,
  make the client's handshake timeout larger than the server's close timeout, and report the
  session state in health. Repeat on `:after`.

## Lab defects fixed during the `:before` run
- **L14:** `repair-catalog.sh` listed handler-owning bundles only for `order-command` and
  `order-query`. The first S00 then failed I7 with `bundle order-observer has no core__handler
  rows`, right after the repair had reported the catalog complete. G8 can drop the handler rows
  of the saga and the observer too (with no error line: the `ConsumerService` created an
  "ephemeral" component first). Sagas and the observer now name their bundle; the repair fixed
  the observer in one pass. `results/S00/before-catalog-gap` keeps that first run.
- **L15:** S11 attributed a short GUI consumer list to G8, and marked G16 AS_EXPECTED on a page
  that listed no context containing `_`. The pre-fix page groups per component (N10). G16 is now
  INCONCLUSIVE when untestable, and the list check compares the page with `core__consumer`.
- **L16:** `Cluster.recreateStack()` ran `repair-catalog.sh` with a bare `bash`, which on Windows
  is WSL's `System32\bash.exe`. It exited 1 in 0.3 s (`catalog repair exit 1: []`), so every
  recreate on this host skipped the G8 workaround. The driver now uses `LAB_BASH`, then Git Bash,
  and prints the output tail when no repair line comes back.
- **L17:** S14 called G5 AS_EXPECTED whenever no key was stored twice, even when none of the
  failed first attempts had been stored. At the default 150 ms kill, none were: the command had
  not reached the append. S14 now counts `failedButStoredBeforeRetry` and is INCONCLUSIVE at 0.
  On `:before` use `S14_KILL_DELAY_MS=2000`.
- **L18:** S15 suggested `FAKETIME=+40s`. The gate compares `now - requestTimestamp`, so a clock
  ahead never looks stale (`results/S15/before-ahead40`: AS_EXPECTED); only a clock behind by more
  than 30 s trips it. The lab also had no libfaketime. `docker/faketime/Dockerfile` derives an
  image, and `S15_IMAGE` makes S15 recreate the JVM with it (`Cluster.recreateWithEnv(service,
  env, image)`). The report records the measured offset (`skewedClockMinusHostSeconds`).

## Full scenario run on `:after` (S00..S17, 2026-09-29)
Summary: [`results/AFTER-SUMMARY.md`](results/AFTER-SUMMARY.md). Side by side with `:before`:
[`results/COMPARISON.md`](results/COMPARISON.md). Each scenario is in `results/<S>/after/`.

- **Fixed and confirmed end to end:** G2 (S03 APAC 44/44, first ack 10.3 s; S04 probes 3/3), G3
  (S05 0 deadlocks; S07 EU+AM 41/41 during the partition, so N11 is gone), G4 (S14: 0 of 13 failures
  after ≥ 25 s, all `broker_changed`), G7/N12 (S04 B: 0 unregisters), G8 (0 of 7 fresh starts need the
  repair), G9 (S00 492/492, 9.35 appends/s; S06 6.55 appends/s at 300 ms against 1.65), G13 (S13: a
  foreign cluster is refused, and failover works with the guard on), G14 (S10 0/3), G15/G16/N10 (S11),
  N3 up to the append (S00 0 stored after timeout).
- **Still open:** G1 (S01), G5 (S14: 8 keys stored twice; N14), N4 (S16), the fetch staleness gate
  (S15), N15, N16. G6, G10 and G25 were not observable in this lab.

## N14: a saga's automatic retry duplicates the payment (G5 at application level)
- **Evidence (`:after`):**
  - `S03/after-run1`: `evento-apac` was killed while `order-saga-apac` had a `ProcessPaymentCommand` in
    flight. The bundle re-handshook on `evento-eu` and failed the call with `BrokerChangedException:
    outcome indeterminate ... (the request may or may not have been applied)` (the G4 fix working as
    designed). The saga's event was redelivered, the command was sent again, and the payment of
    `...-australia-nz-13` ran twice: 2 `PaymentProcessedEvent`, 2 rows of attempts.
  - `S07/after`: in the partitioned cell, `order-command-apac` executed two payments in its own
    database (reachable) while `evento-apac` could not append. Either the lock was lost
    (`Distributed lock lost for key 'RESOURCE:payment-...'`) or the reply was lost after the append.
    After healing, the saga's retries executed them again (`attempts = 2` for `cina-14` and
    `india-8`), and `india-8` has 2 `PaymentProcessedEvent`.
  - The contaminated run that was thrown away (L22/L23) showed the same pattern on
    `...sud-america-9`: the first execution's row was in `app-pg-apac`, the retry's in `app-pg-eu`.
- **Why:** the command handler's side effect happens before the append, and nothing ties the retry
  to the first attempt. G4 makes the outcome *known to be unknown* quickly, which makes the retry
  happen sooner, often while the first attempt still commits. S14 shows the same thing at the
  gateway: 9 of 13 fast-failed first attempts were stored, and 8 keys ended up twice (`:before`
  1 of 24, because a 30 s timeout left time for the original to finish first).
- **Proposal:** an idempotency key minted once by the caller (gateway request, or saga + event
  sequence), carried in the envelope, checked by the server under the aggregate lock before the
  append, and recorded with the events. The handler's side effect must be keyed on it too
  (`payments` keyed by order id here).
- **Status:** implemented after this run (branch `cluster/idempotency`, CLUSTER-READINESS G5): the
  gateway passes `X-Idempotency-Key` to `CommandGateway.sendIdempotent`, the saga's commands get
  `evento:<consumerId>:<seq>:<ordinal>` automatically, and the broker answers a stored key with the
  stored event on any server. The S07 case where the payment ran but its event could not be stored
  stays open (no key is recorded without the event). Not yet re-measured.

## N15: commands commit after their caller's deadline under `es-lock` contention
- **Evidence (`:after` S06, 300 ms RTT to the ES primary):** 31 commands timed out at the caller
  (30 s), and I1 found all 31 stored. `evento.server.requests.expired` on the three servers counted
  **0** drops at `queued`, `lock_wait`, `before_forward` and `before_append` during the run (actuator,
  first pass). The clean re-run shows the same thing: 31 timeouts, 31 stored. At 150 ms the p50 of
  **EU**, the cell of the primary, is already 1.7 s.
- **Likely mechanism:** the last deadline check is `before_append`. The append statement then waits
  inside `pg_advisory_xact_lock('es-lock')` behind every other server's append, and each remote
  holder keeps the lock for at least one RTT. The wait is unbounded and not tied to the deadline, so
  the command commits after the caller has given up. This is N3's window moved into the statement,
  and N5's unfairness makes it grow with RTT.
- **Proposal:** `SET LOCAL lock_timeout` (or `statement_timeout`) = remaining deadline for the append
  transaction, so an expired command fails with a typed error instead of committing. Longer term,
  per-aggregate appends with a sequence fence (G6) instead of one global lock.

## N16: no failback after endpoint failover (G2 fix) — cells stay split across the WAN
- **Evidence (`:after`):**
  - S04, S05, S14 (`after-kill150`) and S15 each started with all 8 APAC JVMs
    (`order-command-apac`, the query, saga and global deployments, the 3 gateways) still on
    `evento-eu`, and S14 (`after`) with 7 EU JVMs still on `evento-am`. In each case the scenario
    before had restarted the killed server and waited for it to be healthy
    (`placementRestoredAtStart` in their reports).
  - After S13 variant A (`evento-eu` killed and restarted), 7 EU JVMs were still on `evento-am`
    (`A.bundlesStillRemoteAfterEuReturned`).
  - Consequences seen (`results/S13/after-n16-evidence`): `gateway-europa`, recreated onto the
    returned `evento-eu`, got `no handler for CreateOrderCommand` 0/3, because `order-command-eu`
    was on `evento-am` (G1). The first S05 pass measured its EU-vs-APAC contention inside one server
    (p50 0.49 s, against 0.93 s once placement was restored).
- **Cause:** `ConnectionSupervisor` moves to the next endpoint after a failed session and goes back
  to the first only at the next disconnect. There is no background probe (the G2 plan had one:
  "periodically re-tries index 0 to fail back").
- **Impact:** after any server restart, a cell's traffic crosses the WAN indefinitely (latency,
  toxiproxy cost, exposure to other cells' faults). With G1, handlers and callers of one cell can end
  up on different servers. The cell architecture assumes they never do.
- **Proposals:** a fail-back probe to the preferred endpoint with jitter and a drain of in-flight
  calls; or have the server shed bundles whose preferred server is back (a "go home" hint in
  `Welcome`/presence). Until then: restart the cell's JVMs after a server returns. The lab does
  this before every scenario (L22).

## N17: the ES promotion outage is longer on `:after`
- **Evidence (S08, with L24 fixed, one run each):** the first ack after `es-pg-eu` was killed and
  `es-pg-am` promoted came at 7.6 s on `:before` and 15.1 s on `:after`. After the kill, `:after`
  acknowledged 103/106 (p50 874 ms), `:before` 105/106 (p50 2.4 s).
- **Hypothesis (not verified):** `:after` has two pools per server (data and the G3 lock pool).
  Both must discover that the old primary is gone (`connectTimeout=10`) before
  `targetServerType=primary` finds `es-pg-am`, and `verifyHeld` adds one query per locked append.
- **Next:** repeat S08 3× on each image set; log pool acquisition timing around the promotion.

## Lab defects fixed during the `:after` run
- **L19 (S12):** the per-event CPU ratio compared totals that were mostly idle cost (polling,
  heartbeats: ~14 ms/s against ~12 ms/s under load), so it swung from ×0.86 (`:before`) to ×1.62
  (`:after`) on noise. Each phase now measures and subtracts an idle rate. A difference below
  `S12_NOISE_FLOOR_MS` (1000) is INCONCLUSIVE, and the report says why. Neither image set shows any
  load-attributable CPU for 250–750 foreign events: with CPES the gaps are too small for G10 to show.
- **L20 (S07):** a lane whose checkpoint does not move was a "stall" even with nothing to consume
  (the ALL lane with no price set; on `:before` the `*` lanes while N11 had stopped every append).
  A lane now stalls only while it is behind the head of what it consumes (`Db.esHeadOfContext`).
- **L21 (S13, S03):** S13 ran only the 2.4.6 single-id guard, which refuses failover by contract, so
  G13 could never pass. Variants C (a bundle expects a foreign cluster: must be refused) and B
  (cluster-name guard only: failover must work) now decide G13. S03's G4 check looked only at gateway
  commands, which at 4/min are rarely in flight at the kill; it now also counts
  `BrokerChangedException` / `RequestTimeoutException` in the APAC JVMs.
- **L22 (all scenarios):** because of N16, every scenario after the first server kill ran on the
  wrong placement. `AbstractScenario` now reads each JVM's last `handshake_complete`, restarts the
  misplaced ones, and records `placementRestoredAtStart`. On 2.4.6 this does nothing: bundles never
  fail over, and the log line carries no server id.
- **L23 (all scenarios):** two runs overlapped once (a stopped Gradle driver kept going in the
  background). S07's heal then failed to re-attach the lb to `net-apac` ("address already in use"),
  and the lb served every `X-Region` from `gateway-cina` with 200s. Every event of every region got
  context CINA, and no check noticed. `AbstractScenario` now checks that each region's `/whoami`
  comes from its own gateway and recreates the lb once, and S07's heal recreates the lb when the
  re-attach fails. Operational rule: never run two scenario drivers at once; stop one by killing the
  whole process tree.
- **L24 (S08):** `pg_basebackup` copies the primary's `postgresql.auto.conf`, including
  `synchronous_standby_names = 'FIRST 1 (es_pg_am)'`. A promoted `es-pg-am` then waited on every
  commit for a sync standby named `es_pg_am`, which is itself. The `:before` S08 "no write resumed
  (G3)" came from this. `replica-entrypoint.sh` now appends `synchronous_standby_names = ''` on the
  replicas. S08 was re-run on both image sets (`before-l24` / `after-l24` keep the old runs).
