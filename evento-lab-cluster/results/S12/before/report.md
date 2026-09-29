# S12: Per-context WATERMARK consumer cost

- Label: `before`
- Gaps exercised: G10, G20
- Started: 2026-09-29T13:19:37.371131400Z, finished: 2026-09-29T13:23:57.639898600Z (260 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s12-before-84f6a2-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{regions=[AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=1, completeRatio=0.0, ratePerMinutePerRegion=120.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EUROPA consumer cost vs global volume | **AS_EXPECTED** | WATERMARK costs about the same as DEFAULT (ratio <= 1.5): flat in the other regions' traffic | recordAbsent is O(numeric gap): WATERMARK burns CPU/heap proportional to global volume (G10) | CPU per 1k global events: DEFAULT 4537,8 ms, WATERMARK 3881,1 ms (x0,86) |

- _EUROPA consumer cost vs global volume_: APES (G20) would make the gap ~4M ids/ms; the lab runs CPES only.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "phases" : {
    "DEFAULT" : {
      "mode" : "DEFAULT",
      "requests" : 789,
      "globalEventsAppended" : 238,
      "cpuMs" : 1080.0,
      "cpuMsPer1kEvents" : 4537.81512605042,
      "gcMsPer1kEvents" : 0.0,
      "heapFirst" : 31356832,
      "heapLast" : 36366368,
      "heapMax" : 36366368
    },
    "WATERMARK" : {
      "mode" : "WATERMARK",
      "requests" : 790,
      "globalEventsAppended" : 286,
      "cpuMs" : 1110.0,
      "cpuMsPer1kEvents" : 3881.1188811188813,
      "gcMsPer1kEvents" : 0.0,
      "heapFirst" : 31188416,
      "heapLast" : 36843304,
      "heapMax" : 36843304
    }
  },
  "watermarkToDefaultCpuRatio" : 0.8552836052836054
}
```

## Timeline

- +6,0 s: scenario start
- +15,0 s: order-query-eu (EUROPA only) in DEFAULT ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":false,"value":"3 address(es)"},{"feature":"expectedClusterName","available":false,"value":"evento-lab-cluster"},{"feature":"zone","available":false,"value":"EU"}],"busState":"READY","ready":true}
- +25,2 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +115,6 s: load done: 789 requests
- +134,3 s: order-query-eu (EUROPA only) in WATERMARK ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"WATERMARK","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":false,"value":"3 address(es)"},{"feature":"expectedClusterName","available":false,"value":"evento-lab-cluster"},{"feature":"zone","available":false,"value":"EU"}],"busState":"READY","ready":true}
- +144,4 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +239,3 s: load done: 790 requests
- +249,4 s: cleanup start
- +260,3 s: cleanup done

### samples DEFAULT

```
{t=1790688002519, cpuNanos=13450000000, heap=31356832, gcMillis=370, dockerStats=0.77%|194.7MiB / 256MiB}
{t=1790688008810, cpuNanos=13540000000, heap=31664648, gcMillis=370, dockerStats=0.92%|194.8MiB / 256MiB}
{t=1790688015059, cpuNanos=13640000000, heap=31927008, gcMillis=370, dockerStats=3.19%|197.2MiB / 256MiB}
{t=1790688021292, cpuNanos=13760000000, heap=32648680, gcMillis=370, dockerStats=0.98%|195.1MiB / 256MiB}
{t=1790688027498, cpuNanos=13820000000, heap=32830472, gcMillis=370, dockerStats=1.17%|195.8MiB / 256MiB}
{t=1790688033739, cpuNanos=13930000000, heap=33379856, gcMillis=370, dockerStats=1.05%|195.5MiB / 256MiB}
{t=1790688039958, cpuNanos=14020000000, heap=33445784, gcMillis=370, dockerStats=1.26%|195.7MiB / 256MiB}
{t=1790688046181, cpuNanos=14080000000, heap=33799768, gcMillis=370, dockerStats=3.39%|195.8MiB / 256MiB}
{t=1790688052907, cpuNanos=14110000000, heap=34096488, gcMillis=370, dockerStats=0.11%|195.9MiB / 256MiB}
{t=1790688059782, cpuNanos=14200000000, heap=34304752, gcMillis=370, dockerStats=0.07%|196MiB / 256MiB}
{t=1790688066002, cpuNanos=14220000000, heap=34458784, gcMillis=370, dockerStats=3.84%|197MiB / 256MiB}
{t=1790688072247, cpuNanos=14270000000, heap=34997736, gcMillis=370, dockerStats=0.08%|196.5MiB / 256MiB}
{t=1790688078464, cpuNanos=14330000000, heap=35437760, gcMillis=370, dockerStats=1.00%|196.7MiB / 256MiB}
{t=1790688084711, cpuNanos=14390000000, heap=35875192, gcMillis=370, dockerStats=0.76%|197.5MiB / 256MiB}
{t=1790688090935, cpuNanos=14470000000, heap=36107680, gcMillis=370, dockerStats=0.78%|197MiB / 256MiB}
{t=1790688097164, cpuNanos=14530000000, heap=36366368, gcMillis=370, dockerStats=9.21%|197.2MiB / 256MiB}
```

### samples WATERMARK

```
{t=1790688121853, cpuNanos=13920000000, heap=31188416, gcMillis=348, dockerStats=0.98%|195.5MiB / 256MiB}
{t=1790688128106, cpuNanos=14000000000, heap=31545960, gcMillis=348, dockerStats=0.93%|196.3MiB / 256MiB}
{t=1790688134371, cpuNanos=14140000000, heap=32096168, gcMillis=348, dockerStats=4.70%|196.7MiB / 256MiB}
{t=1790688140629, cpuNanos=14220000000, heap=32563984, gcMillis=348, dockerStats=0.96%|195.9MiB / 256MiB}
{t=1790688146882, cpuNanos=14300000000, heap=32818760, gcMillis=348, dockerStats=0.82%|195.9MiB / 256MiB}
{t=1790688153092, cpuNanos=14370000000, heap=33236936, gcMillis=348, dockerStats=0.67%|196.1MiB / 256MiB}
{t=1790688159316, cpuNanos=14430000000, heap=33478304, gcMillis=348, dockerStats=0.86%|196.3MiB / 256MiB}
{t=1790688165557, cpuNanos=14520000000, heap=33876112, gcMillis=348, dockerStats=4.53%|196.8MiB / 256MiB}
{t=1790688171768, cpuNanos=14600000000, heap=34410432, gcMillis=348, dockerStats=0.88%|196.7MiB / 256MiB}
{t=1790688177993, cpuNanos=14640000000, heap=34574192, gcMillis=348, dockerStats=1.03%|196.9MiB / 256MiB}
{t=1790688184253, cpuNanos=14670000000, heap=34863968, gcMillis=348, dockerStats=0.65%|196.9MiB / 256MiB}
{t=1790688190473, cpuNanos=14710000000, heap=34995592, gcMillis=348, dockerStats=3.80%|197MiB / 256MiB}
{t=1790688196685, cpuNanos=14740000000, heap=35298984, gcMillis=348, dockerStats=0.27%|197.1MiB / 256MiB}
{t=1790688202971, cpuNanos=14800000000, heap=35546416, gcMillis=348, dockerStats=0.62%|197.3MiB / 256MiB}
{t=1790688209201, cpuNanos=14870000000, heap=35886280, gcMillis=348, dockerStats=0.88%|198MiB / 256MiB}
{t=1790688215440, cpuNanos=14950000000, heap=36295696, gcMillis=348, dockerStats=4.41%|198.5MiB / 256MiB}
{t=1790688221682, cpuNanos=15030000000, heap=36843304, gcMillis=348, dockerStats=0.91%|197.9MiB / 256MiB}
```

### stack actions

```
2026-09-29T13:19:43.335055600Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CHECKPOINT_MODE=DEFAULT, EVENTO_CONTEXTS=EUROPA}
2026-09-29T13:21:42.954589700Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CHECKPOINT_MODE=WATERMARK, EVENTO_CONTEXTS=EUROPA}
2026-09-29T13:23:46.736627200Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

