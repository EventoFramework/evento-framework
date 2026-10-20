# S01: Cross-cell routing miss

- Label: `v3`
- Gaps exercised: G1
- Started: 2026-10-20T15:06:15.534487400Z, finished: 2026-10-20T15:06:44.703373100Z (29 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s01-v3-a751cb-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| AMERICA command with no handler in its cell | **KNOWN_DEFECT_REPRODUCED** | routed to a live handler in EU or APAC and acknowledged (needs the G1 server mesh) | fails immediately with 'no handler for CreateOrderCommand' | 0/5 acknowledged; 5 no_handler; kinds {no_handler=5} |
| EUROPA control | **AS_EXPECTED** | unaffected: all acknowledged |  | 5/5 |
| failure is immediate | **AS_EXPECTED** | a routing miss fails fast (< 2 s), not after a timeout |  | max latency 127 ms |

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
  "americaMaxLatencyMs" : 127,
  "logMarkers" : {
    "evento-am" : {
      "Skipping unregister of bundle" : 1
    },
    "gateway-america" : {
      "no handler for" : 5,
      "event=gateway_command_failed" : 5
    }
  },
  "americaRecoveredAfterRestartSeconds" : 0.594
}
```

## Timeline

- +7,6 s: scenario start
- +8,3 s: stopped order-command-am
- +19,5 s: sent 5 CreateOrder from AMERICA and EUROPA
- +19,7 s: cleanup start
- +29,2 s: cleanup done

### log excerpts: evento-am

```
20261020150624058 INFO  1 --- [nioEventLoopGroup-3-10                  ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: gateway-america

```
2026-10-20T15:06:33.918Z  WARN 1 --- [order-gateway] [mcat-handler-46] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3-a751cb-america-1 region=AMERICA kind=no_handler latencyMs=30 idempotencyKey=0fed9e8e-e119-4378-a1b8-d0ddc885913a error=no handler for CreateOrderCommand
2026-10-20T15:06:34.337Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3-a751cb-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=be8400d8-2576-4f23-b763-e27e6fde58a2 error=no handler for CreateOrderCommand
2026-10-20T15:06:34.549Z  WARN 1 --- [order-gateway] [mcat-handler-49] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3-a751cb-america-5 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=859c4949-c3cb-43e3-86f1-810d42d89fbf error=no handler for CreateOrderCommand
2026-10-20T15:06:33.918Z  WARN 1 --- [order-gateway] [mcat-handler-46] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3-a751cb-america-1 region=AMERICA kind=no_handler latencyMs=30 idempotencyKey=0fed9e8e-e119-4378-a1b8-d0ddc885913a error=no handler for CreateOrderCommand
2026-10-20T15:06:34.337Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3-a751cb-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=be8400d8-2576-4f23-b763-e27e6fde58a2 error=no handler for CreateOrderCommand
2026-10-20T15:06:34.549Z  WARN 1 --- [order-gateway] [mcat-handler-49] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3-a751cb-america-5 region=AMERICA kind=no_handler latencyMs=2 idempotencyKey=859c4949-c3cb-43e3-86f1-810d42d89fbf error=no handler for CreateOrderCommand
```

### stack actions

```
2026-10-20T15:06:23.157954100Z docker stop order-command-am
2026-10-20T15:06:35.258816500Z docker start order-command-am (restore)
```

