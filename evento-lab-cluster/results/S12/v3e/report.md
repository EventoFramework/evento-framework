# S12: Per-context WATERMARK consumer cost

- Label: `v3e`
- Gaps exercised: G10, G20
- Started: 2026-10-22T16:09:09.955004100Z, finished: 2026-10-22T16:13:34.219659200Z (264 s)
- Verdicts: {INCONCLUSIVE=1}

## Parameters

- orderIdPrefix: `s12-v3e-723e2c-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- load: `{itemsPerOrder=1, durationSeconds=60, regions=[AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=120.0, completeRatio=0.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EUROPA consumer cost vs global volume | **INCONCLUSIVE** | WATERMARK costs about the same as DEFAULT (ratio <= 1.5): flat in the other regions' traffic | recordAbsent is O(numeric gap): WATERMARK burns CPU/heap proportional to global volume (G10) | load-attributable CPU per 1k global events (idle rate subtracted): DEFAULT 2748,7 ms, WATERMARK 2901,0 ms (x1,06); load CPU DEFAULT 3958 ms, WATERMARK 4166 ms, difference below the 1000 ms noise floor |

- _EUROPA consumer cost vs global volume_: APES (G20) would make the gap ~4M ids/ms; the lab runs CPES only, where the gap per batch is the other regions' events, so G10 can only show as a small per-event cost.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "phases" : {
    "DEFAULT" : {
      "mode" : "DEFAULT",
      "idleCpuMsPerSecond" : 13.630532392559333,
      "requests" : 1440,
      "globalEventsAppended" : 1440,
      "cpuMs" : 4900.0,
      "sampledSeconds" : 69.098,
      "cpuMsPer1kEvents" : 3402.777777777778,
      "loadCpuMs" : 3958.157472738935,
      "loadCpuMsPer1kEvents" : 2748.7204671798163,
      "gcMsPer1kEvents" : 6.944444444444445,
      "heapFirst" : 40914768,
      "heapLast" : 40155000,
      "heapMax" : 42052984
    },
    "WATERMARK" : {
      "mode" : "WATERMARK",
      "idleCpuMsPerSecond" : 13.257804025551405,
      "requests" : 1436,
      "globalEventsAppended" : 1436,
      "cpuMs" : 5080.0,
      "sampledSeconds" : 68.952,
      "cpuMsPer1kEvents" : 3537.6044568245125,
      "loadCpuMs" : 4165.84789683018,
      "loadCpuMsPer1kEvents" : 2901.0082847006825,
      "gcMsPer1kEvents" : 6.963788300835655,
      "heapFirst" : 44355568,
      "heapLast" : 44105080,
      "heapMax" : 50744232
    }
  },
  "watermarkToDefaultCpuRatio" : 1.055403166432967,
  "noiseFloorMs" : 1000.0
}
```

## Timeline

- +7,9 s: scenario start
- +16,8 s: order-query-eu (EUROPA only) in DEFAULT ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"DEFAULT","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"EU"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}
- +57,0 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +118,6 s: load done: 1440 requests
- +137,5 s: order-query-eu (EUROPA only) in WATERMARK ready=true whoami={"bundleId":"order-query","instance":"order-query-eu","zone":"EU","cell":"EU","region":null,"contexts":["EUROPA"],"consumerPlan":{"OrderProjector":["EUROPA"],"CatalogProjector":[]},"server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"clusterName":"evento-lab-cluster","expectedServerInstanceId":null,"checkpointMode":"WATERMARK","appDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","consumerStateDb":"jdbc:postgresql://app-pg-eu:5432,app-pg-eu-r:5432/app?targetServerType=primary&tcpKeepAlive=true&connectTimeout=10","dataTargets":[],"frameworkFeatures":[{"feature":"endpointFailover","available":true,"value":"3 address(es)"},{"feature":"expectedClusterName","available":true,"value":"evento-lab-cluster"},{"feature":"zone","available":true,"value":"EU"},{"feature":"failback","available":true,"value":"default window"}],"busState":"READY","ready":true}
- +177,5 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +239,1 s: load done: 1436 requests
- +249,1 s: cleanup start
- +264,3 s: cleanup done

### samples DEFAULT

```
{t=1792685406937, cpuNanos=14000000000, heap=40914768, gcMillis=314, dockerStats=11.65%|181MiB / 256MiB}
{t=1792685413228, cpuNanos=14740000000, heap=32533024, gcMillis=320, dockerStats=9.89%|182.7MiB / 256MiB}
{t=1792685419485, cpuNanos=15430000000, heap=35495256, gcMillis=320, dockerStats=8.28%|184.1MiB / 256MiB}
{t=1792685425799, cpuNanos=15960000000, heap=36922096, gcMillis=320, dockerStats=16.64%|184.5MiB / 256MiB}
{t=1792685432095, cpuNanos=16580000000, heap=38485848, gcMillis=320, dockerStats=5.91%|185MiB / 256MiB}
{t=1792685438377, cpuNanos=16980000000, heap=39993056, gcMillis=320, dockerStats=9.30%|185.6MiB / 256MiB}
{t=1792685444638, cpuNanos=17390000000, heap=42052984, gcMillis=320, dockerStats=4.67%|185.5MiB / 256MiB}
{t=1792685450948, cpuNanos=17760000000, heap=33552272, gcMillis=324, dockerStats=10.24%|186.2MiB / 256MiB}
{t=1792685457204, cpuNanos=18210000000, heap=35822408, gcMillis=324, dockerStats=5.14%|186.4MiB / 256MiB}
{t=1792685463527, cpuNanos=18590000000, heap=37919304, gcMillis=324, dockerStats=4.35%|186.7MiB / 256MiB}
{t=1792685469801, cpuNanos=18870000000, heap=39671368, gcMillis=324, dockerStats=0.33%|186.7MiB / 256MiB}
{t=1792685476035, cpuNanos=18900000000, heap=40155000, gcMillis=324, dockerStats=4.91%|187.4MiB / 256MiB}
```

### samples WATERMARK

```
{t=1792685527612, cpuNanos=14350000000, heap=44355568, gcMillis=306, dockerStats=22.90%|204.9MiB / 256MiB}
{t=1792685533854, cpuNanos=15200000000, heap=46406608, gcMillis=306, dockerStats=10.65%|205MiB / 256MiB}
{t=1792685540098, cpuNanos=15910000000, heap=49200752, gcMillis=306, dockerStats=9.62%|206MiB / 256MiB}
{t=1792685546358, cpuNanos=16380000000, heap=50744232, gcMillis=306, dockerStats=17.39%|206.8MiB / 256MiB}
{t=1792685552650, cpuNanos=17050000000, heap=41735888, gcMillis=312, dockerStats=5.84%|208.1MiB / 256MiB}
{t=1792685558950, cpuNanos=17510000000, heap=43860168, gcMillis=312, dockerStats=8.25%|208MiB / 256MiB}
{t=1792685565241, cpuNanos=17900000000, heap=45982448, gcMillis=312, dockerStats=4.88%|208.2MiB / 256MiB}
{t=1792685571523, cpuNanos=18240000000, heap=48413576, gcMillis=312, dockerStats=9.28%|208.6MiB / 256MiB}
{t=1792685577810, cpuNanos=18730000000, heap=50654592, gcMillis=312, dockerStats=6.28%|208.7MiB / 256MiB}
{t=1792685584118, cpuNanos=19140000000, heap=41840560, gcMillis=316, dockerStats=4.43%|209MiB / 256MiB}
{t=1792685590343, cpuNanos=19400000000, heap=43609736, gcMillis=316, dockerStats=0.35%|209MiB / 256MiB}
{t=1792685596564, cpuNanos=19430000000, heap=44105080, gcMillis=316, dockerStats=3.17%|209.2MiB / 256MiB}
```

### stack actions

```
2026-10-22T16:09:17.880910400Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CONTEXTS=EUROPA, EVENTO_CHECKPOINT_MODE=DEFAULT}
2026-10-22T16:11:18.596139800Z compose up -d --no-deps --force-recreate order-query-eu with env {EVENTO_CONTEXTS=EUROPA, EVENTO_CHECKPOINT_MODE=WATERMARK}
2026-10-22T16:13:19.080252900Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

