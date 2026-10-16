# S12: Per-context WATERMARK consumer cost

- Label: `after-versioning`
- Gaps exercised: G10, G20
- Started: 2026-10-16T00:26:53.077159700Z, finished: 2026-10-16T00:32:19.473836900Z (326 s)
- Verdicts: {INCONCLUSIVE=1}

## Parameters

- orderIdPrefix: `s12-after-versioning-272c35-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{completeRatio=0.0, ratePerMinutePerRegion=120.0, regions=[AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=1}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EUROPA consumer cost vs global volume | **INCONCLUSIVE** | WATERMARK costs about the same as DEFAULT (ratio <= 1.5): flat in the other regions' traffic | recordAbsent is O(numeric gap): WATERMARK burns CPU/heap proportional to global volume (G10) | no load-attributable CPU to compare (CPU under load <= idle rate); DEFAULT: 1370.0 ms CPU over 99.938 s for 736 foreign events, idle 14,4 ms/s; WATERMARK: 1270.0 ms CPU over 99.869 s for 726 foreign events, idle 13,7 ms/s; |

- _EUROPA consumer cost vs global volume_: APES (G20) would make the gap ~4M ids/ms; the lab runs CPES only, where the gap per batch is the other regions' events, so G10 can only show as a small per-event cost.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "phases" : {
    "DEFAULT" : {
      "mode" : "DEFAULT",
      "idleCpuMsPerSecond" : 14.420765902900177,
      "requests" : 1101,
      "globalEventsAppended" : 736,
      "cpuMs" : 1370.0,
      "sampledSeconds" : 99.938,
      "cpuMsPer1kEvents" : 1861.413043478261,
      "loadCpuMs" : 0.0,
      "loadCpuMsPer1kEvents" : 0.0,
      "gcMsPer1kEvents" : 2.717391304347826,
      "heapFirst" : 37335672,
      "heapLast" : 28979640,
      "heapMax" : 39643176
    },
    "WATERMARK" : {
      "mode" : "WATERMARK",
      "idleCpuMsPerSecond" : 13.678239530112242,
      "requests" : 1089,
      "globalEventsAppended" : 726,
      "cpuMs" : 1270.0,
      "sampledSeconds" : 99.869,
      "cpuMsPer1kEvents" : 1749.3112947658403,
      "loadCpuMs" : 0.0,
      "loadCpuMsPer1kEvents" : 0.0,
      "gcMsPer1kEvents" : 4.132231404958678,
      "heapFirst" : 37551608,
      "heapLast" : 29161816,
      "heapMax" : 39527712
    }
  },
  "watermarkToDefaultCpuRatio" : null,
  "noiseFloorMs" : 1000.0
}
```

## Timeline

- +7,5 s: scenario start
- +16,6 s: order-query-eu (EUROPA only) in DEFAULT ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"EU"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}
- +56,8 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +151,2 s: load done: 1101 requests
- +170,6 s: order-query-eu (EUROPA only) in WATERMARK ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"WATERMARK","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"EU"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}
- +210,6 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +305,2 s: load done: 1089 requests
- +315,2 s: cleanup start
- +326,4 s: cleanup done

### samples DEFAULT

