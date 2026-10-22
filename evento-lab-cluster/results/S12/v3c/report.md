# S12: Per-context WATERMARK consumer cost

- Label: `v3c`
- Gaps exercised: G10, G20
- Started: 2026-10-22T13:12:05.115279700Z, finished: 2026-10-22T13:16:39.939084500Z (274 s)
- Verdicts: {INCONCLUSIVE=1}

## Parameters

- orderIdPrefix: `s12-v3c-ce2604-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{ratePerMinutePerRegion=120.0, completeRatio=0.0, itemsPerOrder=1, durationSeconds=60, regions=[AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ]}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EUROPA consumer cost vs global volume | **INCONCLUSIVE** | WATERMARK costs about the same as DEFAULT (ratio <= 1.5): flat in the other regions' traffic | recordAbsent is O(numeric gap): WATERMARK burns CPU/heap proportional to global volume (G10) | load-attributable CPU per 1k global events (idle rate subtracted): DEFAULT 2906,6 ms, WATERMARK 2977,3 ms (x1,02); load CPU DEFAULT 4180 ms, WATERMARK 4275 ms, difference below the 1000 ms noise floor |

- _EUROPA consumer cost vs global volume_: APES (G20) would make the gap ~4M ids/ms; the lab runs CPES only, where the gap per batch is the other regions' events, so G10 can only show as a small per-event cost.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "phases" : {
    "DEFAULT" : {
      "mode" : "DEFAULT",
      "idleCpuMsPerSecond" : 14.837390223362874,
      "requests" : 1438,
      "globalEventsAppended" : 1438,
      "cpuMs" : 5210.0,
      "sampledSeconds" : 69.444,
      "cpuMsPer1kEvents" : 3623.0876216968013,
      "loadCpuMs" : 4179.632273328789,
      "loadCpuMsPer1kEvents" : 2906.5592999504793,
      "gcMsPer1kEvents" : 9.040333796940194,
      "heapFirst" : 40878968,
      "heapLast" : 39297408,
      "heapMax" : 41633504
    },
    "WATERMARK" : {
      "mode" : "WATERMARK",
      "idleCpuMsPerSecond" : 15.228019555983009,
      "requests" : 1436,
      "globalEventsAppended" : 1436,
      "cpuMs" : 5330.0,
      "sampledSeconds" : 69.25,
      "cpuMsPer1kEvents" : 3711.699164345404,
      "loadCpuMs" : 4275.459645748177,
      "loadCpuMsPer1kEvents" : 2977.3395861756107,
      "gcMsPer1kEvents" : 5.571030640668524,
      "heapFirst" : 44718064,
      "heapLast" : 44584096,
      "heapMax" : 51247952
    }
  },
  "watermarkToDefaultCpuRatio" : 1.0243519154163954,
  "noiseFloorMs" : 1000.0
}
```

## Timeline

- +9,0 s: scenario start
- +22,3 s: order-query-eu (EUROPA only) in DEFAULT ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"EU"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}
- +62,5 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +124,3 s: load done: 1438 requests
- +147,6 s: order-query-eu (EUROPA only) in WATERMARK ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"WATERMARK","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"EU"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}
- +187,6 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +249,2 s: load done: 1436 requests
- +259,3 s: cleanup start
- +274,8 s: cleanup done

### samples DEFAULT

```
{t=1792674787623, cpuNanos=15050000000, heap=40878968, gcMillis=298, dockerStats=13.68%|180.9MiB / 256MiB}
{t=1792674793950, cpuNanos=15860000000, heap=32259896, gcMillis=304, dockerStats=10.59%|182.5MiB / 256MiB}
{t=1792674800262, cpuNanos=16520000000, heap=35142168, gcMillis=304, dockerStats=7.92%|183.5MiB / 256MiB}
{t=1792674806610, cpuNanos=17130000000, heap=36657808, gcMillis=304, dockerStats=15.02%|184.2MiB / 256MiB}
{t=1792674812926, cpuNanos=17740000000, heap=38258072, gcMillis=304, dockerStats=6.09%|184.7MiB / 256MiB}
{t=1792674819215, cpuNanos=18250000000, heap=39790664, gcMillis=304, dockerStats=9.71%|185.3MiB / 256MiB}
{t=1792674825524, cpuNanos=18670000000, heap=41633504, gcMillis=304, dockerStats=4.38%|185.2MiB / 256MiB}
{t=1792674831840, cpuNanos=19040000000, heap=32527168, gcMillis=311, dockerStats=5.47%|185.8MiB / 256MiB}
{t=1792674838236, cpuNanos=19550000000, heap=34822112, gcMillis=311, dockerStats=6.23%|185.9MiB / 256MiB}
{t=1792674844561, cpuNanos=19950000000, heap=37030744, gcMillis=311, dockerStats=5.93%|186.1MiB / 256MiB}
{t=1792674850821, cpuNanos=20210000000, heap=38886848, gcMillis=311, dockerStats=0.36%|186.2MiB / 256MiB}
{t=1792674857067, cpuNanos=20260000000, heap=39297408, gcMillis=311, dockerStats=0.38%|186.3MiB / 256MiB}
```

### samples WATERMARK

```
{t=1792674912868, cpuNanos=15110000000, heap=44718064, gcMillis=340, dockerStats=11.19%|192.5MiB / 256MiB}
{t=1792674919140, cpuNanos=15890000000, heap=46584400, gcMillis=340, dockerStats=10.03%|194.6MiB / 256MiB}
{t=1792674925462, cpuNanos=16590000000, heap=49421872, gcMillis=340, dockerStats=8.91%|195.5MiB / 256MiB}
{t=1792674931781, cpuNanos=17250000000, heap=50908832, gcMillis=340, dockerStats=14.72%|195.7MiB / 256MiB}
{t=1792674938040, cpuNanos=17910000000, heap=42209408, gcMillis=345, dockerStats=6.53%|197.3MiB / 256MiB}
{t=1792674944396, cpuNanos=18350000000, heap=44387304, gcMillis=345, dockerStats=9.44%|197.6MiB / 256MiB}
{t=1792674950753, cpuNanos=18820000000, heap=46522760, gcMillis=345, dockerStats=4.61%|197.8MiB / 256MiB}
{t=1792674957057, cpuNanos=19230000000, heap=49011536, gcMillis=345, dockerStats=9.43%|198.2MiB / 256MiB}
{t=1792674963350, cpuNanos=19750000000, heap=51247952, gcMillis=345, dockerStats=6.37%|198.2MiB / 256MiB}
{t=1792674969651, cpuNanos=20130000000, heap=42425904, gcMillis=348, dockerStats=5.47%|198.5MiB / 256MiB}
{t=1792674975905, cpuNanos=20390000000, heap=44264160, gcMillis=348, dockerStats=0.64%|198.6MiB / 256MiB}
{t=1792674982118, cpuNanos=20440000000, heap=44584096, gcMillis=348, dockerStats=0.33%|198.6MiB / 256MiB}
```

### stack actions

```
2026-10-22T13:12:14.171932100Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CHECKPOINT_MODE=DEFAULT, EVENTO_CONTEXTS=EUROPA}
2026-10-22T13:14:19.408681800Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CHECKPOINT_MODE=WATERMARK, EVENTO_CONTEXTS=EUROPA}
2026-10-22T13:16:24.387787500Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

