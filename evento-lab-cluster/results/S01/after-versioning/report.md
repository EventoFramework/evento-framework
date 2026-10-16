# S01: Cross-cell routing miss

- Label: `after-versioning`
- Gaps exercised: G1
- Started: 2026-10-16T00:07:30.790690300Z, finished: 2026-10-16T00:08:00.425338800Z (29 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s01-after-versioning-31fb3d-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| AMERICA command with no handler in its cell | **KNOWN_DEFECT_REPRODUCED** | routed to a live handler in EU or APAC and acknowledged (needs the G1 server mesh) | fails immediately with 'no handler for CreateOrderCommand' | 0/5 acknowledged; 5 no_handler; kinds {no_handler=5} |
| EUROPA control | **AS_EXPECTED** | unaffected: all acknowledged |  | 5/5 |
| failure is immediate | **AS_EXPECTED** | a routing miss fails fast (< 2 s), not after a timeout |  | max latency 121 ms |

- _AMERICA command with no handler in its cell_: The cell architecture exists because of this: every cell must host a full handler set.

## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "americaKinds" : {
    "no_handler" : 5
  },
  "americaErrors" : [ "no handler for CreateOrderCommand" ],
  "americaMaxLatencyMs" : 121,
  "logMarkers" : {
    "evento-am" : {
      "Skipping unregister of bundle" : 1
    },
    "gateway-america" : {
      "no handler for" : 5,
      "event=gateway_command_failed" : 5
    }
  },
  "americaRecoveredAfterRestartSeconds" : 0.842
}
```

## Timeline

- +8,0 s: scenario start
- +8,7 s: stopped order-command-am
- +19,7 s: sent 5 CreateOrder from AMERICA and EUROPA
- +19,9 s: cleanup start
- +29,6 s: cleanup done

### log excerpts: evento-am

```
20261016000739788 INFO  1 --- [nioEventLoopGroup-3-7                   ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: gateway-america

```
2026-10-16T00:07:49.589Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-versioning-31fb3d-america-1 region=AMERICA kind=no_handler latencyMs=33 idempotencyKey=5ced9b3a-d29f-49d9-ad81-a27b74b69938 error=no handler for CreateOrderCommand
2026-10-16T00:07:49.894Z  WARN 1 --- [order-gateway] [mcat-handler-49] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-versioning-31fb3d-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=20503197-fa5b-4493-b4d4-492a320c904e error=no handler for CreateOrderCommand
2026-10-16T00:07:50.058Z  WARN 1 --- [order-gateway] [mcat-handler-50] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-versioning-31fb3d-america-5 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=0dad63f4-eb16-4fe0-8ad9-8dfa402a5e6e error=no handler for CreateOrderCommand
2026-10-16T00:07:49.589Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-versioning-31fb3d-america-1 region=AMERICA kind=no_handler latencyMs=33 idempotencyKey=5ced9b3a-d29f-49d9-ad81-a27b74b69938 error=no handler for CreateOrderCommand
2026-10-16T00:07:49.894Z  WARN 1 --- [order-gateway] [mcat-handler-49] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-versioning-31fb3d-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=20503197-fa5b-4493-b4d4-492a320c904e error=no handler for CreateOrderCommand
2026-10-16T00:07:50.058Z  WARN 1 --- [order-gateway] [mcat-handler-50] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-versioning-31fb3d-america-5 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=0dad63f4-eb16-4fe0-8ad9-8dfa402a5e6e error=no handler for CreateOrderCommand
```

### stack actions

```
2026-10-16T00:07:38.779065200Z docker stop order-command-am
2026-10-16T00:07:50.735154200Z docker start order-command-am (restore)
```

