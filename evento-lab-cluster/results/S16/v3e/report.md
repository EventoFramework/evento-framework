# S16: Global ALL events: single lane, fan-out to every regional database

- Label: `v3e`
- Gaps exercised: G24 (one lane per bundleId+context), G11, N4
- Started: 2026-10-22T15:39:43.779395200Z, finished: 2026-10-22T15:41:06.334881300Z (82 s)
- Verdicts: {AS_EXPECTED=7}

## Parameters

- orderIdPrefix: `s16-v3e-d4ffed-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- healedAt: `2026-10-22T15:40:47.115482200Z`
- laneHolderBeforeKill: `order-query-global-eu`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| ALL event reaches every regional database exactly once | **AS_EXPECTED** | each price in app-pg-eu, -am and -apac with its event's sequence, applied_events = 1, context ALL |  | 5 events x 3 databases, each applied once |
| only the global lane writes the catalog | **AS_EXPECTED** | every catalog_price row written by an order-query-global-* replica |  | writers {order-query-global-eu=15} |
| one ALL lane cluster-wide | **AS_EXPECTED** | exactly one CatalogProjector checkpoint, consumers/order-query_CatalogProjector_1_ALL (the replicas share it) |  | [consumers/order-query_CatalogProjector_1_ALL=571] |
| each ALL event fanned out by one replica | **AS_EXPECTED** | every sequence appears in exactly one replica's event=lab_fanout log (standby idle meanwhile) |  | no sequence processed by both replicas |
| healthy regions during APAC's database outage | **AS_EXPECTED** | the single ALL lane stalls for every region: EU and AM get at most the head event, the checkpoint stays before it (known trade-off of one lane with N targets) |  | EU/AM received {AM=1, EU=1} of 4 ALL events; lane checkpoint 571 -> 571 |
| convergence after healing, no duplicates | **AS_EXPECTED** | every ALL event in all three databases, applied_events = 1 (replays refused by the sequence guard) |  | converged; no duplicate |
| standby takes over the ALL lane | **AS_EXPECTED** | after the holder dies, order-query-global-apac fans out the next events to all three databases |  | written by {order-query-global-apac=9} |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "fanOutSeconds" : 2.102,
  "fanOutWriters" : {
    "order-query-global-eu" : 15
  },
  "catalogConsumerStates" : [ "consumers/order-query_CatalogProjector_1_ALL" ],
  "fanOutEventsByReplica" : {
    "order-query-global-apac" : [ ],
    "order-query-global-eu" : [ 567, 568, 569, 570, 571 ]
  },
  "outageEvents" : 4,
  "outageRowsInHealthyCells" : {
    "AM" : 1,
    "EU" : 1
  },
  "laneCheckpointBeforeOutage" : 571,
  "laneCheckpointDuringOutage" : 571,
  "secondsToConvergeAfterHeal" : 0.044,
  "replaysAbsorbed" : 6,
  "takeoverSeconds" : 9.616,
  "logMarkers" : {
    "order-query-global-eu" : {
      "event=lab_fanout" : 12,
      "FanOutIncompleteException" : 3,
      "Event processing failed" : 3
    },
    "order-query-global-apac" : {
      "event=lab_fanout" : 3
    }
  }
}
```

## Notes

- The lane is one (one consumer id, one lock row); which replica holds it can change between fetch cycles (N4: the JDBC consumer lock is taken per cycle, not sticky).
- catalog_price in APAC not readable: Non riesco a trovare un server con il «targetServerType» specificato: primary
- A per-target lane (one consumer per regional database) would keep EU and AM current during an APAC outage, at the price of N lanes to operate; the lab keeps one lane on purpose.

## Timeline

- +8,1 s: scenario start
- +11,2 s: sent 5/5 ALL events (s16-v3e-d4ffed-sku-a*)
- +14,4 s: stopped app-pg-apac (APAC's regional database)
- +16,2 s: sent 4/4 ALL events (s16-v3e-d4ffed-sku-b*)
- +63,3 s: app-pg-apac back, ready=true
- +63,9 s: killed the lane holder order-query-global-eu
- +65,0 s: sent 3/3 ALL events (s16-v3e-d4ffed-sku-c*)
- +73,3 s: cleanup start
- +82,6 s: cleanup done

### catalog_price after the fan-out

```
EU s16-v3e-d4ffed-sku-a0 seq=567 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-v3e-d4ffed-sku-a1 seq=568 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-v3e-d4ffed-sku-a2 seq=569 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-v3e-d4ffed-sku-a3 seq=570 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-v3e-d4ffed-sku-a4 seq=571 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-v3e-d4ffed-sku-a0 seq=567 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-v3e-d4ffed-sku-a1 seq=568 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-v3e-d4ffed-sku-a2 seq=569 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-v3e-d4ffed-sku-a3 seq=570 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-v3e-d4ffed-sku-a4 seq=571 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-v3e-d4ffed-sku-a0 seq=567 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-v3e-d4ffed-sku-a1 seq=568 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-v3e-d4ffed-sku-a2 seq=569 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-v3e-d4ffed-sku-a3 seq=570 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-v3e-d4ffed-sku-a4 seq=571 applied=1 stale=0 ctx=ALL by=order-query-global-eu
```

### ALL lane checkpoint (every 3 s)

```
2026-10-22T15:39:57.643089300Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:00.724985Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:03.795063500Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:06.858958400Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:09.920090200Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:12.986572700Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:16.048147500Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:19.121553900Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:22.188080900Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:25.250679100Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:28.314406200Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:31.390968600Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:34.459014800Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:37.578799800Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:40.648646Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-22T15:40:43.743013500Z consumers/order-query_CatalogProjector_1_ALL=572
2026-10-22T15:40:46.819Z consumers/order-query_CatalogProjector_1_ALL=575
```

### log excerpts: order-query-global-eu

```
2026-10-22T15:39:55.070Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=567 sku=s16-v3e-d4ffed-sku-a0 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-22T15:39:55.616Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=568 sku=s16-v3e-d4ffed-sku-a1 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-22T15:39:56.028Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=569 sku=s16-v3e-d4ffed-sku-a2 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=APPLIED, am=APPLIED}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
2026-10-22T15:40:08.549Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 1)
2026-10-22T15:40:20.899Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 2)
2026-10-22T15:40:33.245Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 3)
```

### log excerpts: order-query-global-apac

```
2026-10-22T15:40:53.369Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=576 sku=s16-v3e-d4ffed-sku-c0 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-22T15:40:54.665Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=577 sku=s16-v3e-d4ffed-sku-c1 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-22T15:40:55.931Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=578 sku=s16-v3e-d4ffed-sku-c2 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
```

### stack actions

```
2026-10-22T15:39:57.643089300Z docker stop app-pg-apac
2026-10-22T15:40:40.149682600Z docker start app-pg-apac
2026-10-22T15:40:47.355336700Z docker kill order-query-global-eu
2026-10-22T15:40:57.196932100Z docker start order-query-global-eu (restore)
```

