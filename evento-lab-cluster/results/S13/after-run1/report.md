# S13: Wrong-broker guard and server identity

- Label: `after`
- Gaps exercised: G13
- Started: 2026-09-29T14:37:59.186957700Z, finished: 2026-09-29T14:39:42.999625100Z (103 s)
- Verdicts: {AS_EXPECTED=1, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s13-after-13ceed-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| guard accepts the expected broker | **AS_EXPECTED** | acknowledged through the VIP |  | 3/3 acknowledged {} |
| failover to a healthy server of the same cluster | **KNOWN_DEFECT_REPRODUCED** | the bundle accepts any evento-lab-cluster server behind the VIP and EUROPA works again | the single expected id rejects every healthy failover target: server_instance_mismatch loop (G13) | 0/3 acknowledged {error=3}; 7 mismatch log lines in 60 s |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "serverInstanceMismatches" : 7,
  "logMarkers" : {
    "gateway-europa" : {
      "event=bundle_state_transition" : 22,
      "event=supervisor_session_failed" : 7,
      "event=server_instance_mismatch" : 7,
      "event=gateway_command_failed" : 3
    },
    "lb" : { }
  }
}
```

## Notes

- Foreign-cluster rejection (the other half of the G13 fix) needs a 4th server with EVENTO_CLUSTER_NAME set to something else; not part of the default topology.

## Timeline

- +6,1 s: scenario start
- +14,4 s: gateway-europa via bus-eu:3030 expecting evento-eu, ready=true
- +15,3 s: killed evento-eu
- +75,6 s: cleanup start
- +103,8 s: cleanup done

### gateway-europa mismatch lines

```
2026-09-29T14:38:17.098Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T14:38:18.039Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T14:38:19.604Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T14:38:22.091Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T14:38:27.148Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T14:38:36.289Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T14:38:57.835Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
```

### log excerpts: gateway-europa

```
2026-09-29T14:38:14.209Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:38:14.678Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:38:14.692Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T14:38:17.103Z  WARN 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 endpoint=bus-eu:3030 cause=java.lang.IllegalStateException: broker at bus-eu:3030 announced instance 'evento-apac', expected 'evento-eu'
2026-09-29T14:38:18.046Z  WARN 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 endpoint=bus-eu:3030 cause=java.lang.IllegalStateException: broker at bus-eu:3030 announced instance 'evento-am', expected 'evento-eu'
2026-09-29T14:38:19.609Z  WARN 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 endpoint=bus-eu:3030 cause=java.lang.IllegalStateException: broker at bus-eu:3030 announced instance 'evento-apac', expected 'evento-eu'
2026-09-29T14:38:17.098Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T14:38:18.039Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T14:38:19.604Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T14:39:14.479Z  WARN 1 --- [order-gateway] [mcat-handler-17] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s13-after-13ceed-europa-4 region=EUROPA kind=error latencyMs=1 idempotencyKey=93e0c274-1423-4e4d-bcfb-c431bf5036ac error=bundle not ready: state=RECONNECTING
2026-09-29T14:39:14.488Z  WARN 1 --- [order-gateway] [mcat-handler-18] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s13-after-13ceed-europa-5 region=EUROPA kind=error latencyMs=1 idempotencyKey=95a5d1d1-a25f-485d-9e24-4b2dd60a0bbd error=bundle not ready: state=RECONNECTING
2026-09-29T14:39:14.496Z  WARN 1 --- [order-gateway] [mcat-handler-19] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s13-after-13ceed-europa-6 region=EUROPA kind=error latencyMs=0 idempotencyKey=199c981e-bcf7-4020-850d-db88d4f24e28 error=bundle not ready: state=RECONNECTING
```

### stack actions

```
2026-09-29T14:38:05.270572100Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=evento-eu}
2026-09-29T14:38:14.036977900Z docker kill evento-eu
2026-09-29T14:39:14.815255Z docker start evento-eu (restore)
2026-09-29T14:39:32.630630100Z compose up -d --no-deps --force-recreate gateway-europa (restore default)
```

