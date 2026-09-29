# evento-lab-cluster: findings (framework at HEAD, evento-server 2.4.6, images `:before`, 2026-09-29)

| Id | What | Severity | Lab status |
|---|---|---|---|
| N1 | `core__component` keyed by simple class name across bundles | High (catalog) | workaround removed: regions are contexts of one class (lab/bundles) |
| G8 | discovery / consumer-registration insert race | High (catalog) | worked around by `repair-catalog.sh` |
| G9 (+G3) | about 14 sequential WAN round trips per command from a remote cell | High (throughput) | recorded |
| N2 | connection liveness probes on the command path, each one a WAN round trip | Medium | recorded |
| N3 (+G4/G5) | commands still executed after the caller timed out; saga retries duplicate the payment | High (correctness) | recorded |
| N4 (+G24/G12) | the single global consumer lane hops between cells and projects over the WAN | Medium | recorded |
| N5 | the CPES `es-lock` favours the cell nearest the ES primary | Medium | recorded |
| L7 | toxiproxy OOM-killed at 64m during base backups | Blocker | fixed (256m) |
| L8 | no restart policy; no healthcheck on cell-core and saga | Medium | fixed |
| L9 | `LAB_TAG` defaulted to `dev` while the stack ran `before` | Medium | fixed |

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
