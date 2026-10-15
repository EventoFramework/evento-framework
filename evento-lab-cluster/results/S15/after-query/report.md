# S15: Clock skew on one region JVM (optional)

- Label: `after-query`
- Gaps exercised: fetch staleness gate (Low), G20
- Started: 2026-10-15T14:29:26.256183700Z, finished: 2026-10-15T14:31:02.528434500Z (96 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s15-after-query-f87077-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- skewEnv: `{LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1, FAKETIME=-40s, FAKETIME_DONT_FAKE_MONOTONIC=1}`
- skewImage: `evento-lab-cluster/order-query:after-query-faketime`
- skewedClockMinusHostSeconds: `-40`

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
      "stale" : 121,
      "fetch" : 121
    },
    "order-query-eu" : {
      "stale" : 244,
      "fetch" : 368
    }
  }
}
```

## Timeline

- +3,5 s: scenario start
- +86,4 s: cleanup start
- +96,3 s: cleanup done

### log excerpts: evento-eu

```
java.lang.IllegalStateException: EventFetchRequest expired: age=40006ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40017ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40002ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40006ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40017ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40002ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
```

### log excerpts: order-query-eu

```
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: EventFetchRequest expired: age=40006ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
Caused by: java.lang.IllegalStateException: EventFetchRequest expired: age=40006ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: EventFetchRequest expired: age=40017ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: EventFetchRequest expired: age=40006ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
	at com.evento.common.messaging.consumer.ConsumerProcessor.fetchEvents(ConsumerProcessor.java:1106) ~[evento-common-2.5.2.jar!/:na]
Caused by: java.lang.IllegalStateException: EventFetchRequest expired: age=40006ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
```

### stack actions

```
2026-10-15T14:29:29.727999200Z compose up -d --no-deps --force-recreate order-query-eu with env {LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1, FAKETIME_DONT_FAKE_MONOTONIC=1, FAKETIME=-40s} image evento-lab-cluster/order-query:after-query-faketime
2026-10-15T14:30:52.669702600Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

