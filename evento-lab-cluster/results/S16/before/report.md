# S16: Global ALL events: single lane, fan-out to every regional database

- Label: `before`
- Gaps exercised: G24 (one lane per bundleId+context), G11, N4
- Started: 2026-09-29T12:26:54.641492200Z, finished: 2026-09-29T12:28:16.348330700Z (81 s)
- Verdicts: {AS_EXPECTED=7}

## Parameters

- orderIdPrefix: `s16-before-781ca2-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- healedAt: `2026-09-29T12:27:57.115264700Z`
- laneHolderBeforeKill: `order-query-global-eu`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| ALL event reaches every regional database exactly once | **AS_EXPECTED** | each price in app-pg-eu, -am and -apac with its event's sequence, applied_events = 1, context ALL |  | 5 events x 3 databases, each applied once |
| only the global lane writes the catalog | **AS_EXPECTED** | every catalog_price row written by an order-query-global-* replica |  | writers {order-query-global-eu=15} |
| one ALL lane cluster-wide | **AS_EXPECTED** | exactly one CatalogProjector checkpoint, consumers/order-query_CatalogProjector_1_ALL (the replicas share it) |  | [consumers/order-query_CatalogProjector_1_ALL=723] |
| each ALL event fanned out by one replica | **AS_EXPECTED** | every sequence appears in exactly one replica's event=lab_fanout log (standby idle meanwhile) |  | no sequence processed by both replicas |
| healthy regions during APAC's database outage | **AS_EXPECTED** | the single ALL lane stalls for every region: EU and AM get at most the head event, the checkpoint stays before it (known trade-off of one lane with N targets) |  | EU/AM received {AM=1, EU=1} of 4 ALL events; lane checkpoint 723 -> 723 |
| convergence after healing, no duplicates | **AS_EXPECTED** | every ALL event in all three databases, applied_events = 1 (replays refused by the sequence guard) |  | converged; no duplicate |
| standby takes over the ALL lane | **AS_EXPECTED** | after the holder dies, order-query-global-apac fans out the next events to all three databases |  | written by {order-query-global-apac=9} |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "fanOutSeconds" : 2.077,
  "fanOutWriters" : {
    "order-query-global-eu" : 15
  },
  "catalogConsumerStates" : [ "consumers/order-query_CatalogProjector_1_ALL" ],
  "fanOutEventsByReplica" : {
    "order-query-global-apac" : [ ],
    "order-query-global-eu" : [ 719, 720, 721, 722, 723 ]
  },
  "outageEvents" : 4,
  "outageRowsInHealthyCells" : {
    "AM" : 1,
    "EU" : 1
  },
  "laneCheckpointBeforeOutage" : 723,
  "laneCheckpointDuringOutage" : 723,
  "secondsToConvergeAfterHeal" : 2.071,
  "replaysAbsorbed" : 6,
  "takeoverSeconds" : 7.678,
  "logMarkers" : {
    "order-query-global-eu" : {
      "event=lab_fanout" : 7
    },
    "order-query-global-apac" : {
      "event=lab_fanout" : 8,
      "FanOutIncompleteException" : 3,
      "Event processing failed" : 3
    }
  }
}
```

## Notes

- The lane is one (one consumer id, one lock row); which replica holds it can change between fetch cycles (N4: the JDBC consumer lock is taken per cycle, not sticky).
- catalog_price in APAC not readable: Non riesco a trovare un server con il «targetServerType» specificato: primary
- A per-target lane (one consumer per regional database) would keep EU and AM current during an APAC outage, at the price of N lanes to operate; the lab keeps one lane on purpose.

## Timeline

- +5,9 s: scenario start
- +9,9 s: sent 5/5 ALL events (s16-before-781ca2-sku-a*)
- +13,1 s: stopped app-pg-apac (APAC's regional database)
- +15,4 s: sent 4/4 ALL events (s16-before-781ca2-sku-b*)
- +62,5 s: app-pg-apac back, ready=true
- +65,0 s: killed the lane holder order-query-global-eu
- +66,2 s: sent 3/3 ALL events (s16-before-781ca2-sku-c*)
- +72,6 s: cleanup start
- +81,7 s: cleanup done

### catalog_price after the fan-out

```
EU s16-before-781ca2-sku-a0 seq=719 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-before-781ca2-sku-a1 seq=720 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-before-781ca2-sku-a2 seq=721 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-before-781ca2-sku-a3 seq=722 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-before-781ca2-sku-a4 seq=723 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-before-781ca2-sku-a0 seq=719 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-before-781ca2-sku-a1 seq=720 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-before-781ca2-sku-a2 seq=721 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-before-781ca2-sku-a3 seq=722 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-before-781ca2-sku-a4 seq=723 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-before-781ca2-sku-a0 seq=719 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-before-781ca2-sku-a1 seq=720 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-before-781ca2-sku-a2 seq=721 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-before-781ca2-sku-a3 seq=722 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-before-781ca2-sku-a4 seq=723 applied=1 stale=0 ctx=ALL by=order-query-global-eu
```

### ALL lane checkpoint (every 3 s)

```
2026-09-29T12:27:07.104150600Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:10.262943200Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:13.358275600Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:16.430935500Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:19.509509900Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:22.578595200Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:25.656730400Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:28.749646100Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:31.836223500Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:34.941655600Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:38.029746700Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:41.102625800Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:44.176238100Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:47.252918500Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:50.350290600Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:53.434865400Z consumers/order-query_CatalogProjector_1_ALL=723
2026-09-29T12:27:56.515986300Z consumers/order-query_CatalogProjector_1_ALL=725
```

### log excerpts: order-query-global-eu

```
2026-09-29T12:27:03.641Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=719 sku=s16-before-781ca2-sku-a0 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-09-29T12:27:05.308Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=720 sku=s16-before-781ca2-sku-a1 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-09-29T12:27:05.721Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=721 sku=s16-before-781ca2-sku-a2 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
```

### log excerpts: order-query-global-apac

```
2026-09-29T12:27:22.610Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=724 sku=s16-before-781ca2-sku-b0 outcomes={eu=APPLIED, am=APPLIED} failed=[apac]
2026-09-29T12:27:36.317Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=724 sku=s16-before-781ca2-sku-b0 outcomes={eu=STALE, am=STALE} failed=[apac]
2026-09-29T12:27:49.988Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=724 sku=s16-before-781ca2-sku-b0 outcomes={eu=STALE, am=STALE} failed=[apac]
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=APPLIED, am=APPLIED}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
2026-09-29T12:27:22.612Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 1)
2026-09-29T12:27:36.318Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 2)
2026-09-29T12:27:49.988Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 3)
```

### stack actions

```
2026-09-29T12:27:07.104684900Z docker stop app-pg-apac
2026-09-29T12:27:50.181115400Z docker start app-pg-apac
2026-09-29T12:27:59.382927500Z docker kill order-query-global-eu
2026-09-29T12:28:07.278812100Z docker start order-query-global-eu (restore)
```

