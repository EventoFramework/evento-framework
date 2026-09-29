# S10: Cold start of 3 servers on an empty event store

- Label: `after`
- Gaps exercised: G14
- Started: 2026-09-29T16:22:18.841181500Z, finished: 2026-09-29T16:25:05.098790900Z (166 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s10-after-f84aa7-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`
- iterations: `3`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| all three servers start, every time | **AS_EXPECTED** | 3/3 servers healthy on every iteration, no DDL race in the logs | occasional boot failures with pg_type_typname_nsp_index / tuple concurrently updated (G14) | 0/3 iterations with a failed server; 0 schema-race log lines |

- _all three servers start, every time_: A clean run does not prove absence of the race; raise S10_ITERATIONS for evidence.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "iterations" : [ {
    "servers" : {
      "evento-eu" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 22
      },
      "evento-am" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 14
      },
      "evento-apac" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 0
      },
      "allStarted" : true
    },
    "iteration" : 1
  }, {
    "servers" : {
      "evento-eu" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 22
      },
      "evento-am" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 14
      },
      "evento-apac" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 0
      },
      "allStarted" : true
    },
    "iteration" : 2
  }, {
    "servers" : {
      "evento-eu" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 22
      },
      "evento-am" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 9
      },
      "evento-apac" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 5
      },
      "allStarted" : true
    },
    "iteration" : 3
  } ]
}
```

## Timeline

- +7,9 s: scenario start
- +7,9 s: iteration 1: stop servers
- +18,8 s: iteration 1: start the 3 servers together on an empty schema
- +56,6 s: iteration 1: allStarted=true
- +56,6 s: iteration 2: stop servers
- +63,0 s: iteration 2: start the 3 servers together on an empty schema
- +100,6 s: iteration 2: allStarted=true
- +100,6 s: iteration 3: stop servers
- +111,4 s: iteration 3: start the 3 servers together on an empty schema
- +149,0 s: iteration 3: allStarted=true
- +149,0 s: cleanup start
- +149,0 s: reset app state: stop app JVMs, truncate consumer state and read models, start again
- +166,3 s: cleanup done

### stack actions

```
2026-09-29T16:22:26.731966700Z docker stop evento-am
2026-09-29T16:22:26.731966700Z docker stop evento-eu
2026-09-29T16:22:26.732964200Z docker stop evento-apac
2026-09-29T16:22:37.299494Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-09-29T16:22:37.621708700Z docker start evento-am
2026-09-29T16:22:37.621708700Z docker start evento-apac
2026-09-29T16:22:37.621708700Z docker start evento-eu
2026-09-29T16:23:15.484521700Z docker stop evento-am
2026-09-29T16:23:15.484521700Z docker stop evento-apac
2026-09-29T16:23:15.484521700Z docker stop evento-eu
2026-09-29T16:23:21.592463500Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-09-29T16:23:21.887159Z docker start evento-am
2026-09-29T16:23:21.887194500Z docker start evento-apac
2026-09-29T16:23:21.887194500Z docker start evento-eu
2026-09-29T16:23:59.488766800Z docker stop evento-am
2026-09-29T16:23:59.488766800Z docker stop evento-apac
2026-09-29T16:23:59.488766800Z docker stop evento-eu
2026-09-29T16:24:09.940781400Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-09-29T16:24:10.214434200Z docker start evento-am
2026-09-29T16:24:10.214434200Z docker start evento-apac
2026-09-29T16:24:10.214434200Z docker start evento-eu
2026-09-29T16:24:47.857224100Z docker stop gateway-india
2026-09-29T16:24:47.857224100Z docker stop order-query-eu
2026-09-29T16:24:47.857224100Z docker stop order-query-global-eu
2026-09-29T16:24:47.857224100Z docker stop order-saga-am
2026-09-29T16:24:47.857224100Z docker stop order-saga-eu
2026-09-29T16:24:47.858225300Z docker stop gateway-africa
2026-09-29T16:24:47.858225300Z docker stop gateway-europa
2026-09-29T16:24:47.858225300Z docker stop gateway-sud-america
2026-09-29T16:24:47.858225300Z docker stop order-command-am
2026-09-29T16:24:47.858225300Z docker stop order-command-eu
2026-09-29T16:24:47.858225300Z docker stop order-observer
2026-09-29T16:24:47.858225300Z docker stop order-query-am
2026-09-29T16:24:47.858225300Z docker stop order-query-wildcard
2026-09-29T16:24:47.896033200Z docker stop gateway-america
2026-09-29T16:24:47.937098100Z docker stop order-command-apac
2026-09-29T16:24:47.945088Z docker stop order-query-cina
2026-09-29T16:24:48.005657200Z docker stop order-query-global-apac
2026-09-29T16:24:48.018585600Z docker stop gateway-cina
2026-09-29T16:24:48.024745700Z docker stop order-saga-apac
2026-09-29T16:24:48.035913800Z docker stop gateway-australia-nz
2026-09-29T16:24:48.061458700Z docker stop order-query-apac
2026-09-29T16:24:59.891356400Z docker start order-command-eu
2026-09-29T16:24:59.891356400Z docker start order-query-eu
2026-09-29T16:24:59.891356400Z docker start order-saga-eu
2026-09-29T16:24:59.891864600Z docker start order-query-global-eu
2026-09-29T16:24:59.891864600Z docker start order-query-wildcard
2026-09-29T16:24:59.891960400Z docker start gateway-africa
2026-09-29T16:24:59.891960400Z docker start gateway-europa
2026-09-29T16:24:59.891960400Z docker start order-command-am
2026-09-29T16:24:59.891960400Z docker start order-observer
2026-09-29T16:24:59.892466Z docker start gateway-sud-america
2026-09-29T16:24:59.892466Z docker start order-command-apac
2026-09-29T16:24:59.892466Z docker start order-query-am
2026-09-29T16:24:59.892466Z docker start order-saga-am
2026-09-29T16:24:59.903916300Z docker start order-query-cina
2026-09-29T16:24:59.916527500Z docker start order-query-apac
2026-09-29T16:24:59.930528100Z docker start order-query-global-apac
2026-09-29T16:24:59.956715500Z docker start order-saga-apac
2026-09-29T16:24:59.976808700Z docker start gateway-cina
2026-09-29T16:24:59.990146Z docker start gateway-australia-nz
2026-09-29T16:25:00.006758300Z docker start gateway-india
2026-09-29T16:25:00.018757600Z docker start gateway-america
```

