# S13: Wrong-broker guard and server identity

- Label: `before-g5n16`
- Gaps exercised: G13, N16
- Started: 2026-09-29T21:54:39.881860600Z, finished: 2026-09-29T21:58:11.244235300Z (211 s)
- Verdicts: {KNOWN_DEFECT_REPRODUCED=3, AS_EXPECTED=1, INCONCLUSIVE=1}

## Parameters

- orderIdPrefix: `s13-before-g5n16-83f33c-`
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
| A: EU bundles fail back to evento-eu once it is healthy again (N16) | **INCONCLUSIVE** | every EU JVM that failed over to another server is attached to evento-eu again, without a restart, within 180 s | no failback: they stay on the remote server until restarted (N16) | no EU JVM had failed over |
| B: failover to a healthy server of the same cluster (cluster-name guard) | **KNOWN_DEFECT_REPRODUCED** | the bundle accepts any evento-lab-cluster server behind the VIP, EUROPA works again, and the cluster-name guard is active | no cluster-name guard in 2.4.6: failover works only because nothing is checked (G13) | ready=true, 0/3 before the kill {no_handler=3}, 3/3 after {}; guard unavailable |


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
      "event=bundle_state_transition" : 5
    },
    "lb" : { }
  },
  "A.bundlesRemoteWhileEuDown" : { },
  "A.bundlesStillRemoteAfterEuReturned" : { },
  "A.allHomeSecondsAfterEuReady" : 0.553,
  "A.bundlesRestartedIntoPlace" : { },
  "B.expectedClusterNameFeature" : "unavailable",
  "B.probesBeforeKill" : "0/3",
  "B.probesAfterKill" : "3/3 {}"
}
```

## Timeline

- +8,0 s: scenario start
- +25,0 s: gateway-europa via bus-eu:3030 expecting evento-eu, ready=true
- +26,1 s: A: killed evento-eu
- +104,8 s: evento-eu back, ready=true
- +115,6 s: B: killed evento-eu
- +183,1 s: cleanup start
- +211,4 s: cleanup done

### gateway-europa C cluster mismatch lines

```
```

### gateway-europa mismatch lines

```
2026-09-29T21:55:08.499Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T21:55:09.020Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T21:55:10.088Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T21:55:12.460Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T21:55:17.236Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T21:55:25.643Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T21:55:43.873Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
```

### log excerpts: gateway-europa

```
2026-09-29T21:56:37.931Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac accepted_caps=[]
2026-09-29T21:56:35.194Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T21:56:35.766Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T21:56:35.781Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
```

### gateway-europa B handshakes after the kill

```
2026-09-29T21:56:37.931Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac accepted_caps=[]
```

### stack actions

```
2026-09-29T21:54:47.858025600Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_CLUSTER_NAME=foreign-cluster, EVENTO_EXPECTED_SERVER_ID=}
2026-09-29T21:54:56.441941600Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_EXPECTED_SERVER_ID=evento-eu, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-09-29T21:55:05.553936300Z docker kill evento-eu
2026-09-29T21:56:06.917106700Z docker start evento-eu (restore)
2026-09-29T21:56:26.750927300Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_EXPECTED_SERVER_ID=, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-09-29T21:56:35.063272300Z docker kill evento-eu
2026-09-29T21:57:43.101565700Z docker start evento-eu (restore)
2026-09-29T21:58:01.027354500Z compose up -d --no-deps --force-recreate gateway-europa (restore default)
```

