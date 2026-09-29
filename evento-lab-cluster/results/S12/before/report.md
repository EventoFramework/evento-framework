# S12: Per-context WATERMARK consumer cost

- Label: `before`
- Gaps exercised: G10, G20
- Started: 2026-09-29T16:50:10.409166500Z, finished: 2026-09-29T16:55:46.562627600Z (336 s)
- Verdicts: {INCONCLUSIVE=1}

## Parameters

- orderIdPrefix: `s12-before-94c15f-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{ratePerMinutePerRegion=120.0, completeRatio=0.0, itemsPerOrder=1, durationSeconds=60, regions=[AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EUROPA consumer cost vs global volume | **INCONCLUSIVE** | WATERMARK costs about the same as DEFAULT (ratio <= 1.5): flat in the other regions' traffic | recordAbsent is O(numeric gap): WATERMARK burns CPU/heap proportional to global volume (G10) | no load-attributable CPU to compare (CPU under load <= idle rate); DEFAULT: 950.0 ms CPU over 99.637 s for 254 foreign events, idle 13,7 ms/s; WATERMARK: 960.0 ms CPU over 99.61 s for 248 foreign events, idle 14,5 ms/s; |

- _EUROPA consumer cost vs global volume_: APES (G20) would make the gap ~4M ids/ms; the lab runs CPES only, where the gap per batch is the other regions' events, so G10 can only show as a small per-event cost.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "phases" : {
    "DEFAULT" : {
      "mode" : "DEFAULT",
      "idleCpuMsPerSecond" : 13.660653300654909,
      "requests" : 813,
      "globalEventsAppended" : 254,
      "cpuMs" : 950.0,
      "sampledSeconds" : 99.637,
      "cpuMsPer1kEvents" : 3740.1574803149606,
      "loadCpuMs" : 0.0,
      "loadCpuMsPer1kEvents" : 0.0,
      "gcMsPer1kEvents" : 0.0,
      "heapFirst" : 32938584,
      "heapLast" : 37924936,
      "heapMax" : 37924936
    },
    "WATERMARK" : {
      "mode" : "WATERMARK",
      "idleCpuMsPerSecond" : 14.465964799485654,
      "requests" : 773,
      "globalEventsAppended" : 248,
      "cpuMs" : 960.0,
      "sampledSeconds" : 99.61,
      "cpuMsPer1kEvents" : 3870.967741935484,
      "loadCpuMs" : 0.0,
      "loadCpuMsPer1kEvents" : 0.0,
      "gcMsPer1kEvents" : 0.0,
      "heapFirst" : 32926944,
      "heapLast" : 37781160,
      "heapMax" : 37781160
    }
  },
  "watermarkToDefaultCpuRatio" : null,
  "noiseFloorMs" : 1000.0
}
```

## Timeline

- +7,4 s: scenario start
- +21,4 s: order-query-eu (EUROPA only) in DEFAULT ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":false,"value":"3 address(es)"},{"feature":"expectedClusterName","available":false,"value":"evento-lab-cluster"},{"feature":"zone","available":false,"value":"EU"}],"busState":"READY","ready":true}
- +61,6 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +151,7 s: load done: 813 requests
- +175,4 s: order-query-eu (EUROPA only) in WATERMARK ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"WATERMARK","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":false,"value":"3 address(es)"},{"feature":"expectedClusterName","available":false,"value":"evento-lab-cluster"},{"feature":"zone","available":false,"value":"EU"}],"busState":"READY","ready":true}
- +215,4 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +305,5 s: load done: 773 requests
- +320,5 s: cleanup start
- +336,2 s: cleanup done

### samples DEFAULT

