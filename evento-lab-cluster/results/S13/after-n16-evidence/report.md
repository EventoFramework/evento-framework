# S13: Wrong-broker guard and server identity

- Label: `after`
- Gaps exercised: G13
- Started: 2026-09-29T14:42:15.874062800Z, finished: 2026-09-29T14:46:37.483876400Z (261 s)
- Verdicts: {AS_EXPECTED=2, UNEXPECTED=1, KNOWN_DEFECT_REPRODUCED=1}

## Parameters

- orderIdPrefix: `s13-after-fb56ce-`
- composeFile: `D:\Gabor\DIDATTICA\UNI\TESI_MAGISTRALE\evento\evento-lab-cluster\docker\compose.yaml`
- servicesNotReadyAtStart: `[]`

## Observed vs expected

| Observation | Verdict | Expected (correct system) | Expected before fix | Observed |
|---|---|---|---|---|
| C: bundle expecting a foreign cluster | **AS_EXPECTED** | the session is refused (cluster_name_mismatch) and the bundle never becomes READY | no cluster identity in 2.4.6: the bundle attaches to whatever server answers (G13) | healthy=false, busState READY=false, 40 cluster_name_mismatch line(s), feature null |
| guard accepts the expected broker | **UNEXPECTED** | acknowledged through the VIP |  | 0/3 acknowledged {no_handler=3} |
| A: legacy single-id guard on failover | **KNOWN_DEFECT_REPRODUCED** | by contract the single expected id refuses every other server, even a healthy one of the same cluster (recorded on both image sets; the fix is variant B's cluster name) | the single expected id rejects every healthy failover target: server_instance_mismatch loop (G13) | 0/3 acknowledged {error=3}; 7 mismatch log lines in 60 s |
| B: failover to a healthy server of the same cluster (cluster-name guard) | **AS_EXPECTED** | the bundle accepts any evento-lab-cluster server behind the VIP, EUROPA works again, and the cluster-name guard is active | no cluster-name guard in 2.4.6: failover works only because nothing is checked (G13) | ready=true, 0/3 before the kill, 3/3 after {}; guard available: evento-lab-cluster |


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
  "B.expectedClusterNameFeature" : "available: evento-lab-cluster",
  "B.probesBeforeKill" : "0/3",
  "B.probesAfterKill" : "3/3 {}"
}
```

## Timeline

- +5,9 s: scenario start
- +78,5 s: gateway-europa via bus-eu:3030 expecting evento-eu, ready=true
- +79,0 s: A: killed evento-eu
- +157,2 s: evento-eu back, ready=true
- +165,9 s: B: killed evento-eu
- +228,7 s: cleanup start
- +261,6 s: cleanup done

### gateway-europa C cluster mismatch lines

```
2026-09-29T14:42:27.395Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T14:42:27.974Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T14:42:29.180Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T14:42:31.182Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
2026-09-29T14:42:32.519Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=cluster_name_mismatch host=bus-eu port=3030 announced=evento-lab-cluster expected=foreign-cluster - refusing to register with this broker
```

### gateway-europa mismatch lines

```
2026-09-29T14:43:37.472Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T14:43:38.497Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T14:43:39.897Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T14:43:42.315Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T14:43:46.527Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
2026-09-29T14:43:55.588Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-apac expected=evento-eu - refusing to register with this broker
2026-09-29T14:44:13.760Z ERROR 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=server_instance_mismatch host=bus-eu port=3030 announced=evento-am expected=evento-eu - refusing to register with this broker
```

### log excerpts: gateway-europa

```
2026-09-29T14:45:04.230Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-am cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[]
2026-09-29T14:45:01.515Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=READY to=RECONNECTING reason=post_session force=true
2026-09-29T14:45:01.954Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=RECONNECTING to=CONNECTING reason=open_transport force=true
2026-09-29T14:45:01.966Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=bundle_state_transition from=CONNECTING to=HANDSHAKING reason=tcp_up force=true
```

### gateway-europa B handshakes after the kill

```
2026-09-29T14:45:04.230Z  INFO 1 --- [order-gateway] [-gateway-europa] c.e.a.c.connection.ConnectionSupervisor  : event=handshake_complete server_instance=evento-am cluster=evento-lab-cluster endpoint=bus-eu:3030 accepted_caps=[]
```

### stack actions

```
2026-09-29T14:42:21.749026800Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_CLUSTER_NAME=foreign-cluster, EVENTO_EXPECTED_SERVER_ID=, EVENTO_SERVER_ADDRESSES=bus-eu:3030}
2026-09-29T14:43:24.210301300Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=evento-eu}
2026-09-29T14:43:34.528725100Z docker kill evento-eu
2026-09-29T14:44:35.262803500Z docker start evento-eu (restore)
2026-09-29T14:44:53.069554700Z compose up -d --no-deps --force-recreate gateway-europa with env {EVENTO_SERVER_ADDRESSES=bus-eu:3030, EVENTO_EXPECTED_SERVER_ID=}
2026-09-29T14:45:01.357574900Z docker kill evento-eu
2026-09-29T14:46:04.635387900Z docker start evento-eu (restore)
2026-09-29T14:46:22.444288600Z compose up -d --no-deps --force-recreate gateway-europa (restore default)
```

