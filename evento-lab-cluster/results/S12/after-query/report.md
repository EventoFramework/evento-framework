# S12: Per-context WATERMARK consumer cost

- Label: `after-query`
- Gaps exercised: G10, G20
- Started: 2026-10-15T13:58:41.237060200Z, finished: 2026-10-15T14:04:15.629676500Z (334 s)
- Verdicts: {INCONCLUSIVE=1}

## Parameters

- orderIdPrefix: `s12-after-query-abbebe-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{ratePerMinutePerRegion=120.0, regions=[AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], durationSeconds=60, itemsPerOrder=1, completeRatio=0.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EUROPA consumer cost vs global volume | **INCONCLUSIVE** | WATERMARK costs about the same as DEFAULT (ratio <= 1.5): flat in the other regions' traffic | recordAbsent is O(numeric gap): WATERMARK burns CPU/heap proportional to global volume (G10) | no load-attributable CPU to compare (CPU under load <= idle rate); DEFAULT: 1320.0 ms CPU over 99.858 s for 742 foreign events, idle 17,6 ms/s; WATERMARK: 1420.0 ms CPU over 106.292 s for 736 foreign events, idle 15,3 ms/s; |

- _EUROPA consumer cost vs global volume_: APES (G20) would make the gap ~4M ids/ms; the lab runs CPES only, where the gap per batch is the other regions' events, so G10 can only show as a small per-event cost.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "phases" : {
    "DEFAULT" : {
      "mode" : "DEFAULT",
      "idleCpuMsPerSecond" : 17.57890531362365,
      "requests" : 1102,
      "globalEventsAppended" : 742,
      "cpuMs" : 1320.0,
      "sampledSeconds" : 99.858,
      "cpuMsPer1kEvents" : 1778.9757412398922,
      "loadCpuMs" : 0.0,
      "loadCpuMsPer1kEvents" : 0.0,
      "gcMsPer1kEvents" : 4.0431266846361185,
      "heapFirst" : 37061864,
      "heapLast" : 28991872,
      "heapMax" : 39747064
    },
    "WATERMARK" : {
      "mode" : "WATERMARK",
      "idleCpuMsPerSecond" : 15.287444180713683,
      "requests" : 1104,
      "globalEventsAppended" : 736,
      "cpuMs" : 1420.0,
      "sampledSeconds" : 106.292,
      "cpuMsPer1kEvents" : 1929.3478260869565,
      "loadCpuMs" : 0.0,
      "loadCpuMsPer1kEvents" : 0.0,
      "gcMsPer1kEvents" : 4.076086956521739,
      "heapFirst" : 37250360,
      "heapLast" : 29475952,
      "heapMax" : 39690072
    }
  },
  "watermarkToDefaultCpuRatio" : null,
  "noiseFloorMs" : 1000.0
}
```

## Timeline

- +7,9 s: scenario start
- +16,9 s: order-query-eu (EUROPA only) in DEFAULT ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"EU"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}
- +57,1 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +151,5 s: load done: 1102 requests
- +171,1 s: order-query-eu (EUROPA only) in WATERMARK ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"WATERMARK","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"EU"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}
- +211,1 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +308,2 s: load done: 1104 requests
- +323,2 s: cleanup start
- +334,4 s: cleanup done

### samples DEFAULT

