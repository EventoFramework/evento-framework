# S13: Wrong-broker guard and server identity

- Label: `before`
- Gaps exercised: G13
- Started: 2026-09-29T16:55:53.628378600Z, finished: 2026-09-29T16:59:58.589375100Z (244 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=3, AS_EXPECTED=1}

## Parameters

- orderIdPrefix: `s13-before-03c5a4-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| C: bundle expecting a foreign cluster | **KNOWN_DEFECT_REPRODUCED** | the session is refused (cluster_name_mismatch) and the bundle never becomes READY | no cluster identity in 2.4.6: the bundle attaches to whatever server answers (G13) | healthy=true, busState READY=true, 0 cluster_name_mismatch line(s), feature unavailable |
| guard accepts the expected broker | **AS_EXPECTED** | acknowledged through the VIP |  | 3/3 acknowledged {} |
| A: legacy single-id guard on failover | **KNOWN_DEFECT_REPRODUCED** | by contract the single expected id refuses every other server, even a healthy one of the same cluster (recorded on both image sets; the fix is variant B's cluster name) | the single expected id rejects every healthy failover target: server_instance_mismatch loop (G13) | 0/3 acknowledged {error=3}; 7 mismatch log lines in 60 s |
| B: failover to a healthy server of the same cluster (cluster-name guard) | **KNOWN_DEFECT_REPRODUCED** | the bundle accepts any evento-lab-cluster server behind the VIP, EUROPA works again, and the cluster-name guard is active | no cluster-name guard in 2.4.6: failover works only because nothing is checked (G13) | ready=true, 3/3 before the kill {}, 2/3 after {timeout=1}; guard unavailable |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "C.clusterNameMismatches" : 0,
  "C.expectedClusterNameFeature" : "unavailable",
  "serverInstanceMismatches" : 7,
  "logMarkers" : {
    "gateway-europa" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 5,
      "event=bundle_correlation_expired" : 1,
      "event=gateway_command_failed" : 1
    },
    "lb" : { }
  },
  "A.bundlesStillRemoteAfterEuReturned" : { },
  "B.expectedClusterNameFeature" : "unavailable",
  "B.probesBeforeKill" : "3/3",
  "B.probesAfterKill" : "2/3 {timeout=1}"
}
```

## Timeline

- +7,8 s: scenario start
- +24,9 s: gateway-europa via bus-eu:3030 expecting evento-eu, ready=true
- +27,6 s: A: killed evento-eu
- +105,7 s: evento-eu back, ready=true
- +117,6 s: B: killed evento-eu
- +216,9 s: cleanup start
- +245,0 s: cleanup done

### gateway-europa C cluster mismatch lines

```
```

### gateway-europa mismatch lines

```
2026-09-29T16:56:23.743Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T16:56:24.351Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T16:56:25.337Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T16:56:27.337Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T16:56:31.645Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T16:56:40.855Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T16:57:00.781Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
```

### log excerpts: gateway-europa

```
2026-09-29T16:57:53.658Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac accepted_caps=[]
2026-09-29T16:57:50.907Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T16:57:51.471Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T16:57:51.499Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
2026-09-29T16:59:21.625Z  WARN 1 --- [order-gateway] [elation-cleaner] c.e.a.c.c.BundleCorrelationTracker       : event=bundle_correlation_expired count=1 pending=0 byType={CreateOrderCommand=1}
2026-09-29T16:59:21.625Z  WARN 1 --- [order-gateway] [mcat-handler-18] c.e.l.c.gateway.OrderGatewayController   : event=gateway_command_failed op=create orderId=s13-before-03c5a4-europa-10 region=EUROPA kind=timeout latencyMs=30428 idempotencyKey=18713c7a-8e29-4f75-96c7-7b1b49335cb3 error=CreateOrderCommand: request expired after 30424ms (timeout=30000)
```

### gateway-europa B handshakes after the kill

```
2026-09-29T16:57:53.658Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac accepted_caps=[]
```

### stack actions

```
2026-09-29T16:56:01.469212800Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_CLUSTER_NAME=foreign-cluster, EVENTO_EXPECTED_SERVER_ID=, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-09-29T16:56:10.089726700Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=evento-eu}
2026-09-29T16:56:20.826209400Z docker kill evento-eu
2026-09-29T16:57:21.587266400Z docker start evento-eu (restore)
2026-09-29T16:57:41.109485800Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=}
2026-09-29T16:57:50.732841700Z docker kill evento-eu
2026-09-29T16:59:30.634574500Z docker start evento-eu (restore)
2026-09-29T16:59:48.460335300Z compose up -d --no-deps --force-recreate gateway-europa (restore default)
```

