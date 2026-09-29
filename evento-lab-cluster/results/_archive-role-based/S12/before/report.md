# S12: Per-context WATERMARK consumer cost

- Label: `before`
- Gaps exercised: G10, G20
- Started: 2026-09-29T09:01:47.936978100Z, finished: 2026-09-29T09:11:33.131724100Z (585 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s12-before-f40567-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- load: `{itemsPerOrder=1, durationSeconds=60, regions=[AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ], ratePerMinutePerRegion=120.0, completeRatio=0.0}`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| EUROPA consumer cost vs global volume | **AS_EXPECTED** | WATERMARK costs about the same as DEFAULT (ratio <= 1.5): flat in the other regions' traffic | recordAbsent is O(numeric gap): WATERMARK burns CPU/heap proportional to global volume (G10) | CPU per 1k global events: DEFAULT 5814,8 ms, WATERMARK 4020,6 ms (x0,69) |

- _EUROPA consumer cost vs global volume_: APES (G20) would make the gap ~4M ids/ms; the lab runs CPES only.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "phases" : {
    "DEFAULT" : {
      "mode" : "DEFAULT",
      "requests" : 817,
      "globalEventsAppended" : 270,
      "cpuMs" : 1570.0,
      "cpuMsPer1kEvents" : 5814.814814814815,
      "gcMsPer1kEvents" : 0.0,
      "heapFirst" : 29314016,
      "heapLast" : 34513216,
      "heapMax" : 34513216
    },
    "WATERMARK" : {
      "mode" : "WATERMARK",
      "requests" : 836,
      "globalEventsAppended" : 388,
      "cpuMs" : 1560.0,
      "cpuMsPer1kEvents" : 4020.6185567010307,
      "gcMsPer1kEvents" : 0.0,
      "heapFirst" : 30318544,
      "heapLast" : 35080824,
      "heapMax" : 35080824
    }
  },
  "watermarkToDefaultCpuRatio" : 0.6914439556110052
}
```

## Timeline

- +3,8 s: scenario start
- +12,3 s: region-europa in DEFAULT ready=true whoami={"role":"region","region":"EUROPA","cell":"EU","instance":"region-europa","server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"expectedServerInstanceId":null,"checkpointMode":"DEFAULT","bundles":["query-europa"],"busStates":{"query-europa":"READY"},"ready":true}
- +22,5 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +112,9 s: load done: 817 requests
- +131,2 s: region-europa in WATERMARK ready=true whoami={"role":"region","region":"EUROPA","cell":"EU","instance":"region-europa","server":"evento-eu:3030","addresses":["evento-eu:3030","toxi-eu:20123","toxi-eu:20133"],"expectedServerInstanceId":null,"checkpointMode":"WATERMARK","bundles":["query-europa"],"busStates":{"query-europa":"READY"},"ready":true}
- +141,3 s: load start [AFRICA, AMERICA, SUD_AMERICA, CINA, INDIA, AUSTRALIA_NZ] @120.0/min for 60 s
- +244,4 s: load done: 836 requests
- +254,5 s: cleanup start
- +585,2 s: cleanup done

### samples DEFAULT

```
{t=1790672530223, cpuNanos=13550000000, heap=29314016, gcMillis=338, dockerStats=1.15%|180.3MiB / 320MiB}
{t=1790672536327, cpuNanos=13650000000, heap=29848768, gcMillis=338, dockerStats=1.35%|180.4MiB / 320MiB}
{t=1790672542422, cpuNanos=13780000000, heap=30055496, gcMillis=338, dockerStats=1.28%|180.6MiB / 320MiB}
{t=1790672548523, cpuNanos=13870000000, heap=30487808, gcMillis=338, dockerStats=4.13%|180.7MiB / 320MiB}
{t=1790672554626, cpuNanos=13950000000, heap=30701832, gcMillis=338, dockerStats=0.89%|180.7MiB / 320MiB}
{t=1790672560705, cpuNanos=14040000000, heap=31093032, gcMillis=338, dockerStats=0.77%|180.8MiB / 320MiB}
{t=1790672566789, cpuNanos=14110000000, heap=31394608, gcMillis=338, dockerStats=0.99%|181MiB / 320MiB}
{t=1790672572872, cpuNanos=14190000000, heap=31632472, gcMillis=338, dockerStats=0.54%|181.3MiB / 320MiB}
{t=1790672578959, cpuNanos=14690000000, heap=32201328, gcMillis=338, dockerStats=2.70%|189.9MiB / 320MiB}
{t=1790672585040, cpuNanos=14720000000, heap=32413568, gcMillis=338, dockerStats=0.06%|189.9MiB / 320MiB}
{t=1790672591126, cpuNanos=14740000000, heap=32642944, gcMillis=338, dockerStats=0.08%|190MiB / 320MiB}
{t=1790672597233, cpuNanos=14760000000, heap=32798832, gcMillis=338, dockerStats=0.05%|190.1MiB / 320MiB}
{t=1790672603315, cpuNanos=14810000000, heap=33043128, gcMillis=338, dockerStats=1.18%|190.3MiB / 320MiB}
{t=1790672609394, cpuNanos=14890000000, heap=33449896, gcMillis=338, dockerStats=3.74%|190.8MiB / 320MiB}
{t=1790672615481, cpuNanos=14960000000, heap=33912224, gcMillis=338, dockerStats=0.96%|190.6MiB / 320MiB}
{t=1790672621568, cpuNanos=15030000000, heap=34185680, gcMillis=338, dockerStats=0.86%|190.8MiB / 320MiB}
{t=1790672627679, cpuNanos=15120000000, heap=34513216, gcMillis=338, dockerStats=0.86%|191.1MiB / 320MiB}
```

### samples WATERMARK

```
{t=1790672649189, cpuNanos=13920000000, heap=30318544, gcMillis=358, dockerStats=1.05%|174.6MiB / 320MiB}
{t=1790672655280, cpuNanos=14030000000, heap=30569328, gcMillis=358, dockerStats=2.02%|174.7MiB / 320MiB}
{t=1790672661380, cpuNanos=14120000000, heap=30941928, gcMillis=358, dockerStats=1.54%|174.9MiB / 320MiB}
{t=1790672667462, cpuNanos=14230000000, heap=31383144, gcMillis=358, dockerStats=7.68%|175MiB / 320MiB}
{t=1790672673623, cpuNanos=14390000000, heap=31723456, gcMillis=358, dockerStats=1.07%|175MiB / 320MiB}
{t=1790672680707, cpuNanos=14560000000, heap=31956224, gcMillis=358, dockerStats=0.88%|175.1MiB / 320MiB}
{t=1790672686824, cpuNanos=14650000000, heap=32200512, gcMillis=358, dockerStats=0.95%|175.2MiB / 320MiB}
{t=1790672692998, cpuNanos=14790000000, heap=32440240, gcMillis=358, dockerStats=3.61%|175.5MiB / 320MiB}
{t=1790672699077, cpuNanos=14890000000, heap=32964360, gcMillis=358, dockerStats=0.84%|176.2MiB / 320MiB}
{t=1790672705165, cpuNanos=14970000000, heap=33321280, gcMillis=358, dockerStats=2.33%|175.8MiB / 320MiB}
{t=1790672711304, cpuNanos=15060000000, heap=33504488, gcMillis=358, dockerStats=1.00%|176.2MiB / 320MiB}
{t=1790672717448, cpuNanos=15170000000, heap=33708600, gcMillis=358, dockerStats=0.80%|176.2MiB / 320MiB}
{t=1790672723625, cpuNanos=15250000000, heap=34097952, gcMillis=358, dockerStats=4.36%|176.5MiB / 320MiB}
{t=1790672729724, cpuNanos=15330000000, heap=34412232, gcMillis=358, dockerStats=1.76%|176.7MiB / 320MiB}
{t=1790672735813, cpuNanos=15420000000, heap=34697440, gcMillis=358, dockerStats=1.37%|176.8MiB / 320MiB}
{t=1790672741956, cpuNanos=15480000000, heap=35080824, gcMillis=358, dockerStats=31.69%|177.1MiB / 320MiB}
{t=1790672748289, dockerStats=n/a}
{t=1790672755144, dockerStats=n/a}
{t=1790672760231, dockerStats=n/a}
```

### stack actions

```
2026-09-29T09:01:51.754683500Z compose up -d --no-deps --force-recreate region-europa with env {CONSUMER_CHECKPOINT_MODE=DEFAULT}
2026-09-29T09:03:50.853368900Z compose up -d --no-deps --force-recreate region-europa with env {CONSUMER_CHECKPOINT_MODE=WATERMARK}
2026-09-29T09:06:02.462811300Z compose up -d --no-deps --force-recreate region-europa (restore default)
2026-09-29T09:11:04.548734100Z docker compose up -d --no-deps evento-eu (restore)
2026-09-29T09:11:05.487856600Z docker compose up -d --no-deps evento-am (restore)
2026-09-29T09:11:06.559164Z docker compose up -d --no-deps evento-apac (restore)
2026-09-29T09:11:08.080924Z docker compose up -d --no-deps cell-core-eu (restore)
2026-09-29T09:11:09.594663700Z docker compose up -d --no-deps cell-core-am (restore)
2026-09-29T09:11:11.606051900Z docker compose up -d --no-deps cell-core-apac (restore)
2026-09-29T09:11:13.995308800Z docker compose up -d --no-deps saga-eu (restore)
2026-09-29T09:11:15.565880300Z docker compose up -d --no-deps saga-apac (restore)
2026-09-29T09:11:17.833886100Z docker compose up -d --no-deps region-africa (restore)
2026-09-29T09:11:19.451774200Z docker compose up -d --no-deps region-america (restore)
2026-09-29T09:11:21.430099800Z docker compose up -d --no-deps region-sud-america (restore)
2026-09-29T09:11:23.353489400Z docker compose up -d --no-deps region-cina (restore)
2026-09-29T09:11:26.522986500Z docker compose up -d --no-deps region-india (restore)
2026-09-29T09:11:28.540362300Z docker compose up -d --no-deps region-australia-nz (restore)
2026-09-29T09:11:30.503279700Z docker compose up -d --no-deps lb (restore)
```

