# S10: Cold start of 3 servers on an empty event store

- Label: `v3e`
- Gaps exercised: G14
- Started: 2026-10-22T15:41:13.587645600Z, finished: 2026-10-22T15:43:46.974845400Z (153 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s10-v3e-822190-`
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
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 22,
        "ready" : true
      },
      "evento-am" : {
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 19,
        "ready" : true
      },
      "evento-apac" : {
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 0,
        "ready" : true
      },
      "allStarted" : true
    },
    "iteration" : 1
  }, {
    "servers" : {
      "evento-eu" : {
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 22,
        "ready" : true
      },
      "evento-am" : {
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 9,
        "ready" : true
      },
      "evento-apac" : {
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 14,
        "ready" : true
      },
      "allStarted" : true
    },
    "iteration" : 2
  }, {
    "servers" : {
      "evento-eu" : {
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 22,
        "ready" : true
      },
      "evento-am" : {
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 19,
        "ready" : true
      },
      "evento-apac" : {
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 0,
        "ready" : true
      },
      "allStarted" : true
    },
    "iteration" : 3
  } ]
}
```

## Timeline

- +7,8 s: scenario start
- +7,8 s: iteration 1: stop servers
- +9,5 s: iteration 1: start the 3 servers together on an empty schema
- +52,2 s: iteration 1: allStarted=true
- +52,2 s: iteration 2: stop servers
- +53,4 s: iteration 2: start the 3 servers together on an empty schema
- +100,6 s: iteration 2: allStarted=true
- +100,6 s: iteration 3: stop servers
- +101,8 s: iteration 3: start the 3 servers together on an empty schema
- +144,4 s: iteration 3: allStarted=true
- +144,4 s: cleanup start
- +144,4 s: reset app state: stop app JVMs, truncate consumer state and read models, start again
- +153,4 s: cleanup done

### stack actions

```
2026-10-22T15:41:21.406969200Z docker stop evento-apac
2026-10-22T15:41:21.406969200Z docker stop evento-eu
2026-10-22T15:41:21.407969200Z docker stop evento-am
2026-10-22T15:41:22.816015300Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-22T15:41:23.131133800Z docker start evento-am
2026-10-22T15:41:23.131133800Z docker start evento-eu
2026-10-22T15:41:23.131663600Z docker start evento-apac
2026-10-22T15:42:05.757637300Z docker stop evento-eu
2026-10-22T15:42:05.758144500Z docker stop evento-am
2026-10-22T15:42:05.758172600Z docker stop evento-apac
2026-10-22T15:42:06.646023500Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-22T15:42:06.965576900Z docker start evento-am
2026-10-22T15:42:06.965576900Z docker start evento-apac
2026-10-22T15:42:06.965576900Z docker start evento-eu
2026-10-22T15:42:54.182516300Z docker stop evento-am
2026-10-22T15:42:54.182516300Z docker stop evento-apac
2026-10-22T15:42:54.182516300Z docker stop evento-eu
2026-10-22T15:42:55.109960200Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-22T15:42:55.414337600Z docker start evento-am
2026-10-22T15:42:55.414337600Z docker start evento-apac
2026-10-22T15:42:55.414337600Z docker start evento-eu
2026-10-22T15:43:37.970670500Z docker stop gateway-europa
2026-10-22T15:43:37.970670500Z docker stop gateway-india
2026-10-22T15:43:37.970670500Z docker stop gateway-sud-america
2026-10-22T15:43:37.970670500Z docker stop order-command-eu
2026-10-22T15:43:37.970670500Z docker stop order-query-am
2026-10-22T15:43:37.970670500Z docker stop order-query-apac
2026-10-22T15:43:37.970670500Z docker stop order-query-cina
2026-10-22T15:43:37.970670500Z docker stop order-query-global-apac
2026-10-22T15:43:37.970670500Z docker stop order-saga-apac
2026-10-22T15:43:37.971671Z docker stop gateway-africa
2026-10-22T15:43:37.971671Z docker stop order-command-apac
2026-10-22T15:43:37.971671Z docker stop order-query-eu
2026-10-22T15:43:37.971671Z docker stop order-query-global-eu
2026-10-22T15:43:37.971671Z docker stop order-query-wildcard
2026-10-22T15:43:37.976673500Z docker stop order-observer
2026-10-22T15:43:38.039046200Z docker stop order-command-am
2026-10-22T15:43:38.112452900Z docker stop order-saga-am
2026-10-22T15:43:38.181077100Z docker stop order-saga-eu
2026-10-22T15:43:38.201456600Z docker stop gateway-america
2026-10-22T15:43:38.217288500Z docker stop gateway-cina
2026-10-22T15:43:38.251461500Z docker stop gateway-australia-nz
2026-10-22T15:43:41.792565700Z docker start order-observer
2026-10-22T15:43:41.793080300Z docker start order-command-apac
2026-10-22T15:43:41.793196400Z docker start gateway-australia-nz
2026-10-22T15:43:41.793196400Z docker start gateway-cina
2026-10-22T15:43:41.793196400Z docker start order-command-eu
2026-10-22T15:43:41.793196400Z docker start order-query-cina
2026-10-22T15:43:41.793196400Z docker start order-query-eu
2026-10-22T15:43:41.793196400Z docker start order-query-global-apac
2026-10-22T15:43:41.793196400Z docker start order-query-global-eu
2026-10-22T15:43:41.793196400Z docker start order-query-wildcard
2026-10-22T15:43:41.793743400Z docker start gateway-europa
2026-10-22T15:43:41.793979700Z docker start gateway-africa
2026-10-22T15:43:41.793979700Z docker start order-command-am
2026-10-22T15:43:41.793979700Z docker start order-query-apac
2026-10-22T15:43:41.793979700Z docker start order-saga-am
2026-10-22T15:43:41.807242800Z docker start order-query-am
2026-10-22T15:43:41.821435600Z docker start order-saga-apac
2026-10-22T15:43:41.836434300Z docker start gateway-india
2026-10-22T15:43:41.856093200Z docker start order-saga-eu
2026-10-22T15:43:41.873619400Z docker start gateway-america
2026-10-22T15:43:41.891936Z docker start gateway-sud-america
```

