# S13: Wrong-broker guard and server identity

- Label: `after-g5n16`
- Gaps exercised: G13, N16
- Started: 2026-09-29T20:39:22.621579900Z, finished: 2026-09-29T20:44:42.088928Z (319 s)
- Verdicts: {AS_EXPECTED=4, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s13-after-g5n16-272eba-`
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
  "A.allHomeSecondsAfterEuReady" : 53.314,
  "A.bundlesRestartedIntoPlace" : { },
  "B.expectedClusterNameFeature" : "available: evento-lab-cluster",
  "B.probesBeforeKill" : "3/3",
  "B.probesAfterKill" : "3/3 {}"
}
```

## Timeline

- +8,3 s: scenario start
- +81,0 s: gateway-europa via bus-eu:3030 expecting evento-eu, ready=true
- +82,1 s: A: killed evento-eu
- +160,8 s: evento-eu back, ready=true
- +225,2 s: B: killed evento-eu
- +291,5 s: cleanup start
- +319,5 s: cleanup done

### gateway-europa C cluster mismatch lines

```
2026-09-29T20:39:37.167Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T20:39:37.832Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T20:39:38.885Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T20:39:41.253Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T20:39:42.308Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
```

### gateway-europa mismatch lines

```
2026-09-29T20:40:47.580Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T20:40:48.433Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T20:40:49.964Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T20:40:52.639Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T20:40:57.602Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T20:41:04.470Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T20:41:20.228Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
```

### log excerpts: gateway-europa

```
2026-09-29T20:43:10.297Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[]
2026-09-29T20:43:07.490Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T20:43:07.900Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T20:43:07.923Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
```

### gateway-europa B handshakes after the kill

```
2026-09-29T20:43:10.297Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[]
```

### stack actions

```
2026-09-29T20:39:30.944723900Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_CLUSTER_NAME=foreign-cluster, EVENTO_EXPECTED_SERVER_ID=, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-09-29T20:40:33.754717200Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_EXPECTED_SERVER_ID=evento-eu, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-09-29T20:40:44.270743600Z docker kill evento-eu
2026-09-29T20:41:45.560026800Z docker start evento-eu (restore)
2026-09-29T20:42:58.318004700Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_EXPECTED_SERVER_ID=, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-09-29T20:43:07.368865800Z docker kill evento-eu
2026-09-29T20:44:14.193911300Z docker start evento-eu (restore)
2026-09-29T20:44:31.953260700Z compose up -d --no-deps --force-recreate gateway-europa (restore default)
```

