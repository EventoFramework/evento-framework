# S16: Global ALL events: single lane, fan-out to every regional database

- Label: `after-query`
- Gaps exercised: G24 (one lane per bundleId+context), G11, N4
- Started: 2026-10-15T13:27:30.955455Z, finished: 2026-10-15T13:29:08.444574600Z (97 s)
- Verdicts: {AS_EXPECTED=7}

## Parameters

- orderIdPrefix: `s16-after-query-d23dbb-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- healedAt: `2026-10-15T13:28:40.521252600Z`
- laneHolderBeforeKill: `order-query-global-apac`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| ALL event reaches every regional database exactly once | **AS_EXPECTED** | each price in app-pg-eu, -am and -apac with its event's sequence, applied_events = 1, context ALL |  | 5 events x 3 databases, each applied once |
| only the global lane writes the catalog | **AS_EXPECTED** | every catalog_price row written by an order-query-global-* replica |  | writers {order-query-global-apac=6, order-query-global-eu=9} |
| one ALL lane cluster-wide | **AS_EXPECTED** | exactly one CatalogProjector checkpoint, consumers/order-query_CatalogProjector_1_ALL (the replicas share it) |  | [consumers/order-query_CatalogProjector_1_ALL=575] |
| each ALL event fanned out by one replica | **AS_EXPECTED** | every sequence appears in exactly one replica's event=lab_fanout log (standby idle meanwhile) |  | no sequence processed by both replicas |
| healthy regions during APAC's database outage | **AS_EXPECTED** | the single ALL lane stalls for every region: EU and AM get at most the head event, the checkpoint stays before it (known trade-off of one lane with N targets) |  | EU/AM received {AM=1, EU=1} of 4 ALL events; lane checkpoint 575 -> 575 |
| convergence after healing, no duplicates | **AS_EXPECTED** | every ALL event in all three databases, applied_events = 1 (replays refused by the sequence guard) |  | converged; no duplicate |
| standby takes over the ALL lane | **AS_EXPECTED** | after the holder dies, order-query-global-eu fans out the next events to all three databases |  | written by {order-query-global-eu=9} |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "fanOutSeconds" : 8.199,
  "fanOutWriters" : {
    "order-query-global-apac" : 6,
    "order-query-global-eu" : 9
  },
  "catalogConsumerStates" : [ "consumers/order-query_CatalogProjector_1_ALL" ],
  "fanOutEventsByReplica" : {
    "order-query-global-apac" : [ 571, 572 ],
    "order-query-global-eu" : [ 573, 574, 575 ]
  },
  "outageEvents" : 4,
  "outageRowsInHealthyCells" : {
    "AM" : 1,
    "EU" : 1
  },
  "laneCheckpointBeforeOutage" : 575,
  "laneCheckpointDuringOutage" : 575,
  "secondsToConvergeAfterHeal" : 2.087,
  "replaysAbsorbed" : 6,
  "takeoverSeconds" : 5.663,
  "logMarkers" : {
    "order-query-global-eu" : {
      "event=lab_fanout" : 11,
      "FanOutIncompleteException" : 3,
      "Event processing failed" : 3
    },
    "order-query-global-apac" : {
      "event=lab_fanout" : 4
    }
  }
}
```

## Notes

- The lane is one (one consumer id, one lock row); which replica holds it can change between fetch cycles (N4: the JDBC consumer lock is taken per cycle, not sticky).
- catalog_price in APAC not readable: Non riesco a trovare un server con il «targetServerType» specificato: primary
- A per-target lane (one consumer per regional database) would keep EU and AM current during an APAC outage, at the price of N lanes to operate; the lab keeps one lane on purpose.

## Timeline

