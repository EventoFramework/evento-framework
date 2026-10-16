# S01: Cross-cell routing miss

- Label: `after-query`
- Gaps exercised: G1
- Started: 2026-10-15T13:38:54.544695400Z, finished: 2026-10-15T13:39:24.226821200Z (29 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s01-after-query-3dcfed-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| AMERICA command with no handler in its cell | **KNOWN_DEFECT_REPRODUCED** | routed to a live handler in EU or APAC and acknowledged (needs the G1 server mesh) | fails immediately with 'no handler for CreateOrderCommand' | 0/5 acknowledged; 5 no_handler; kinds {no_handler=5} |
| EUROPA control | **AS_EXPECTED** | unaffected: all acknowledged |  | 5/5 |
| failure is immediate | **AS_EXPECTED** | a routing miss fails fast (< 2 s), not after a timeout |  | max latency 120 ms |

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
  "americaMaxLatencyMs" : 120,
  "logMarkers" : {
    "evento-am" : {
      "Skipping unregister of bundle" : 1
    },
    "gateway-america" : {
      "no handler for" : 5,
      "event=gateway_command_failed" : 5
    }
  },
  "americaRecoveredAfterRestartSeconds" : 0.85
}
```

## Timeline

- +8,0 s: scenario start
- +8,7 s: stopped order-command-am
- +19,7 s: sent 5 CreateOrder from AMERICA and EUROPA
- +19,9 s: cleanup start
- +29,7 s: cleanup done

### log excerpts: evento-am

```
20261015133903476 INFO  1 --- [nioEventLoopGroup-3-10                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: gateway-america

```
2026-10-15T13:39:13.324Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-query-3dcfed-america-1 region=AMERICA kind=no_handler latencyMs=35 idempotencyKey=d3134e65-80d5-474c-b9fa-aa13fe62607f error=no handler for CreateOrderCommand
2026-10-15T13:39:13.629Z  WARN 1 --- [order-gateway] [mcat-handler-49] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-query-3dcfed-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=1969e44d-7cea-4d01-a9c1-5a6c7172736c error=no handler for CreateOrderCommand
2026-10-15T13:39:13.793Z  WARN 1 --- [order-gateway] [mcat-handler-50] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-query-3dcfed-america-5 region=AMERICA kind=no_handler latencyMs=4 idempotencyKey=8e37b464-db63-45dd-a2f4-25cf9fdb5db8 error=no handler for CreateOrderCommand
2026-10-15T13:39:13.324Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-query-3dcfed-america-1 region=AMERICA kind=no_handler latencyMs=35 idempotencyKey=d3134e65-80d5-474c-b9fa-aa13fe62607f error=no handler for CreateOrderCommand
2026-10-15T13:39:13.629Z  WARN 1 --- [order-gateway] [mcat-handler-49] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-query-3dcfed-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=1969e44d-7cea-4d01-a9c1-5a6c7172736c error=no handler for CreateOrderCommand
2026-10-15T13:39:13.793Z  WARN 1 --- [order-gateway] [mcat-handler-50] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-after-query-3dcfed-america-5 region=AMERICA kind=no_handler latencyMs=4 idempotencyKey=8e37b464-db63-45dd-a2f4-25cf9fdb5db8 error=no handler for CreateOrderCommand
```

### stack actions

```
2026-10-15T13:39:02.533637900Z docker stop order-command-am
2026-10-15T13:39:14.533439Z docker start order-command-am (restore)
```

