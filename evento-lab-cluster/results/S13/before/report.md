# S13: Wrong-broker guard and server identity

- Label: `before`
- Gaps exercised: G13
- Started: 2026-09-29T13:50:55.350713800Z, finished: 2026-09-29T13:52:39.320980800Z (103 s)
- Verdicts: {AS_EXPECTED=1, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s13-before-4ec5e8-`
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
- +14,7 s: gateway-europa via bus-eu:3030 expecting evento-eu, ready=true
- +16,1 s: killed evento-eu
- +76,3 s: cleanup start
- +104,0 s: cleanup done

### gateway-europa mismatch lines

```
2026-09-29T13:51:13.884Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T13:51:14.440Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T13:51:15.676Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T13:51:17.868Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T13:51:22.201Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T13:51:31.446Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T13:51:48.428Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
```

### log excerpts: gateway-europa

```
2026-09-29T13:51:11.156Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T13:51:11.720Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T13:51:11.738Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T13:51:13.899Z  WARN 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=1 cause=java.lang.IllegalStateException: broker at bus-eu:3030 announced instance 'evento-apac', expected 'evento-eu'
2026-09-29T13:51:14.445Z  WARN 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=2 cause=java.lang.IllegalStateException: broker at bus-eu:3030 announced instance 'evento-am', expected 'evento-eu'
2026-09-29T13:51:15.684Z  WARN 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=supervisor_session_failed attempt=3 cause=java.lang.IllegalStateException: broker at bus-eu:3030 announced instance 'evento-apac', expected 'evento-eu'
2026-09-29T13:51:13.884Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T13:51:14.440Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T13:51:15.676Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T13:52:11.412Z  WARN 1 --- [order-gateway] [mcat-handler-17] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s13-before-4ec5e8-europa-4 region=EUROPA kind=error latencyMs=1 idempotencyKey=6ea0fc74-4a9f-4f25-bafb-bedff5e9af0c error=bundle not ready: state=RECONNECTING
2026-09-29T13:52:11.420Z  WARN 1 --- [order-gateway] [mcat-handler-18] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s13-before-4ec5e8-europa-5 region=EUROPA kind=error latencyMs=0 idempotencyKey=c9a9c7e8-bbca-4c95-84ec-61fa46cf3cfc error=bundle not ready: state=RECONNECTING
2026-09-29T13:52:11.430Z  WARN 1 --- [order-gateway] [mcat-handler-19] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s13-before-4ec5e8-europa-6 region=EUROPA kind=error latencyMs=2 idempotencyKey=be5747b7-1075-4f78-9ee6-304d31de3fab error=bundle not ready: state=RECONNECTING
```

### stack actions

```
2026-09-29T13:51:01.489150400Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=evento-eu}
2026-09-29T13:51:10.941848500Z docker kill evento-eu
2026-09-29T13:52:11.745640300Z docker start evento-eu (restore)
2026-09-29T13:52:29.583443100Z compose up -d --no-deps --force-recreate gateway-europa (restore default)
```

