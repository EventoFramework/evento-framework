# S01: Cross-cell routing miss

- Label: `v3c`
- Gaps exercised: G1
- Started: 2026-10-22T12:53:02.938791800Z, finished: 2026-10-22T12:53:38.052785Z (35 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s01-v3c-cf6138-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| AMERICA command with no handler in its cell | **KNOWN_DEFECT_REPRODUCED** | routed to a live handler in EU or APAC and acknowledged (needs the G1 server mesh) | fails immediately with 'no handler for CreateOrderCommand' | 0/5 acknowledged; 5 no_handler; kinds {no_handler=5} |
| EUROPA control | **AS_EXPECTED** | unaffected: all acknowledged |  | 5/5 |
| failure is immediate | **AS_EXPECTED** | a routing miss fails fast (< 2 s), not after a timeout |  | max latency 149 ms |

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
  "americaMaxLatencyMs" : 149,
  "logMarkers" : {
    "evento-am" : {
      "Skipping unregister of bundle" : 1
    },
    "gateway-america" : {
      "no handler for" : 5,
      "event=gateway_command_failed" : 5
    }
  },
  "americaRecoveredAfterRestartSeconds" : 0.6
}
```

## Timeline

- +8,8 s: scenario start
- +9,5 s: stopped order-command-am
- +20,8 s: sent 5 CreateOrder from AMERICA and EUROPA
- +21,0 s: cleanup start
- +35,1 s: cleanup done

### log excerpts: evento-am

```
20261022125312656 INFO  1 --- [evento-leave-cleanup                    ] AutoDiscoveryService                     : Skipping unregister of bundle order-command: another live instance is connected
```

### log excerpts: gateway-america

```
2026-10-22T12:53:22.579Z  WARN 1 --- [order-gateway] [mcat-handler-46] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3c-cf6138-america-1 region=AMERICA kind=no_handler latencyMs=45 idempotencyKey=256c4f73-19ff-4198-9dc3-b240bf8b4e4d error=no handler for CreateOrderCommand
2026-10-22T12:53:22.994Z  WARN 1 --- [order-gateway] [mcat-handler-47] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3c-cf6138-america-3 region=AMERICA kind=no_handler latencyMs=5 idempotencyKey=97a8db52-24f5-45dd-94d0-a3aa850378f4 error=no handler for CreateOrderCommand
2026-10-22T12:53:23.173Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3c-cf6138-america-5 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=589acbcc-468b-4f30-a84d-f92395eb0439 error=no handler for CreateOrderCommand
2026-10-22T12:53:22.579Z  WARN 1 --- [order-gateway] [mcat-handler-46] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3c-cf6138-america-1 region=AMERICA kind=no_handler latencyMs=45 idempotencyKey=256c4f73-19ff-4198-9dc3-b240bf8b4e4d error=no handler for CreateOrderCommand
2026-10-22T12:53:22.994Z  WARN 1 --- [order-gateway] [mcat-handler-47] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3c-cf6138-america-3 region=AMERICA kind=no_handler latencyMs=5 idempotencyKey=97a8db52-24f5-45dd-94d0-a3aa850378f4 error=no handler for CreateOrderCommand
2026-10-22T12:53:23.173Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3c-cf6138-america-5 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=589acbcc-468b-4f30-a84d-f92395eb0439 error=no handler for CreateOrderCommand
```

### stack actions

```
2026-10-22T12:53:11.712272200Z docker stop order-command-am
2026-10-22T12:53:24.061303900Z docker start order-command-am (restore)
```

