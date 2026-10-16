# S10: Cold start of 3 servers on an empty event store

- Label: `after-versioning`
- Gaps exercised: G14
- Started: 2026-10-15T23:58:06.473971600Z, finished: 2026-10-16T00:00:49.818271700Z (163 s)
- Verdicts: {AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s10-after-versioning-df9993-`
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
    "iteration" : 3
  } ]
}
```

## Timeline

- +8,2 s: scenario start
- +8,2 s: iteration 1: stop servers
- +19,0 s: iteration 1: start the 3 servers together on an empty schema
- +56,8 s: iteration 1: allStarted=true
- +56,8 s: iteration 2: stop servers
- +67,8 s: iteration 2: start the 3 servers together on an empty schema
- +105,5 s: iteration 2: allStarted=true
- +105,5 s: iteration 3: stop servers
- +116,4 s: iteration 3: start the 3 servers together on an empty schema
- +154,4 s: iteration 3: allStarted=true
- +154,4 s: cleanup start
- +154,4 s: reset app state: stop app JVMs, truncate consumer state and read models, start again
- +163,3 s: cleanup done

### stack actions

```
2026-10-15T23:58:14.643336300Z docker stop evento-apac
2026-10-15T23:58:14.644336300Z docker stop evento-am
2026-10-15T23:58:14.644336300Z docker stop evento-eu
2026-10-15T23:58:25.179196500Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-15T23:58:25.515359800Z docker start evento-am
2026-10-15T23:58:25.515359800Z docker start evento-apac
2026-10-15T23:58:25.515359800Z docker start evento-eu
2026-10-15T23:59:03.282040900Z docker stop evento-am
2026-10-15T23:59:03.282040900Z docker stop evento-apac
2026-10-15T23:59:03.282040900Z docker stop evento-eu
2026-10-15T23:59:13.935723100Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-15T23:59:14.257130400Z docker start evento-am
2026-10-15T23:59:14.257130400Z docker start evento-apac
2026-10-15T23:59:14.257130400Z docker start evento-eu
2026-10-15T23:59:51.987056900Z docker stop evento-am
2026-10-15T23:59:51.987056900Z docker stop evento-apac
2026-10-15T23:59:51.987056900Z docker stop evento-eu
2026-10-16T00:00:02.543413900Z psql es-pg-eu/evento: DROP SCHEMA public CASCADE; CREATE SCHEMA public;
2026-10-16T00:00:02.829373100Z docker start evento-am
2026-10-16T00:00:02.829373100Z docker start evento-apac
2026-10-16T00:00:02.829373100Z docker start evento-eu
2026-10-16T00:00:40.848411800Z docker stop gateway-africa
2026-10-16T00:00:40.848411800Z docker stop gateway-europa
2026-10-16T00:00:40.848411800Z docker stop gateway-sud-america
2026-10-16T00:00:40.848411800Z docker stop order-command-am
2026-10-16T00:00:40.848411800Z docker stop order-command-eu
2026-10-16T00:00:40.848411800Z docker stop order-observer
2026-10-16T00:00:40.848411800Z docker stop order-query-apac
2026-10-16T00:00:40.848411800Z docker stop order-query-cina
2026-10-16T00:00:40.848411800Z docker stop order-query-eu
2026-10-16T00:00:40.848411800Z docker stop order-query-global-eu
2026-10-16T00:00:40.848411800Z docker stop order-query-wildcard
2026-10-16T00:00:40.848411800Z docker stop order-saga-am
2026-10-16T00:00:40.848411800Z docker stop order-saga-eu
2026-10-16T00:00:40.852931200Z docker stop order-query-global-apac
2026-10-16T00:00:40.857932200Z docker stop order-query-am
2026-10-16T00:00:40.897251800Z docker stop order-saga-apac
2026-10-16T00:00:40.916157100Z docker stop gateway-cina
2026-10-16T00:00:40.918173600Z docker stop gateway-india
2026-10-16T00:00:40.930624700Z docker stop gateway-australia-nz
2026-10-16T00:00:40.971399800Z docker stop order-command-apac
2026-10-16T00:00:41.008028300Z docker stop gateway-america
2026-10-16T00:00:44.620995900Z docker start gateway-europa
2026-10-16T00:00:44.620995900Z docker start order-command-eu
2026-10-16T00:00:44.620995900Z docker start order-query-eu
2026-10-16T00:00:44.620995900Z docker start order-query-global-eu
2026-10-16T00:00:44.620995900Z docker start order-query-wildcard
2026-10-16T00:00:44.621995100Z docker start order-command-am
2026-10-16T00:00:44.621995100Z docker start order-observer
2026-10-16T00:00:44.621995100Z docker start order-query-am
2026-10-16T00:00:44.621995100Z docker start order-saga-eu
2026-10-16T00:00:44.631999300Z docker start order-saga-am
2026-10-16T00:00:44.647573200Z docker start gateway-africa
2026-10-16T00:00:44.654674900Z docker start gateway-sud-america
2026-10-16T00:00:44.661302200Z docker start order-command-apac
2026-10-16T00:00:44.669304300Z docker start order-query-cina
2026-10-16T00:00:44.677303400Z docker start gateway-america
2026-10-16T00:00:44.686985Z docker start order-query-apac
2026-10-16T00:00:44.698640500Z docker start order-query-global-apac
2026-10-16T00:00:44.705862900Z docker start order-saga-apac
2026-10-16T00:00:44.715311Z docker start gateway-cina
2026-10-16T00:00:44.723491100Z docker start gateway-india
2026-10-16T00:00:44.751832500Z docker start gateway-australia-nz
```

