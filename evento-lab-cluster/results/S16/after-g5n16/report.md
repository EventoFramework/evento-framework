# S16: Global ALL events: single lane, fan-out to every regional database

- Label: `after-g5n16`
- Gaps exercised: G24 (one lane per bundleId+context), G11, N4
- Started: 2026-09-29T21:08:46.718548600Z, finished: 2026-09-29T21:10:40.910588300Z (114 s)
- Verdicts: {AS_EXPECTED=7}

## Parameters

- orderIdPrefix: `s16-after-g5n16-86127c-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- healedAt: `2026-09-29T21:09:59.970486500Z`
- laneHolderBeforeKill: `order-query-global-apac`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| ALL event reaches every regional database exactly once | **AS_EXPECTED** | each price in app-pg-eu, -am and -apac with its event's sequence, applied_events = 1, context ALL |  | 5 events x 3 databases, each applied once |
| only the global lane writes the catalog | **AS_EXPECTED** | every catalog_price row written by an order-query-global-* replica |  | writers {order-query-global-apac=9, order-query-global-eu=6} |
| one ALL lane cluster-wide | **AS_EXPECTED** | exactly one CatalogProjector checkpoint, consumers/order-query_CatalogProjector_1_ALL (the replicas share it) |  | [consumers/order-query_CatalogProjector_1_ALL=5] |
| each ALL event fanned out by one replica | **AS_EXPECTED** | every sequence appears in exactly one replica's event=lab_fanout log (standby idle meanwhile) |  | no sequence processed by both replicas |
| healthy regions during APAC's database outage | **AS_EXPECTED** | the single ALL lane stalls for every region: EU and AM get at most the head event, the checkpoint stays before it (known trade-off of one lane with N targets) |  | EU/AM received {AM=1, EU=1} of 4 ALL events; lane checkpoint 5 -> 5 |
| convergence after healing, no duplicates | **AS_EXPECTED** | every ALL event in all three databases, applied_events = 1 (replays refused by the sequence guard) |  | converged; no duplicate |
| standby takes over the ALL lane | **AS_EXPECTED** | after the holder dies, order-query-global-eu fans out the next events to all three databases |  | written by {order-query-global-eu=9} |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "fanOutSeconds" : 8.149,
  "fanOutWriters" : {
    "order-query-global-apac" : 9,
    "order-query-global-eu" : 6
  },
  "catalogConsumerStates" : [ "consumers/order-query_CatalogProjector_1_ALL" ],
  "fanOutEventsByReplica" : {
    "order-query-global-apac" : [ 2, 3, 4 ],
    "order-query-global-eu" : [ 1, 5 ]
  },
  "outageEvents" : 4,
  "outageRowsInHealthyCells" : {
    "AM" : 1,
    "EU" : 1
  },
  "laneCheckpointBeforeOutage" : 5,
  "laneCheckpointDuringOutage" : 5,
  "secondsToConvergeAfterHeal" : 16.254,
  "replaysAbsorbed" : 8,
  "takeoverSeconds" : 4.38,
  "logMarkers" : {
    "order-query-global-eu" : {
      "event=lab_fanout" : 10,
      "FanOutIncompleteException" : 3,
      "Event processing failed" : 3
    },
    "order-query-global-apac" : {
      "event=lab_fanout" : 6,
      "FanOutIncompleteException" : 1,
      "Event processing failed" : 1
    }
  }
}
```

## Notes

- The lane is one (one consumer id, one lock row); which replica holds it can change between fetch cycles (N4: the JDBC consumer lock is taken per cycle, not sticky).
- catalog_price in APAC not readable: Non riesco a trovare un server con il «targetServerType» specificato: primary
- A per-target lane (one consumer per regional database) would keep EU and AM current during an APAC outage, at the price of N lanes to operate; the lab keeps one lane on purpose.

## Timeline

