# S16: Global ALL events: single lane, fan-out to every regional database

- Label: `after-versioning`
- Gaps exercised: G24 (one lane per bundleId+context), G11, N4
- Started: 2026-10-15T23:56:25.800202600Z, finished: 2026-10-15T23:57:58.710368700Z (92 s)
- Verdicts: {AS_EXPECTED=7}

## Parameters

- orderIdPrefix: `s16-after-versioning-9dd350-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- healedAt: `2026-10-15T23:57:34.997936800Z`
- laneHolderBeforeKill: `order-query-global-apac`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| ALL event reaches every regional database exactly once | **AS_EXPECTED** | each price in app-pg-eu, -am and -apac with its event's sequence, applied_events = 1, context ALL |  | 5 events x 3 databases, each applied once |
| only the global lane writes the catalog | **AS_EXPECTED** | every catalog_price row written by an order-query-global-* replica |  | writers {order-query-global-apac=6, order-query-global-eu=9} |
| one ALL lane cluster-wide | **AS_EXPECTED** | exactly one CatalogProjector checkpoint, consumers/order-query_CatalogProjector_1_ALL (the replicas share it) |  | [consumers/order-query_CatalogProjector_1_ALL=571] |
| each ALL event fanned out by one replica | **AS_EXPECTED** | every sequence appears in exactly one replica's event=lab_fanout log (standby idle meanwhile) |  | no sequence processed by both replicas |
| healthy regions during APAC's database outage | **AS_EXPECTED** | the single ALL lane stalls for every region: EU and AM get at most the head event, the checkpoint stays before it (known trade-off of one lane with N targets) |  | EU/AM received {AM=1, EU=1} of 4 ALL events; lane checkpoint 571 -> 571 |
| convergence after healing, no duplicates | **AS_EXPECTED** | every ALL event in all three databases, applied_events = 1 (replays refused by the sequence guard) |  | converged; no duplicate |
| standby takes over the ALL lane | **AS_EXPECTED** | after the holder dies, order-query-global-eu fans out the next events to all three databases |  | written by {order-query-global-eu=9} |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "fanOutSeconds" : 8.159,
  "fanOutWriters" : {
    "order-query-global-apac" : 6,
    "order-query-global-eu" : 9
  },
  "catalogConsumerStates" : [ "consumers/order-query_CatalogProjector_1_ALL" ],
  "fanOutEventsByReplica" : {
    "order-query-global-apac" : [ 567, 568 ],
    "order-query-global-eu" : [ 569, 570, 571 ]
  },
  "outageEvents" : 4,
  "outageRowsInHealthyCells" : {
    "AM" : 1,
    "EU" : 1
  },
  "laneCheckpointBeforeOutage" : 571,
  "laneCheckpointDuringOutage" : 571,
  "secondsToConvergeAfterHeal" : 0.046,
  "replaysAbsorbed" : 6,
  "takeoverSeconds" : 3.543,
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

- +7,8 s: scenario start
- +11,1 s: sent 5/5 ALL events (s16-after-versioning-9dd350-sku-a*)
- +20,2 s: stopped app-pg-apac (APAC's regional database)
- +22,1 s: sent 4/4 ALL events (s16-after-versioning-9dd350-sku-b*)
- +69,2 s: app-pg-apac back, ready=true
- +69,8 s: killed the lane holder order-query-global-apac
- +70,9 s: sent 3/3 ALL events (s16-after-versioning-9dd350-sku-c*)
- +73,2 s: cleanup start
- +92,9 s: cleanup done

### catalog_price after the fan-out

```
EU s16-after-versioning-9dd350-sku-a0 seq=567 applied=1 stale=0 ctx=ALL by=order-query-global-apac
EU s16-after-versioning-9dd350-sku-a1 seq=568 applied=1 stale=0 ctx=ALL by=order-query-global-apac
EU s16-after-versioning-9dd350-sku-a2 seq=569 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-after-versioning-9dd350-sku-a3 seq=570 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-after-versioning-9dd350-sku-a4 seq=571 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-after-versioning-9dd350-sku-a0 seq=567 applied=1 stale=0 ctx=ALL by=order-query-global-apac
AM s16-after-versioning-9dd350-sku-a1 seq=568 applied=1 stale=0 ctx=ALL by=order-query-global-apac
AM s16-after-versioning-9dd350-sku-a2 seq=569 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-after-versioning-9dd350-sku-a3 seq=570 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-after-versioning-9dd350-sku-a4 seq=571 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-after-versioning-9dd350-sku-a0 seq=567 applied=1 stale=0 ctx=ALL by=order-query-global-apac
APAC s16-after-versioning-9dd350-sku-a1 seq=568 applied=1 stale=0 ctx=ALL by=order-query-global-apac
APAC s16-after-versioning-9dd350-sku-a2 seq=569 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-after-versioning-9dd350-sku-a3 seq=570 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-after-versioning-9dd350-sku-a4 seq=571 applied=1 stale=0 ctx=ALL by=order-query-global-eu
```

### ALL lane checkpoint (every 3 s)

```
2026-10-15T23:56:45.604758700Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:56:48.679513600Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:56:51.760621600Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:56:54.823556600Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:56:57.894595Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:00.954355500Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:04.023330400Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:07.086080800Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:10.145178500Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:13.210391600Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:16.269320600Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:19.328313200Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:22.387315400Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:25.453908900Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:28.542481100Z consumers/order-query_CatalogProjector_1_ALL=571
2026-10-15T23:57:31.621715800Z consumers/order-query_CatalogProjector_1_ALL=574
2026-10-15T23:57:34.682830Z consumers/order-query_CatalogProjector_1_ALL=575
```

### log excerpts: order-query-global-eu

```
2026-10-15T23:56:44.013Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=569 sku=s16-after-versioning-9dd350-sku-a2 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-15T23:56:44.526Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=570 sku=s16-after-versioning-9dd350-sku-a3 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-15T23:56:44.941Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=571 sku=s16-after-versioning-9dd350-sku-a4 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=APPLIED, am=APPLIED}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
2026-10-15T23:56:57.280Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 1)
2026-10-15T23:57:09.621Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 2)
2026-10-15T23:57:21.971Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 3)
```

### log excerpts: order-query-global-apac

```
2026-10-15T23:56:38.630Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=567 sku=s16-after-versioning-9dd350-sku-a0 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-15T23:56:39.918Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=568 sku=s16-after-versioning-9dd350-sku-a1 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-15T23:57:32.532Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=575 sku=s16-after-versioning-9dd350-sku-b3 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
```

### stack actions

```
2026-10-15T23:56:45.604758700Z docker stop app-pg-apac
2026-10-15T23:57:28.055982300Z docker start app-pg-apac
2026-10-15T23:57:35.293392Z docker kill order-query-global-apac
2026-10-15T23:57:39.055755100Z docker start order-query-global-apac (restore)
```