```
{t=1792110469877, cpuNanos=13830000000, heap=37335672, gcMillis=349, dockerStats=0.92%|190.4MiB / 256MiB}
{t=1792110476164, cpuNanos=13920000000, heap=37642040, gcMillis=349, dockerStats=1.21%|190.9MiB / 256MiB}
{t=1792110482405, cpuNanos=14010000000, heap=38009384, gcMillis=349, dockerStats=2.98%|191.4MiB / 256MiB}
{t=1792110488660, cpuNanos=14120000000, heap=38372664, gcMillis=349, dockerStats=3.84%|192MiB / 256MiB}
{t=1792110494899, cpuNanos=14230000000, heap=38702800, gcMillis=349, dockerStats=0.82%|192.5MiB / 256MiB}
{t=1792110501141, cpuNanos=14310000000, heap=39113400, gcMillis=349, dockerStats=0.65%|192.9MiB / 256MiB}
{t=1792110507373, cpuNanos=14400000000, heap=39385816, gcMillis=349, dockerStats=0.95%|193.6MiB / 256MiB}
{t=1792110513608, cpuNanos=14480000000, heap=39643176, gcMillis=349, dockerStats=5.62%|194.4MiB / 256MiB}
{t=1792110519843, cpuNanos=14560000000, heap=24971344, gcMillis=351, dockerStats=0.92%|194.6MiB / 256MiB}
{t=1792110526119, cpuNanos=14620000000, heap=25266912, gcMillis=351, dockerStats=0.50%|194.8MiB / 256MiB}
{t=1792110532380, cpuNanos=14680000000, heap=25734280, gcMillis=351, dockerStats=0.68%|194.8MiB / 256MiB}
{t=1792110538645, cpuNanos=14740000000, heap=26126592, gcMillis=351, dockerStats=4.01%|195MiB / 256MiB}
{t=1792110544870, cpuNanos=14810000000, heap=26518120, gcMillis=351, dockerStats=0.69%|195.1MiB / 256MiB}
{t=1792110551132, cpuNanos=14890000000, heap=26764000, gcMillis=351, dockerStats=1.36%|195.3MiB / 256MiB}
{t=1792110557374, cpuNanos=15100000000, heap=28274472, gcMillis=351, dockerStats=0.59%|195.9MiB / 256MiB}
{t=1792110563598, cpuNanos=15160000000, heap=28651312, gcMillis=351, dockerStats=0.50%|195.9MiB / 256MiB}
{t=1792110569815, cpuNanos=15200000000, heap=28979640, gcMillis=351, dockerStats=4.96%|196MiB / 256MiB}
```

### samples WATERMARK

```
{t=1792110623789, cpuNanos=13110000000, heap=37551608, gcMillis=350, dockerStats=0.70%|182.8MiB / 256MiB}
{t=1792110630057, cpuNanos=13210000000, heap=37950864, gcMillis=350, dockerStats=1.04%|183.3MiB / 256MiB}
{t=1792110636381, cpuNanos=13310000000, heap=38350280, gcMillis=350, dockerStats=1.23%|183.8MiB / 256MiB}
{t=1792110642640, cpuNanos=13410000000, heap=38562000, gcMillis=350, dockerStats=4.09%|184.4MiB / 256MiB}
{t=1792110648908, cpuNanos=13500000000, heap=38889744, gcMillis=350, dockerStats=0.69%|184.8MiB / 256MiB}
{t=1792110655159, cpuNanos=13570000000, heap=39130392, gcMillis=350, dockerStats=0.72%|185.2MiB / 256MiB}
{t=1792110661378, cpuNanos=13650000000, heap=39527712, gcMillis=350, dockerStats=0.94%|185.7MiB / 256MiB}
{t=1792110667609, cpuNanos=13740000000, heap=24421104, gcMillis=353, dockerStats=3.96%|186.6MiB / 256MiB}
{t=1792110673844, cpuNanos=13840000000, heap=25403520, gcMillis=353, dockerStats=0.69%|186.7MiB / 256MiB}
{t=1792110680103, cpuNanos=13900000000, heap=25665616, gcMillis=353, dockerStats=0.87%|186.9MiB / 256MiB}
{t=1792110686332, cpuNanos=13970000000, heap=25999976, gcMillis=353, dockerStats=0.50%|186.9MiB / 256MiB}
{t=1792110692564, cpuNanos=14050000000, heap=26343408, gcMillis=353, dockerStats=3.74%|188MiB / 256MiB}
{t=1792110698794, cpuNanos=14110000000, heap=26677168, gcMillis=353, dockerStats=0.83%|187.3MiB / 256MiB}
{t=1792110705018, cpuNanos=14170000000, heap=27002784, gcMillis=353, dockerStats=1.48%|187.5MiB / 256MiB}
{t=1792110711230, cpuNanos=14280000000, heap=28530872, gcMillis=353, dockerStats=0.57%|187.8MiB / 256MiB}
{t=1792110717443, cpuNanos=14340000000, heap=28820216, gcMillis=353, dockerStats=0.54%|187.8MiB / 256MiB}
{t=1792110723658, cpuNanos=14380000000, heap=29161816, gcMillis=353, dockerStats=3.13%|187.9MiB / 256MiB}
```

### stack actions

```
2026-10-16T00:27:00.629612300Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CONTEXTS=EUROPA, EVENTO_CHECKPOINT_MODE=DEFAULT}
2026-10-16T00:29:34.321799400Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CONTEXTS=EUROPA, EVENTO_CHECKPOINT_MODE=WATERMARK}
2026-10-16T00:32:08.259605300Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

