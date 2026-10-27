# S01: Cross-cell routing miss

- Label: `v3e`
- Gaps exercised: G1
- Started: 2026-10-22T15:50:50.291525200Z, finished: 2026-10-22T15:51:24.602747300Z (34 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=1, AS_EXPECTED=2}

## Parameters

- orderIdPrefix: `s01-v3e-aac993-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| AMERICA command with no handler in its cell | **KNOWN_DEFECT_REPRODUCED** | routed to a live handler in EU or APAC and acknowledged (needs the G1 server mesh) | fails immediately with 'no handler for CreateOrderCommand' | 0/5 acknowledged; 5 no_handler; kinds {no_handler=5} |
| EUROPA control | **AS_EXPECTED** | unaffected: all acknowledged |  | 5/5 |
| failure is immediate | **AS_EXPECTED** | a routing miss fails fast (< 2 s), not after a timeout |  | max latency 113 ms |

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
  "americaMaxLatencyMs" : 113,
  "logMarkers" : {
    "evento-am" : { },
    "gateway-america" : {
      "no handler for" : 5,
      "event=gateway_command_failed" : 5
    }
  },
  "americaRecoveredAfterRestartSeconds" : 0.595
}
```

## Timeline

- +8,4 s: scenario start
- +9,0 s: stopped order-command-am
- +20,3 s: sent 5 CreateOrder from AMERICA and EUROPA
- +20,5 s: cleanup start
- +34,3 s: cleanup done

### log excerpts: gateway-america

```
2026-10-22T15:51:09.445Z  WARN 1 --- [order-gateway] [mcat-handler-46] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3e-aac993-america-1 region=AMERICA kind=no_handler latencyMs=25 idempotencyKey=2f0bc728-3050-4b94-9d76-144a5112c38b error=no handler for CreateOrderCommand
2026-10-22T15:51:09.793Z  WARN 1 --- [order-gateway] [mcat-handler-47] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3e-aac993-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=cbed5f90-47bc-4984-be80-1abf1edceb45 error=no handler for CreateOrderCommand
2026-10-22T15:51:10.050Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3e-aac993-america-5 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=3b186d24-ed0d-49fe-a903-3057af6c52e0 error=no handler for CreateOrderCommand
2026-10-22T15:51:09.445Z  WARN 1 --- [order-gateway] [mcat-handler-46] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3e-aac993-america-1 region=AMERICA kind=no_handler latencyMs=25 idempotencyKey=2f0bc728-3050-4b94-9d76-144a5112c38b error=no handler for CreateOrderCommand
2026-10-22T15:51:09.793Z  WARN 1 --- [order-gateway] [mcat-handler-47] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3e-aac993-america-3 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=cbed5f90-47bc-4984-be80-1abf1edceb45 error=no handler for CreateOrderCommand
2026-10-22T15:51:10.050Z  WARN 1 --- [order-gateway] [mcat-handler-48] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s01-v3e-aac993-america-5 region=AMERICA kind=no_handler latencyMs=3 idempotencyKey=3b186d24-ed0d-49fe-a903-3057af6c52e0 error=no handler for CreateOrderCommand
```

### stack actions

```
2026-10-22T15:50:58.650374Z docker stop order-command-am
2026-10-22T15:51:10.828803600Z docker start order-command-am (restore)
```

