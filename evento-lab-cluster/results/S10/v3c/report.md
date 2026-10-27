# S10: Cold start of 3 servers on an empty event store

- Label: `v3c`
- Gaps exercised: G14
- Started: 2026-10-22T12:42:33.765065700Z, finished: 2026-10-22T12:45:46.367100900Z (192 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s10-v3c-61a253-`
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
    "iteration" : 1,
    "servers" : {
      "evento-eu" : {
        "ready" : true,
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 27
      },
      "evento-am" : {
        "ready" : true,
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 20
      },
      "evento-apac" : {
        "ready" : true,
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 0
      },
      "allStarted" : true
    }
  }, {
    "iteration" : 2,
    "servers" : {
      "evento-eu" : {
        "ready" : true,
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 48
      },
      "evento-am" : {
        "ready" : true,
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 0
      },
      "evento-apac" : {
        "ready" : true,
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 12
      },
      "allStarted" : true
    }
  }, {
    "iteration" : 3,
    "servers" : {
      "evento-eu" : {
        "ready" : true,
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 22
      },
      "evento-am" : {
        "ready" : true,
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 9
      },
      "evento-apac" : {
        "ready" : true,
        "markers" : { },
        "state" : "running",
        "waitedSeconds" : 14
      },
      "allStarted" : true
    }
  } ]
}
```

## Timeline

- +9,7 s: scenario start
- +9,7 s: iteration 1: stop servers
- +11,5 s: iteration 1: start the 3 servers together on an empty schema
- +60,5 s: iteration 1: allStarted=true
- +60,5 s: iteration 2: stop servers
- +62,0 s: iteration 2: start the 3 servers together on an empty schema
- +124,5 s: iteration 2: allStarted=true
- +124,5 s: iteration 3: stop servers
- +125,8 s: iteration 3: start the 3 servers together on an empty schema
- +173,6 s: iteration 3: allStarted=true
- +173,6 s: cleanup start
- +173,6 s: reset app state: stop app JVMs, truncate consumer state and read models, start again
- +192,6 s: cleanup done

### stack actions

```
2026-10-22T12:42:43.461446800Z docker stop evento-am
2026-10-22T12:42:43.461446800Z docker stop evento-apac
2026-10-22T12:42:43.461446800Z docker stop evento-eu
2026-10-22T12:42:44.885447600Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-22T12:42:45.232672300Z docker start evento-am
2026-10-22T12:42:45.232672300Z docker start evento-apac
2026-10-22T12:42:45.232672300Z docker start evento-eu
2026-10-22T12:43:34.314781900Z docker stop evento-am
2026-10-22T12:43:34.314781900Z docker stop evento-apac
2026-10-22T12:43:34.314781900Z docker stop evento-eu
2026-10-22T12:43:35.493040600Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-22T12:43:35.798884500Z docker start evento-am
2026-10-22T12:43:35.799429800Z docker start evento-apac
2026-10-22T12:43:35.799429800Z docker start evento-eu
2026-10-22T12:44:38.279627200Z docker stop evento-am
2026-10-22T12:44:38.280150600Z docker stop evento-apac
2026-10-22T12:44:38.280150600Z docker stop evento-eu
2026-10-22T12:44:39.208522Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-22T12:44:39.555563600Z docker start evento-am
2026-10-22T12:44:39.556075500Z docker start evento-apac
2026-10-22T12:44:39.556075500Z docker start evento-eu
2026-10-22T12:45:27.327900Z docker stop gateway-europa
2026-10-22T12:45:27.327900Z docker stop order-command-am
2026-10-22T12:45:27.327900Z docker stop order-command-apac
2026-10-22T12:45:27.327900Z docker stop order-query-apac
2026-10-22T12:45:27.327900Z docker stop order-query-cina
2026-10-22T12:45:27.327900Z docker stop order-query-global-eu
2026-10-22T12:45:27.327900Z docker stop order-query-wildcard
2026-10-22T12:45:27.327900Z docker stop order-saga-am
2026-10-22T12:45:27.328908400Z docker stop gateway-africa
2026-10-22T12:45:27.328908400Z docker stop gateway-australia-nz
2026-10-22T12:45:27.328908400Z docker stop gateway-india
2026-10-22T12:45:27.328908400Z docker stop order-command-eu
2026-10-22T12:45:27.328908400Z docker stop order-observer
2026-10-22T12:45:27.328908400Z docker stop order-query-am
2026-10-22T12:45:27.328908400Z docker stop order-query-eu
2026-10-22T12:45:27.328908400Z docker stop order-saga-apac
2026-10-22T12:45:27.328908400Z docker stop order-saga-eu
2026-10-22T12:45:27.371028Z docker stop gateway-america
2026-10-22T12:45:27.414208100Z docker stop gateway-sud-america
2026-10-22T12:45:27.461499500Z docker stop order-query-global-apac
2026-10-22T12:45:27.482402400Z docker stop gateway-cina
2026-10-22T12:45:39.051964900Z docker start order-query-eu
2026-10-22T12:45:39.052962200Z docker start order-command-eu
2026-10-22T12:45:39.052962200Z docker start order-observer
2026-10-22T12:45:39.052962200Z docker start order-query-wildcard
2026-10-22T12:45:39.052962200Z docker start order-saga-eu
2026-10-22T12:45:39.053967200Z docker start gateway-europa
2026-10-22T12:45:39.056962200Z docker start order-query-global-eu
2026-10-22T12:45:39.060964100Z docker start gateway-africa
2026-10-22T12:45:39.064968200Z docker start order-query-am
2026-10-22T12:45:39.072947900Z docker start order-command-am
2026-10-22T12:45:39.078860Z docker start gateway-america
2026-10-22T12:45:39.086163700Z docker start order-saga-am
2026-10-22T12:45:39.093166Z docker start gateway-sud-america
2026-10-22T12:45:39.101165700Z docker start order-query-apac
2026-10-22T12:45:39.111164500Z docker start order-command-apac
2026-10-22T12:45:39.120146800Z docker start order-query-global-apac
2026-10-22T12:45:39.129104100Z docker start order-saga-apac
2026-10-22T12:45:39.139822500Z docker start gateway-india
2026-10-22T12:45:39.148420Z docker start gateway-australia-nz
2026-10-22T12:45:39.157420600Z docker start gateway-cina
2026-10-22T12:45:39.173282Z docker start order-query-cina
```

