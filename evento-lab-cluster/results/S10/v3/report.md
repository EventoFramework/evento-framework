# S10: Cold start of 3 servers on an empty event store

- Label: `v3`
- Gaps exercised: G14
- Started: 2026-10-20T14:56:50.106458500Z, finished: 2026-10-20T14:59:41.381998500Z (171 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s10-v3-e971d9-`
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
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 22,
        "state" : "running"
      },
      "evento-am" : {
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 9,
        "state" : "running"
      },
      "evento-apac" : {
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 9,
        "state" : "running"
      },
      "allStarted" : true
    }
  }, {
    "iteration" : 2,
    "servers" : {
      "evento-eu" : {
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 22,
        "state" : "running"
      },
      "evento-am" : {
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 9,
        "state" : "running"
      },
      "evento-apac" : {
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 9,
        "state" : "running"
      },
      "allStarted" : true
    }
  }, {
    "iteration" : 3,
    "servers" : {
      "evento-eu" : {
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 22,
        "state" : "running"
      },
      "evento-am" : {
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 9,
        "state" : "running"
      },
      "evento-apac" : {
        "markers" : { },
        "ready" : true,
        "waitedSeconds" : 9,
        "state" : "running"
      },
      "allStarted" : true
    }
  } ]
}
```

## Timeline

- +8,2 s: scenario start
- +8,2 s: iteration 1: stop servers
- +19,1 s: iteration 1: start the 3 servers together on an empty schema
- +61,8 s: iteration 1: allStarted=true
- +61,8 s: iteration 2: stop servers
- +72,6 s: iteration 2: start the 3 servers together on an empty schema
- +115,2 s: iteration 2: allStarted=true
- +115,2 s: iteration 3: stop servers
- +119,7 s: iteration 3: start the 3 servers together on an empty schema
- +162,1 s: iteration 3: allStarted=true
- +162,1 s: cleanup start
- +162,1 s: reset app state: stop app JVMs, truncate consumer state and read models, start again
- +171,3 s: cleanup done

### stack actions

```
2026-10-20T14:56:58.283437100Z docker stop evento-am
2026-10-20T14:56:58.283437100Z docker stop evento-apac
2026-10-20T14:56:58.283437100Z docker stop evento-eu
2026-10-20T14:57:08.877544700Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-20T14:57:09.226383700Z docker start evento-am
2026-10-20T14:57:09.226383700Z docker start evento-apac
2026-10-20T14:57:09.226383700Z docker start evento-eu
2026-10-20T14:57:51.886862Z docker stop evento-am
2026-10-20T14:57:51.886862Z docker stop evento-apac
2026-10-20T14:57:51.886862Z docker stop evento-eu
2026-10-20T14:58:02.368460800Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-20T14:58:02.700260500Z docker start evento-am
2026-10-20T14:58:02.700260500Z docker start evento-apac
2026-10-20T14:58:02.700260500Z docker start evento-eu
2026-10-20T14:58:45.317943100Z docker stop evento-am
2026-10-20T14:58:45.318448500Z docker stop evento-apac
2026-10-20T14:58:45.318473200Z docker stop evento-eu
2026-10-20T14:58:49.493390300Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-20T14:58:49.821021200Z docker start evento-eu
2026-10-20T14:58:49.821544100Z docker start evento-am
2026-10-20T14:58:49.822116500Z docker start evento-apac
2026-10-20T14:59:32.201484600Z docker stop gateway-africa
2026-10-20T14:59:32.201484600Z docker stop gateway-sud-america
2026-10-20T14:59:32.201484600Z docker stop order-command-apac
2026-10-20T14:59:32.201484600Z docker stop order-observer
2026-10-20T14:59:32.201484600Z docker stop order-query-apac
2026-10-20T14:59:32.201484600Z docker stop order-query-cina
2026-10-20T14:59:32.201484600Z docker stop order-query-eu
2026-10-20T14:59:32.201484600Z docker stop order-query-global-apac
2026-10-20T14:59:32.201484600Z docker stop order-saga-am
2026-10-20T14:59:32.201484600Z docker stop order-saga-apac
2026-10-20T14:59:32.201484600Z docker stop order-saga-eu
2026-10-20T14:59:32.201989700Z docker stop gateway-cina
2026-10-20T14:59:32.202019200Z docker stop gateway-australia-nz
2026-10-20T14:59:32.202019200Z docker stop order-command-eu
2026-10-20T14:59:32.202019200Z docker stop order-query-wildcard
2026-10-20T14:59:32.202196800Z docker stop gateway-europa
2026-10-20T14:59:32.202196800Z docker stop order-query-global-eu
2026-10-20T14:59:32.206848100Z docker stop order-command-am
2026-10-20T14:59:32.279046500Z docker stop order-query-am
2026-10-20T14:59:32.296164300Z docker stop gateway-america
2026-10-20T14:59:32.313171300Z docker stop gateway-india
2026-10-20T14:59:36.101324900Z docker start order-command-am
2026-10-20T14:59:36.101324900Z docker start order-query-global-apac
2026-10-20T14:59:36.101324900Z docker start order-query-global-eu
2026-10-20T14:59:36.101324900Z docker start order-query-wildcard
2026-10-20T14:59:36.102325800Z docker start gateway-africa
2026-10-20T14:59:36.102325800Z docker start gateway-europa
2026-10-20T14:59:36.102325800Z docker start order-command-eu
2026-10-20T14:59:36.102325800Z docker start order-observer
2026-10-20T14:59:36.102325800Z docker start order-query-eu
2026-10-20T14:59:36.102325800Z docker start order-saga-am
2026-10-20T14:59:36.102325800Z docker start order-saga-eu
2026-10-20T14:59:36.108327200Z docker start gateway-america
2026-10-20T14:59:36.119326800Z docker start order-query-am
2026-10-20T14:59:36.134699800Z docker start gateway-sud-america
2026-10-20T14:59:36.154370800Z docker start order-command-apac
2026-10-20T14:59:36.170587300Z docker start order-query-cina
2026-10-20T14:59:36.184923700Z docker start order-query-apac
2026-10-20T14:59:36.193375400Z docker start order-saga-apac
2026-10-20T14:59:36.203333200Z docker start gateway-cina
2026-10-20T14:59:36.210336500Z docker start gateway-india
2026-10-20T14:59:36.220342200Z docker start gateway-australia-nz
```

