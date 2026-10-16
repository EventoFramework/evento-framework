# S10: Cold start of 3 servers on an empty event store

- Label: `after-query`
- Gaps exercised: G14
- Started: 2026-10-15T13:29:15.935700500Z, finished: 2026-10-15T13:31:59.550224700Z (163 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s10-after-query-1fd463-`
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
    }
  }, {
    "iteration" : 2,
    "servers" : {
      "evento-eu" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 26
      },
      "evento-am" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 5
      },
      "evento-apac" : {
        "state" : "running",
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 5
      },
      "allStarted" : true
    }
  }, {
    "iteration" : 3,
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
    }
  } ]
}
```

## Timeline

- +8,0 s: scenario start
- +8,0 s: iteration 1: stop servers
- +19,0 s: iteration 1: start the 3 servers together on an empty schema
- +57,1 s: iteration 1: allStarted=true
- +57,1 s: iteration 2: stop servers
- +67,9 s: iteration 2: start the 3 servers together on an empty schema
- +105,8 s: iteration 2: allStarted=true
- +105,8 s: iteration 3: stop servers
- +116,6 s: iteration 3: start the 3 servers together on an empty schema
- +154,5 s: iteration 3: allStarted=true
- +154,5 s: cleanup start
- +154,5 s: reset app state: stop app JVMs, truncate consumer state and read models, start again
- +163,6 s: cleanup done

### stack actions

```
2026-10-15T13:29:23.911019200Z docker stop evento-am
2026-10-15T13:29:23.911019200Z docker stop evento-apac
2026-10-15T13:29:23.911019200Z docker stop evento-eu
2026-10-15T13:29:34.565505500Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-15T13:29:34.930262100Z docker start evento-eu
2026-10-15T13:29:34.930778300Z docker start evento-am
2026-10-15T13:29:34.930778300Z docker start evento-apac
2026-10-15T13:30:12.999594800Z docker stop evento-am
2026-10-15T13:30:12.999594800Z docker stop evento-eu
2026-10-15T13:30:13.000114500Z docker stop evento-apac
2026-10-15T13:30:23.590506900Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-15T13:30:23.866430200Z docker start evento-am
2026-10-15T13:30:23.866430200Z docker start evento-apac
2026-10-15T13:30:23.866430200Z docker start evento-eu
2026-10-15T13:31:01.771109Z docker stop evento-am
2026-10-15T13:31:01.771109Z docker stop evento-apac
2026-10-15T13:31:01.771109Z docker stop evento-eu
2026-10-15T13:31:12.237864500Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-15T13:31:12.532117800Z docker start evento-am
2026-10-15T13:31:12.532117800Z docker start evento-apac
2026-10-15T13:31:12.532117800Z docker start evento-eu
2026-10-15T13:31:50.403517Z docker stop gateway-africa
2026-10-15T13:31:50.403517Z docker stop gateway-america
2026-10-15T13:31:50.403517Z docker stop gateway-india
2026-10-15T13:31:50.403517Z docker stop order-command-am
2026-10-15T13:31:50.403517Z docker stop order-command-eu
2026-10-15T13:31:50.403517Z docker stop order-observer
2026-10-15T13:31:50.403517Z docker stop order-query-eu
2026-10-15T13:31:50.403517Z docker stop order-query-global-apac
2026-10-15T13:31:50.403517Z docker stop order-query-global-eu
2026-10-15T13:31:50.403517Z docker stop order-query-wildcard
2026-10-15T13:31:50.404516900Z docker stop gateway-europa
2026-10-15T13:31:50.404516900Z docker stop order-command-apac
2026-10-15T13:31:50.404516900Z docker stop order-query-am
2026-10-15T13:31:50.404516900Z docker stop order-saga-am
2026-10-15T13:31:50.404516900Z docker stop order-saga-eu
2026-10-15T13:31:50.408520Z docker stop gateway-sud-america
2026-10-15T13:31:50.419515700Z docker stop order-query-cina
2026-10-15T13:31:50.490719Z docker stop order-query-apac
2026-10-15T13:31:50.522724700Z docker stop order-saga-apac
2026-10-15T13:31:50.530355600Z docker stop gateway-cina
2026-10-15T13:31:50.554172Z docker stop gateway-australia-nz
2026-10-15T13:31:54.049929400Z docker start gateway-america
2026-10-15T13:31:54.049929400Z docker start gateway-cina
2026-10-15T13:31:54.049929400Z docker start gateway-india
2026-10-15T13:31:54.049929400Z docker start gateway-sud-america
2026-10-15T13:31:54.049929400Z docker start order-command-am
2026-10-15T13:31:54.049929400Z docker start order-command-apac
2026-10-15T13:31:54.049929400Z docker start order-command-eu
2026-10-15T13:31:54.049929400Z docker start order-query-cina
2026-10-15T13:31:54.049929400Z docker start order-query-eu
2026-10-15T13:31:54.049929400Z docker start order-query-wildcard
2026-10-15T13:31:54.049929400Z docker start order-saga-am
2026-10-15T13:31:54.049929400Z docker start order-saga-apac
2026-10-15T13:31:54.050925600Z docker start gateway-africa
2026-10-15T13:31:54.050925600Z docker start gateway-europa
2026-10-15T13:31:54.050925600Z docker start order-observer
2026-10-15T13:31:54.050925600Z docker start order-query-am
2026-10-15T13:31:54.050925600Z docker start order-query-apac
2026-10-15T13:31:54.050925600Z docker start order-query-global-eu
2026-10-15T13:31:54.050925600Z docker start order-saga-eu
2026-10-15T13:31:54.051926500Z docker start gateway-australia-nz
2026-10-15T13:31:54.051926500Z docker start order-query-global-apac
```