- +7,7 s: scenario start
- +14,0 s: sent 5/5 ALL events (s16-after-g5n16-86127c-sku-a*)
- +23,2 s: stopped app-pg-apac (APAC's regional database)
- +26,2 s: sent 4/4 ALL events (s16-after-g5n16-86127c-sku-b*)
- +73,3 s: app-pg-apac back, ready=true
- +90,0 s: killed the lane holder order-query-global-apac
- +91,9 s: sent 3/3 ALL events (s16-after-g5n16-86127c-sku-c*)
- +94,3 s: cleanup start
- +114,2 s: cleanup done

### catalog_price after the fan-out

```
EU s16-after-g5n16-86127c-sku-a0 seq=1 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-after-g5n16-86127c-sku-a1 seq=2 applied=1 stale=0 ctx=ALL by=order-query-global-apac
EU s16-after-g5n16-86127c-sku-a2 seq=3 applied=1 stale=0 ctx=ALL by=order-query-global-apac
EU s16-after-g5n16-86127c-sku-a3 seq=4 applied=1 stale=0 ctx=ALL by=order-query-global-apac
EU s16-after-g5n16-86127c-sku-a4 seq=5 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-after-g5n16-86127c-sku-a0 seq=1 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-after-g5n16-86127c-sku-a1 seq=2 applied=1 stale=0 ctx=ALL by=order-query-global-apac
AM s16-after-g5n16-86127c-sku-a2 seq=3 applied=1 stale=0 ctx=ALL by=order-query-global-apac
AM s16-after-g5n16-86127c-sku-a3 seq=4 applied=1 stale=0 ctx=ALL by=order-query-global-apac
AM s16-after-g5n16-86127c-sku-a4 seq=5 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-after-g5n16-86127c-sku-a0 seq=1 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-after-g5n16-86127c-sku-a1 seq=2 applied=1 stale=0 ctx=ALL by=order-query-global-apac
APAC s16-after-g5n16-86127c-sku-a2 seq=3 applied=1 stale=0 ctx=ALL by=order-query-global-apac
APAC s16-after-g5n16-86127c-sku-a3 seq=4 applied=1 stale=0 ctx=ALL by=order-query-global-apac
APAC s16-after-g5n16-86127c-sku-a4 seq=5 applied=1 stale=0 ctx=ALL by=order-query-global-eu
```

### ALL lane checkpoint (every 3 s)

```
2026-09-29T21:09:09.381916300Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:12.469053900Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:15.540058200Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:18.634920300Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:21.709921Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:24.791474600Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:27.867684900Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:30.954448900Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:34.027483Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:37.091728500Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:40.156509500Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:43.222343100Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:46.290506400Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:49.353788300Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:52.419855700Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:55.487295700Z consumers/order-query_CatalogProjector_1_ALL=5
2026-09-29T21:09:58.561099400Z consumers/order-query_CatalogProjector_1_ALL=7
2026-09-29T21:10:01.627352700Z consumers/order-query_CatalogProjector_1_ALL=7
2026-09-29T21:10:04.706343200Z consumers/order-query_CatalogProjector_1_ALL=7
2026-09-29T21:10:07.769763100Z consumers/order-query_CatalogProjector_1_ALL=7
2026-09-29T21:10:10.839018600Z consumers/order-query_CatalogProjector_1_ALL=7
2026-09-29T21:10:13.913811900Z consumers/order-query_CatalogProjector_1_ALL=8
```

### log excerpts: order-query-global-eu

```
2026-09-29T21:08:57.772Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=1 sku=s16-after-g5n16-86127c-sku-a0 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-09-29T21:09:07.469Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=5 sku=s16-after-g5n16-86127c-sku-a4 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-09-29T21:09:20.834Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=6 sku=s16-after-g5n16-86127c-sku-b0 outcomes={eu=APPLIED, am=APPLIED} failed=[apac]
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=APPLIED, am=APPLIED}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
2026-09-29T21:09:20.835Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 1)
2026-09-29T21:09:33.185Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 2)
2026-09-29T21:09:45.539Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 3)
```

### log excerpts: order-query-global-apac

```
2026-09-29T21:09:02.791Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=2 sku=s16-after-g5n16-86127c-sku-a1 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-09-29T21:09:04.061Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=3 sku=s16-after-g5n16-86127c-sku-a2 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-09-29T21:09:05.351Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=4 sku=s16-after-g5n16-86127c-sku-a3 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=APPLIED, am=APPLIED}
2026-09-29T21:10:09.462Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 1)
```

### stack actions

```
2026-09-29T21:09:09.382457Z docker stop app-pg-apac
2026-09-29T21:09:53.100872400Z docker start app-pg-apac
2026-09-29T21:10:16.449864500Z docker kill order-query-global-apac
2026-09-29T21:10:21.056499800Z docker start order-query-global-apac (restore)
```

