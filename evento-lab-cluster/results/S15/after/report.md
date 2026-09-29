# S15: Clock skew on one region JVM (optional)

- Label: `after`
- Gaps exercised: fetch staleness gate (Low), G20
- Started: 2026-09-29T16:00:38.255527200Z, finished: 2026-09-29T16:03:10.478344600Z (152 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s15-after-8dcae3-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}`
- lbRoutingAtStart: `ok`
- skewEnv: `{LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1, FAKETIME=-40s, FAKETIME_DONT_FAKE_MONOTONIC=1}`
- skewImage: `evento-lab-cluster/order-query:after-faketime`
- skewedClockMinusHostSeconds: `-41`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| skewed region consumer | **KNOWN_DEFECT_REPRODUCED** | the AFRICA projector (skewed clock) still projects its orders within 60 s | the staleness gate drops the skewed consumer's fetches: the orders never appear | orders not projected within 60 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "logMarkers" : {
    "evento-eu" : {
      "stale" : 120,
      "fetch" : 120
    },
    "order-query-eu" : {
      "stale" : 240,
      "fetch" : 364
    }
  }
}
```

## Timeline

- +3,3 s: placement: 8 JVM(s) on a remote server, restarting them: {gateway-australia-nz=evento-eu, gateway-cina=evento-eu, gateway-india=evento-eu, order-command-apac=evento-eu, order-query-apac=evento-eu, order-query-cina=evento-eu, order-query-global-apac=evento-eu, order-saga-apac=evento-eu}
- +59,4 s: placement restored, ready={gateway-australia-nz=true, gateway-cina=true, gateway-india=true, order-command-apac=true, order-query-apac=true, order-query-cina=true, order-query-global-apac=true, order-saga-apac=true}
- +59,4 s: scenario start
- +142,0 s: cleanup start
- +152,2 s: cleanup done

### log excerpts: evento-eu

```
java.lang.IllegalStateException: EventFetchRequest expired: age=40018ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40014ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40002ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40018ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40014ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40002ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
```

### log excerpts: order-query-eu

```
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: EventFetchRequest expired: age=40014ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
Caused by: java.lang.IllegalStateException: EventFetchRequest expired: age=40014ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: EventFetchRequest expired: age=40018ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: EventFetchRequest expired: age=40014ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
	at com.evento.common.messaging.consumer.ConsumerProcessor.fetchEvents(ConsumerProcessor.java:1072) ~[evento-common-2.4.6.jar!/:na]
Caused by: java.lang.IllegalStateException: EventFetchRequest expired: age=40014ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
```

### stack actions

```
2026-09-29T16:00:41.560067Z docker restart gateway-australia-nz gateway-cina gateway-india order-command-apac order-query-apac order-query-cina order-query-global-apac order-saga-apac
2026-09-29T16:01:37.699157900Z compose up -d --no-deps --force-recreate order-query-eu with env {FAKETIME_DONT_FAKE_MONOTONIC=1, LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1, FAKETIME=-40s} image evento-lab-cluster/order-query:after-faketime
2026-09-29T16:03:00.291381200Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