```
{t=1790700672004, cpuNanos=17340000000, heap=32938584, gcMillis=481, dockerStats=1.41%|190.8MiB / 256MiB}
{t=1790700678320, cpuNanos=17430000000, heap=33247488, gcMillis=481, dockerStats=0.68%|191.2MiB / 256MiB}
{t=1790700684543, cpuNanos=17500000000, heap=33520448, gcMillis=481, dockerStats=0.80%|191.2MiB / 256MiB}
{t=1790700690761, cpuNanos=17570000000, heap=33884840, gcMillis=481, dockerStats=4.20%|191.4MiB / 256MiB}
{t=1790700697002, cpuNanos=17650000000, heap=34299136, gcMillis=481, dockerStats=0.72%|191.5MiB / 256MiB}
{t=1790700703235, cpuNanos=17710000000, heap=34626192, gcMillis=481, dockerStats=0.97%|191.7MiB / 256MiB}
{t=1790700709442, cpuNanos=17800000000, heap=34800120, gcMillis=481, dockerStats=1.21%|191.9MiB / 256MiB}
{t=1790700715666, cpuNanos=17880000000, heap=35090128, gcMillis=481, dockerStats=0.37%|192.1MiB / 256MiB}
{t=1790700721889, cpuNanos=17910000000, heap=35380232, gcMillis=481, dockerStats=5.15%|192.2MiB / 256MiB}
{t=1790700728110, cpuNanos=17970000000, heap=35636832, gcMillis=481, dockerStats=0.07%|192.3MiB / 256MiB}
{t=1790700734313, cpuNanos=17990000000, heap=35882336, gcMillis=481, dockerStats=0.06%|192.4MiB / 256MiB}
{t=1790700740521, cpuNanos=18060000000, heap=36358232, gcMillis=481, dockerStats=0.06%|192.5MiB / 256MiB}
{t=1790700746789, cpuNanos=18100000000, heap=36537296, gcMillis=481, dockerStats=4.23%|192.6MiB / 256MiB}
{t=1790700752998, cpuNanos=18140000000, heap=36866488, gcMillis=481, dockerStats=0.78%|192.9MiB / 256MiB}
{t=1790700759199, cpuNanos=18200000000, heap=37098000, gcMillis=481, dockerStats=0.49%|193.2MiB / 256MiB}
{t=1790700765416, cpuNanos=18240000000, heap=37558208, gcMillis=481, dockerStats=0.47%|193.1MiB / 256MiB}
{t=1790700771641, cpuNanos=18290000000, heap=37924936, gcMillis=481, dockerStats=n/a}
```

### samples WATERMARK

```
{t=1790700825955, cpuNanos=13750000000, heap=32926944, gcMillis=360, dockerStats=1.03%|181.4MiB / 256MiB}
{t=1790700832207, cpuNanos=13830000000, heap=33219208, gcMillis=360, dockerStats=1.03%|181.3MiB / 256MiB}
{t=1790700838434, cpuNanos=13910000000, heap=33451088, gcMillis=360, dockerStats=0.98%|181.6MiB / 256MiB}
{t=1790700844642, cpuNanos=13990000000, heap=33862320, gcMillis=360, dockerStats=4.42%|181.9MiB / 256MiB}
{t=1790700850877, cpuNanos=14080000000, heap=34203080, gcMillis=360, dockerStats=0.79%|182MiB / 256MiB}
{t=1790700857091, cpuNanos=14150000000, heap=34560320, gcMillis=360, dockerStats=0.89%|182.2MiB / 256MiB}
{t=1790700863311, cpuNanos=14230000000, heap=34901856, gcMillis=360, dockerStats=1.05%|182.4MiB / 256MiB}
{t=1790700869558, cpuNanos=14290000000, heap=35140864, gcMillis=360, dockerStats=4.65%|182.9MiB / 256MiB}
{t=1790700875801, cpuNanos=14330000000, heap=35455408, gcMillis=360, dockerStats=0.62%|182.7MiB / 256MiB}
{t=1790700882050, cpuNanos=14360000000, heap=35716496, gcMillis=360, dockerStats=0.07%|182.8MiB / 256MiB}
{t=1790700888261, cpuNanos=14400000000, heap=35925136, gcMillis=360, dockerStats=0.06%|182.8MiB / 256MiB}
{t=1790700894467, cpuNanos=14420000000, heap=36208232, gcMillis=360, dockerStats=0.05%|182.9MiB / 256MiB}
{t=1790700900686, cpuNanos=14440000000, heap=36407184, gcMillis=360, dockerStats=3.58%|183MiB / 256MiB}
{t=1790700906929, cpuNanos=14540000000, heap=36984992, gcMillis=360, dockerStats=0.65%|183.1MiB / 256MiB}
{t=1790700913130, cpuNanos=14590000000, heap=37228744, gcMillis=360, dockerStats=1.13%|183.2MiB / 256MiB}
{t=1790700919357, cpuNanos=14660000000, heap=37513864, gcMillis=360, dockerStats=0.45%|184.1MiB / 256MiB}
{t=1790700925565, cpuNanos=14710000000, heap=37781160, gcMillis=360, dockerStats=n/a}
```

### stack actions

```
2026-09-29T16:50:17.870596900Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CHECKPOINT_MODE=DEFAULT, EVENTO_CONTEXTS=EUROPA}
2026-09-29T16:52:57.172704800Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CHECKPOINT_MODE=WATERMARK, EVENTO_CONTEXTS=EUROPA}
2026-09-29T16:55:30.933382500Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