```
{t=1792072778311, cpuNanos=13440000000, heap=37061864, gcMillis=333, dockerStats=0.83%|187.6MiB / 256MiB}
{t=1792072784572, cpuNanos=13550000000, heap=37462096, gcMillis=333, dockerStats=1.16%|187.9MiB / 256MiB}
{t=1792072790828, cpuNanos=13640000000, heap=37701296, gcMillis=333, dockerStats=0.70%|188.3MiB / 256MiB}
{t=1792072797064, cpuNanos=13720000000, heap=38076592, gcMillis=333, dockerStats=5.68%|188.9MiB / 256MiB}
{t=1792072803305, cpuNanos=13810000000, heap=38476096, gcMillis=333, dockerStats=0.78%|189.4MiB / 256MiB}
{t=1792072809578, cpuNanos=13940000000, heap=38978632, gcMillis=333, dockerStats=1.54%|190.2MiB / 256MiB}
{t=1792072815813, cpuNanos=14030000000, heap=39334808, gcMillis=333, dockerStats=1.13%|190.8MiB / 256MiB}
{t=1792072822028, cpuNanos=14110000000, heap=39747064, gcMillis=336, dockerStats=4.42%|191.7MiB / 256MiB}
{t=1792072828272, cpuNanos=14200000000, heap=25122264, gcMillis=336, dockerStats=0.68%|191.8MiB / 256MiB}
{t=1792072834484, cpuNanos=14250000000, heap=25380936, gcMillis=336, dockerStats=0.49%|191.9MiB / 256MiB}
{t=1792072840742, cpuNanos=14310000000, heap=25784184, gcMillis=336, dockerStats=0.67%|192.7MiB / 256MiB}
{t=1792072846997, cpuNanos=14380000000, heap=26120808, gcMillis=336, dockerStats=0.82%|192.1MiB / 256MiB}
{t=1792072853225, cpuNanos=14440000000, heap=26388760, gcMillis=336, dockerStats=5.30%|192.3MiB / 256MiB}
{t=1792072859481, cpuNanos=14510000000, heap=26818320, gcMillis=336, dockerStats=1.26%|192.4MiB / 256MiB}
{t=1792072865705, cpuNanos=14650000000, heap=28298632, gcMillis=336, dockerStats=0.46%|192.8MiB / 256MiB}
{t=1792072871935, cpuNanos=14700000000, heap=28689896, gcMillis=336, dockerStats=0.38%|193.1MiB / 256MiB}
{t=1792072878169, cpuNanos=14760000000, heap=28991872, gcMillis=336, dockerStats=5.58%|193.2MiB / 256MiB}
```

### samples WATERMARK

```
{t=1792072932530, cpuNanos=14430000000, heap=37250360, gcMillis=358, dockerStats=1.37%|173.7MiB / 256MiB}
{t=1792072938778, cpuNanos=14520000000, heap=37595808, gcMillis=358, dockerStats=1.45%|174MiB / 256MiB}
{t=1792072945048, cpuNanos=14630000000, heap=37963000, gcMillis=358, dockerStats=1.09%|174.5MiB / 256MiB}
{t=1792072951313, cpuNanos=14710000000, heap=38253728, gcMillis=358, dockerStats=4.87%|175.1MiB / 256MiB}
{t=1792072957545, cpuNanos=14790000000, heap=38618488, gcMillis=358, dockerStats=0.74%|175.5MiB / 256MiB}
{t=1792072963783, cpuNanos=14870000000, heap=38931680, gcMillis=358, dockerStats=0.82%|176MiB / 256MiB}
{t=1792072970055, cpuNanos=14930000000, heap=39241056, gcMillis=358, dockerStats=0.99%|176.5MiB / 256MiB}
{t=1792072976339, cpuNanos=15030000000, heap=39690072, gcMillis=358, dockerStats=4.22%|177.1MiB / 256MiB}
{t=1792072982609, cpuNanos=15130000000, heap=24987576, gcMillis=361, dockerStats=1.07%|177.8MiB / 256MiB}
{t=1792072988901, cpuNanos=15190000000, heap=25356064, gcMillis=361, dockerStats=0.65%|177.9MiB / 256MiB}
{t=1792072995150, cpuNanos=15250000000, heap=25737048, gcMillis=361, dockerStats=0.56%|178MiB / 256MiB}
{t=1792073001390, cpuNanos=15380000000, heap=26046664, gcMillis=361, dockerStats=5.04%|178.6MiB / 256MiB}
{t=1792073007620, cpuNanos=15450000000, heap=26485480, gcMillis=361, dockerStats=1.11%|178.3MiB / 256MiB}
{t=1792073013870, cpuNanos=15510000000, heap=26889288, gcMillis=361, dockerStats=1.25%|178.5MiB / 256MiB}
{t=1792073020092, cpuNanos=15670000000, heap=28406304, gcMillis=361, dockerStats=0.50%|181.5MiB / 256MiB}
{t=1792073026349, cpuNanos=15730000000, heap=28779312, gcMillis=361, dockerStats=0.52%|181.5MiB / 256MiB}
{t=1792073032598, cpuNanos=15770000000, heap=29067240, gcMillis=361, dockerStats=0.45%|181.8MiB / 256MiB}
{t=1792073038822, cpuNanos=15850000000, heap=29475952, gcMillis=361, dockerStats=n/a}
```

### stack actions

```
2026-10-15T13:58:49.137908100Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CONTEXTS=EUROPA, EVENTO_CHECKPOINT_MODE=DEFAULT}
2026-10-15T14:01:22.748248Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CONTEXTS=EUROPA, EVENTO_CHECKPOINT_MODE=WATERMARK}
2026-10-15T14:04:04.468179600Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

