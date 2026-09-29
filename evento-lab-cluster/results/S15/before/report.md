# S15: Clock skew on one region JVM (optional)

- Label: `before`
- Gaps exercised: fetch staleness gate (Low), G20
- Started: 2026-09-29T14:00:26.209223200Z, finished: 2026-09-29T14:02:00.021102300Z (93 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s15-before-1230ab-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- skewEnv: `{LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1, FAKETIME=-40s, FAKETIME_DONT_FAKE_MONOTONIC=1}`
- skewImage: `evento-lab-cluster/order-query:before-faketime`
- skewedClockMinusHostSeconds: `-40`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| skewed region consumer | **KNOWN_DEFECT_REPRODUCED** | the AFRICA projector (clock +skew) still projects its orders within 60 s | the staleness gate drops the skewed consumer's fetches: the orders never appear | orders not projected within 60 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "logMarkers" : {
    "evento-eu" : {
      "stale" : 118,
      "fetch" : 118
    },
    "order-query-eu" : {
      "stale" : 236,
      "fetch" : 357
    }
  }
}
```

## Timeline

- +1,5 s: scenario start
- +84,2 s: cleanup start
- +93,8 s: cleanup done

### log excerpts: evento-eu

```
java.lang.IllegalStateException: EventFetchRequest expired: age=40004ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40006ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40013ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40004ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40006ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.lang.IllegalStateException: EventFetchRequest expired: age=40013ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
```

### log excerpts: order-query-eu

```
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: EventFetchRequest expired: age=40004ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
Caused by: java.lang.IllegalStateException: EventFetchRequest expired: age=40004ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: EventFetchRequest expired: age=40006ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
java.util.concurrent.ExecutionException: java.lang.IllegalStateException: EventFetchRequest expired: age=40004ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
	at com.evento.common.messaging.consumer.ConsumerProcessor.fetchEvents(ConsumerProcessor.java:1032) ~[evento-common-2.4.6.jar!/:na]
Caused by: java.lang.IllegalStateException: EventFetchRequest expired: age=40004ms > max=30000ms; the requesting consumer has already timed out, dropping stale fetch
```

### stack actions

```
2026-09-29T14:00:27.753870400Z compose up -d --no-deps --force-recreate order-query-eu with env {LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1, FAKETIME=-40s, FAKETIME_DONT_FAKE_MONOTONIC=1} image evento-lab-cluster/order-query:before-faketime
2026-09-29T14:01:50.411465800Z compose up -d --no-deps --force-recreate order-query-eu (restore default)
```

