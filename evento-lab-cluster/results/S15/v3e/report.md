# S15: Clock skew on one region JVM (optional)

- Label: `v3e`
- Gaps exercised: fetch staleness gate (Low), G20
- Started: 2026-10-22T16:41:34.256402800Z, finished: 2026-10-22T16:42:10.447058Z (36 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s15-v3e-18c0e5-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- skewEnv: `{LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1, FAKETIME=-40s, FAKETIME_DONT_FAKE_MONOTONIC=1}`
- skewImage: `evento-lab-cluster/order-query:v3e-faketime`
- skewedClockMinusHostSeconds: `-40`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| skewed region consumer | **AS_EXPECTED** | the AFRICA projector (skewed clock) still projects its orders within 60 s | the staleness gate drops the skewed consumer's fetches: the orders never appear | all 5 orders projected |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "logMarkers" : {
    "evento-eu" : { },
    "order-query-eu" : { }
  }
}
```

## Timeline

- +3,5 s: scenario start
- +26,0 s: cleanup start
- +36,2 s: cleanup done

### stack actions

```
2026-10-22T16:41:37.812279200Z compose up -d --no-deps --force-recreate order-query-eu with env {FAKETIME=-40s, LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1, FAKETIME_DONT_FAKE_MONOTONIC=1} image evento-lab-cluster/order-query:v3e-faketime
2026-10-22T16:42:00.207781800Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

