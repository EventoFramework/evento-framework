# S13: Wrong-broker guard and server identity

- Label: `after-versioning`
- Gaps exercised: G13, N16
- Started: 2026-10-16T00:47:13.359616700Z, finished: 2026-10-16T00:52:30.964021100Z (317 s)
- Verdicts: {AS_EXPECTED=4, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s13-after-versioning-bdff99-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`
- placementRestoredAtStart: `{}`
- lbRoutingAtStart: `ok`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| C: bundle expecting a foreign cluster | **AS_EXPECTED** | the session is refused (cluster_name_mismatch) and the bundle never becomes READY | no cluster identity in 2.4.6: the bundle attaches to whatever server answers (G13) | healthy=false, busState READY=false, 40 cluster_name_mismatch line(s), feature null |
| guard accepts the expected broker | **AS_EXPECTED** | acknowledged through the VIP |  | 3/3 acknowledged {} |
| A: legacy single-id guard on failover | **KNOWN_DEFECT_REPRODUCED** | by contract the single expected id refuses every other server, even a healthy one of the same cluster (recorded on both image sets; the fix is variant B's cluster name) | the single expected id rejects every healthy failover target: server_instance_mismatch loop (G13) | 0/3 acknowledged {error=3}; 7 mismatch log lines in 60 s |
| A: EU bundles fail back to evento-eu once it is healthy again (N16) | **AS_EXPECTED** | every EU JVM that failed over to another server is attached to evento-eu again, without a restart, within 180 s | no failback: they stay on the remote server until restarted (N16) | 7 JVM(s) remote while evento-eu was down {gateway-africa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}; still remote after the wait: {} |
| B: failover to a healthy server of the same cluster (cluster-name guard) | **AS_EXPECTED** | the bundle accepts any evento-lab-cluster server behind the VIP, EUROPA works again, and the cluster-name guard is active | no cluster-name guard in 2.4.6: failover works only because nothing is checked (G13) | ready=true, 3/3 before the kill {}, 3/3 after {}; guard available: evento-lab-cluster |


## Invariants

_Not evaluated in this scenario._

## Metrics

```json
{
  "C.clusterNameMismatches" : 40,
  "C.expectedClusterNameFeature" : null,
  "serverInstanceMismatches" : 7,
  "logMarkers" : {
    "gateway-europa" : {
      "event=handshake_complete" : 1,
      "event=bundle_state_transition" : 5
    },
    "lb" : { }
  },
  "A.bundlesRemoteWhileEuDown" : {
    "gateway-africa" : "evento-am",
    "order-command-eu" : "evento-am",
    "order-observer" : "evento-am",
    "order-query-eu" : "evento-am",
    "order-query-global-eu" : "evento-am",
    "order-query-wildcard" : "evento-am",
    "order-saga-eu" : "evento-am"
  },
  "A.bundlesStillRemoteAfterEuReturned" : { },
  "A.allHomeSecondsAfterEuReady" : 55.55,
  "A.bundlesRestartedIntoPlace" : { },
  "B.expectedClusterNameFeature" : "available: evento-lab-cluster",
  "B.probesBeforeKill" : "3/3",
  "B.probesAfterKill" : "3/3 {}"
}
```

## Timeline

- +8,4 s: scenario start
- +80,0 s: gateway-europa via bus-eu:3030 expecting evento-eu, ready=true
- +81,1 s: A: killed evento-eu
- +160,0 s: evento-eu back, ready=true
- +226,4 s: B: killed evento-eu
- +289,5 s: cleanup start
- +317,6 s: cleanup done

### gateway-europa C cluster mismatch lines

```
2026-10-16T00:47:26.989Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-10-16T00:47:27.529Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-10-16T00:47:28.497Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-10-16T00:47:30.269Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-10-16T00:47:32.108Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
```

### gateway-europa mismatch lines

```
2026-10-16T00:48:37.074Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-10-16T00:48:38.412Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-10-16T00:48:40.118Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-10-16T00:48:42.338Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-10-16T00:48:46.714Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-10-16T00:48:53.717Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-10-16T00:49:10.081Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
```

### log excerpts: gateway-europa

```
2026-10-16T00:51:02.334Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-am cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[revision:1, stream:1]
2026-10-16T00:50:59.549Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-16T00:50:59.995Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-16T00:51:00.005Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
```

### gateway-europa B handshakes after the kill

```
2026-10-16T00:51:02.334Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-am cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[revision:1, stream:1]
```

### stack actions

```
2026-10-16T00:47:21.749260600Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=, EVENTO_CLUSTER_NAME=foreign-cluster}
2026-10-16T00:48:24.431008300Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_EXPECTED_SERVER_ID=evento-eu, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-10-16T00:48:33.974987600Z docker kill evento-eu
2026-10-16T00:49:35.477699200Z docker start evento-eu (restore)
2026-10-16T00:50:50.525277200Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_EXPECTED_SERVER_ID=, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-10-16T00:50:59.408779200Z docker kill evento-eu
2026-10-16T00:52:02.934497Z docker start evento-eu (restore)
2026-10-16T00:52:20.699755800Z compose up -d --no-deps --force-recreate gateway-europa (restore default)
```

