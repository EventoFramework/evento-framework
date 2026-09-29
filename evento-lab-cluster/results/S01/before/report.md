# S01: Cross-cell routing miss

- Label: `before`
- Gaps exercised: G1
- Started: 2026-09-29T12:43:48.973800900Z, finished: 2026-09-29T12:44:16.861995600Z (27 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s01-before-ef73e6-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| AMERICA command with no handler in its cell | **KNOWN_DEFECT_REPRODUCED** | routed to a live handler in EU or APAC and acknowledged (needs the G1 server mesh) | fails immediately with 'no handler for CreateOrderCommand' | 0/5 acknowledged; 5 no_handler; kinds {no_handler=5} |
| EUROPA control | **AS_EXPECTED** | unaffected: all acknowledged |  | 5/5 |
| failure is immediate | **AS_EXPECTED** | a routing miss fails fast (< 2 s), not after a timeout |  | max latency 89 ms |

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
  "americaMaxLatencyMs" : 89,
  "logMarkers" : {
    "evento-am" : { },
    "gateway-america" : {
      "no handler for" : 5,
      "event=gateway_command_failed" : 5
    }
  },
  "americaRecoveredAfterRestartSeconds" : 1.218
}
```

## Timeline

- +6,0 s: scenario start
- +6,6 s: stopped order-command-am
- +17,6 s: sent 5 CreateOrder from AMERICA and EUROPA
- +17,8 s: cleanup start
- +27,9 s: cleanup done

### log excerpts: gateway-america

```
2026-09-29T12:44:05.701Z  WARN 1 --- [order-gateway] [cat-handler-112] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-before-ef73e6-america-1 region=AMERICA kind=no_handler latencyMs=29 idempotencyKey=6b5f34df-cf97-4e25-ae12-e210bff58c56 error=no handler for CreateOrderCommand
2026-09-29T12:44:06.117Z  WARN 1 --- [order-gateway] [cat-handler-114] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-before-ef73e6-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=c53cb918-991d-43fc-b8d4-c3e580142941 error=no handler for CreateOrderCommand
2026-09-29T12:44:06.252Z  WARN 1 --- [order-gateway] [cat-handler-115] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-before-ef73e6-america-5 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=18fc4b5c-de98-4b2e-b755-bd6dbef8ae9c error=no handler for CreateOrderCommand
2026-09-29T12:44:05.701Z  WARN 1 --- [order-gateway] [cat-handler-112] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-before-ef73e6-america-1 region=AMERICA kind=no_handler latencyMs=29 idempotencyKey=6b5f34df-cf97-4e25-ae12-e210bff58c56 error=no handler for CreateOrderCommand
2026-09-29T12:44:06.117Z  WARN 1 --- [order-gateway] [cat-handler-114] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-before-ef73e6-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=c53cb918-991d-43fc-b8d4-c3e580142941 error=no handler for CreateOrderCommand
2026-09-29T12:44:06.252Z  WARN 1 --- [order-gateway] [cat-handler-115] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-before-ef73e6-america-5 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=18fc4b5c-de98-4b2e-b755-bd6dbef8ae9c error=no handler for CreateOrderCommand
```

### stack actions

```
2026-09-29T12:43:54.975759200Z docker stop order-command-am
2026-09-29T12:44:06.855677100Z docker start order-command-am (restore)
```

