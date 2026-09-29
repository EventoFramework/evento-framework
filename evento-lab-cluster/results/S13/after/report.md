# S13: Wrong-broker guard and server identity

- Label: `after`
- Gaps exercised: G13
- Started: 2026-09-29T15:51:07.773434800Z, finished: 2026-09-29T15:56:18.882847Z (311 s)
- Verdicts: {AS_EXPECTED=3, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s13-after-735ee2-`
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
  "A.bundlesStillRemoteAfterEuReturned" : {
    "gateway-africa" : "evento-am",
    "order-command-eu" : "evento-am",
    "order-observer" : "evento-am",
    "order-query-eu" : "evento-am",
    "order-query-global-eu" : "evento-am",
    "order-query-wildcard" : "evento-am",
    "order-saga-eu" : "evento-am"
  },
  "B.expectedClusterNameFeature" : "available: evento-lab-cluster",
  "B.probesBeforeKill" : "3/3",
  "B.probesAfterKill" : "3/3 {}"
}
```

## Timeline

- +7,6 s: scenario start
- +79,3 s: gateway-europa via bus-eu:3030 expecting evento-eu, ready=true
- +80,2 s: A: killed evento-eu
- +158,3 s: evento-eu back, ready=true
- +159,9 s: placement: 7 JVM(s) on a remote server, restarting them: {gateway-africa=evento-am, order-command-eu=evento-am, order-observer=evento-am, order-query-eu=evento-am, order-query-global-eu=evento-am, order-query-wildcard=evento-am, order-saga-eu=evento-am}
- +208,8 s: placement restored, ready={gateway-africa=true, order-command-eu=true, order-observer=true, order-query-eu=true, order-query-global-eu=true, order-query-wildcard=true, order-saga-eu=true}
- +218,4 s: B: killed evento-eu
- +283,2 s: cleanup start
- +311,1 s: cleanup done

### gateway-europa C cluster mismatch lines

```
2026-09-29T15:51:20.698Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T15:51:21.406Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T15:51:22.534Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T15:51:24.552Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T15:51:25.823Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
```

### gateway-europa mismatch lines

```
2026-09-29T15:52:30.791Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T15:52:32.051Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T15:52:33.420Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T15:52:35.953Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T15:52:40.387Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T15:52:47.676Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T15:53:04.549Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
```

### log excerpts: gateway-europa

```
2026-09-29T15:54:48.798Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[]
2026-09-29T15:54:45.924Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T15:54:46.389Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T15:54:46.408Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
```

### gateway-europa B handshakes after the kill

```
2026-09-29T15:54:48.798Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-apac cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[]
```

### stack actions

```
2026-09-29T15:51:15.411498Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_EXPECTED_SERVER_ID=, EVENTO_CLUSTER_NAME=foreign-cluster, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-09-29T15:52:18.072060300Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=evento-eu}
2026-09-29T15:52:27.547250700Z docker kill evento-eu
2026-09-29T15:53:28.361750100Z docker start evento-eu (restore)
2026-09-29T15:53:47.703722500Z docker restart gateway-africa order-command-eu order-observer order-query-eu order-query-global-eu order-query-wildcard order-saga-eu
2026-09-29T15:54:36.587859700Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=}
2026-09-29T15:54:45.682892500Z docker kill evento-eu
2026-09-29T15:55:51.026133300Z docker start evento-eu (restore)
2026-09-29T15:56:08.875858700Z compose up -d --no-deps --force-recreate gateway-europa (restore default)
```

