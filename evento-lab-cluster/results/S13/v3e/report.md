# S13: Wrong-broker guard and server identity

- Label: `v3e`
- Gaps exercised: G13, N16
- Started: 2026-10-22T16:31:35.414134Z, finished: 2026-10-22T16:36:51.509423400Z (316 s)
- Verdicts: {AS_EXPECTED=4, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s13-v3e-31cf74-`
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
  "A.allHomeSecondsAfterEuReady" : 53.337,
  "A.bundlesRestartedIntoPlace" : { },
  "B.expectedClusterNameFeature" : "available: evento-lab-cluster",
  "B.probesBeforeKill" : "3/3",
  "B.probesAfterKill" : "3/3 {}"
}
```

## Timeline

- +8,1 s: scenario start
- +79,8 s: gateway-europa via bus-eu:3030 expecting evento-eu, ready=true
- +81,0 s: A: killed evento-eu
- +159,9 s: evento-eu back, ready=true
- +224,5 s: B: killed evento-eu
- +287,8 s: cleanup start
- +316,1 s: cleanup done

### gateway-europa C cluster mismatch lines

```
2026-10-22T16:31:49.055Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-10-22T16:31:49.497Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-10-22T16:31:50.646Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-10-22T16:31:52.714Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-10-22T16:31:54.302Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
```

### gateway-europa mismatch lines

```
2026-10-22T16:32:58.856Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-10-22T16:32:59.519Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-10-22T16:33:00.836Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-10-22T16:33:02.600Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-10-22T16:33:06.769Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-10-22T16:33:15.276Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-10-22T16:33:31.245Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
```

### log excerpts: gateway-europa

```
2026-10-22T16:35:22.296Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[revision:1, head:1, events:1, stream:1]
2026-10-22T16:35:19.596Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-10-22T16:35:20.139Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-10-22T16:35:20.143Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
```

### gateway-europa B handshakes after the kill

```
2026-10-22T16:35:22.296Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[revision:1, head:1, events:1, stream:1]
```

### stack actions

```
2026-10-22T16:31:43.493682700Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_CLUSTER_NAME=foreign-cluster, EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=}
2026-10-22T16:32:46.273246100Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_EXPECTED_SERVER_ID=evento-eu, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-10-22T16:32:55.893153900Z docker kill evento-eu
2026-10-22T16:33:57.386046300Z docker start evento-eu (restore)
2026-10-22T16:35:10.326505800Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_EXPECTED_SERVER_ID=, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-10-22T16:35:19.444152900Z docker kill evento-eu
2026-10-22T16:36:23.339157Z docker start evento-eu (restore)
2026-10-22T16:36:41.246630100Z compose up -d --no-deps --force-recreate gateway-europa (restore default)
```