- +8,0 s: scenario start
- +11,3 s: sent 5/5 ALL events (s16-after-query-d23dbb-sku-a*)
- +20,5 s: stopped app-pg-apac (APAC's regional database)
- +22,5 s: sent 4/4 ALL events (s16-after-query-d23dbb-sku-b*)
- +69,6 s: app-pg-apac back, ready=true
- +72,2 s: killed the lane holder order-query-global-apac
- +73,4 s: sent 3/3 ALL events (s16-after-query-d23dbb-sku-c*)
- +77,7 s: cleanup start
- +97,5 s: cleanup done

### catalog_price after the fan-out

```
EU s16-after-query-d23dbb-sku-a0 seq=571 applied=1 stale=0 ctx=ALL by=order-query-global-apac
EU s16-after-query-d23dbb-sku-a1 seq=572 applied=1 stale=0 ctx=ALL by=order-query-global-apac
EU s16-after-query-d23dbb-sku-a2 seq=573 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-after-query-d23dbb-sku-a3 seq=574 applied=1 stale=0 ctx=ALL by=order-query-global-eu
EU s16-after-query-d23dbb-sku-a4 seq=575 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-after-query-d23dbb-sku-a0 seq=571 applied=1 stale=0 ctx=ALL by=order-query-global-apac
AM s16-after-query-d23dbb-sku-a1 seq=572 applied=1 stale=0 ctx=ALL by=order-query-global-apac
AM s16-after-query-d23dbb-sku-a2 seq=573 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-after-query-d23dbb-sku-a3 seq=574 applied=1 stale=0 ctx=ALL by=order-query-global-eu
AM s16-after-query-d23dbb-sku-a4 seq=575 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-after-query-d23dbb-sku-a0 seq=571 applied=1 stale=0 ctx=ALL by=order-query-global-apac
APAC s16-after-query-d23dbb-sku-a1 seq=572 applied=1 stale=0 ctx=ALL by=order-query-global-apac
APAC s16-after-query-d23dbb-sku-a2 seq=573 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-after-query-d23dbb-sku-a3 seq=574 applied=1 stale=0 ctx=ALL by=order-query-global-eu
APAC s16-after-query-d23dbb-sku-a4 seq=575 applied=1 stale=0 ctx=ALL by=order-query-global-eu
```

### ALL lane checkpoint (every 3 s)

```
2026-10-15T13:27:51.008308Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:27:54.090799Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:27:57.156451Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:00.239392600Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:03.298071700Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:06.367903800Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:09.425674100Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:12.484772400Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:15.547923100Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:18.613474600Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:21.675439500Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:24.749026Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:27.813741600Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:30.880621400Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:33.965647800Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:37.065209800Z consumers/order-query_CatalogProjector_1_ALL=575
2026-10-15T13:28:40.136998700Z consumers/order-query_CatalogProjector_1_ALL=577
```

### log excerpts: order-query-global-eu

```
2026-10-15T13:27:48.833Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=573 sku=s16-after-query-d23dbb-sku-a2 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-15T13:27:49.341Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=574 sku=s16-after-query-d23dbb-sku-a3 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-15T13:27:49.767Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=575 sku=s16-after-query-d23dbb-sku-a4 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=APPLIED, am=APPLIED}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
com.evento.lab.cluster.support.store.CatalogFanOut$FanOutIncompleteException: fan-out incomplete, failed targets [apac], done {eu=STALE, am=STALE}
2026-10-15T13:28:02.120Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 1)
2026-10-15T13:28:14.482Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 2)
2026-10-15T13:28:26.829Z ERROR 1 --- [order-query] [Projector_1_ALL] c.e.a.reference.ProjectorReference       : Event processing failed for projector CatalogProjector event PriceChangedEvent (attempt 3)
```

### log excerpts: order-query-global-apac

```
2026-10-15T13:27:43.537Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=571 sku=s16-after-query-d23dbb-sku-a0 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-15T13:27:44.821Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=572 sku=s16-after-query-d23dbb-sku-a1 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
2026-10-15T13:28:40.558Z  INFO 1 --- [order-query] [Projector_1_ALL] c.e.l.c.support.store.CatalogFanOut      : event=lab_fanout seq=578 sku=s16-after-query-d23dbb-sku-b2 outcomes={eu=APPLIED, am=APPLIED, apac=APPLIED} failed=[]
```

### stack actions

```
2026-10-15T13:27:51.008308Z docker stop app-pg-apac
2026-10-15T13:28:33.555194300Z docker start app-pg-apac
2026-10-15T13:28:42.837753700Z docker kill order-query-global-apac
2026-10-15T13:28:48.726840200Z docker start order-query-global-apac (restore)
```

