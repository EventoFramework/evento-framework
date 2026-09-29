# S15: Clock skew on one region JVM (optional)

- Label: `before`
- Gaps exercised: fetch staleness gate (Low), G20
- Started: 2026-09-29T13:59:25.163774800Z, finished: 2026-09-29T14:00:06.691248100Z (41 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s15-before-ae3944-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- skewEnv: `{LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1, FAKETIME=+40s, FAKETIME_DONT_FAKE_MONOTONIC=1}`
- skewImage: `evento-lab-cluster/order-query:before-faketime`
- skewedClockMinusHostSeconds: `40`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| skewed region consumer | **AS_EXPECTED** | the AFRICA projector (clock +skew) still projects its orders within 60 s | the staleness gate drops the skewed consumer's fetches: the orders never appear | all 5 orders projected |


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

- +1,4 s: scenario start
- +26,5 s: cleanup start
- +41,5 s: cleanup done

### stack actions

```
2026-09-29T13:59:26.587118400Z compose up -d --no-deps --force-recreate order-query-eu with env {FAKETIME=+40s, FAKETIME_DONT_FAKE_MONOTONIC=1, LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1} image evento-lab-cluster/order-query:before-faketime
2026-09-29T13:59:51.640860600Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

