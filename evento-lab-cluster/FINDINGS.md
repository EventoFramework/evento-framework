# evento-lab-cluster: findings (images `:before` = evento 2.4.6 and `:after` = the cluster fixes, 2026-09-29)

| Id | What | Severity | Lab status |
|---|---|---|---|
| N1 | `core__component` keyed by simple class name across bundles | High (catalog) | workaround removed: regions are contexts of one class (lab/bundles) |
| G8 | discovery / consumer-registration insert race | High (catalog) | fixed: `:after` starts complete with `REPAIR_CATALOG=false`; `:before` still needs `repair-catalog.sh` (integration run) |
| G9 (+G3) | about 14 sequential WAN round trips per command from a remote cell | High (throughput) | `:after` S00: 498/498 acked, p50 AM 0.72 s, APAC 1.12 s; residual in N9 |
| N2 | connection liveness probes on the command path, each one a WAN round trip | Medium | recorded |
| N3 (+G4/G5) | commands still executed after the caller timed out; saga retries duplicate the payment | High (correctness) | recorded |
| N4 (+G24/G12) | the single global consumer lane hops between cells and projects over the WAN | Medium | recorded; still hops on `:after` (lock holder EU/APAC alternates) |
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
