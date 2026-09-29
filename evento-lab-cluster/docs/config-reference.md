# Riferimento di configurazione

[Indice](README.md)

Tutti i nomi e i default qui sotto sono letti dal codice (percorsi indicati per ogni tabella). Le
proprietà Spring del server si impostano anche da ambiente con la regola di Spring Boot (`.` e `-`
diventano `_`, tutto maiuscolo): `evento.lock.pool.maximum-size` → `EVENTO_LOCK_POOL_MAXIMUM_SIZE`.
Nella tabella del §4 la colonna "Variabile d'ambiente" è derivata con questa regola; solo alcune
(`SPRING_DATASOURCE_URL`, `EVENTO_CLUSTER_NAME`, `EVENTO_SERVER_BUS_PICK_STRATEGY`,
`EVENTO_SERVER_BUS_AUTH_TOKEN`, …) sono scritte esplicitamente in `application.properties` o nel
compose. Allo stesso modo `<PREFISSO>_POOL_SIZE` del §1 è composto da `DbSettings` a partire dal
prefisso (`APP_DB`, `CONSUMER_STATE_DB`, `GLOBAL_VIEW_DB`).

## 1. Bundle del laboratorio (contratto d'ambiente)

Fonte: [`BundleDeployment`](../lab-support/src/main/java/com/evento/lab/cluster/support/BundleDeployment.java),
[`DbSettings`](../lab-support/src/main/java/com/evento/lab/cluster/support/DbSettings.java),
[`ContextSelection`](../lab-support/src/main/java/com/evento/lab/cluster/support/ContextSelection.java),
[`DataTargets`](../lab-support/src/main/java/com/evento/lab/cluster/support/DataTargets.java),
[`BundleRunner`](../lab-support/src/main/java/com/evento/lab/cluster/support/BundleRunner.java).

| Variabile | Default | Significato | Livello da cui serve |
|---|---|---|---|
| `EVENTO_BUNDLE_ID` | quello del modulo (`order-command`, `order-query`, `order-saga`, `order-observer`, `order-gateway`) | bundle id. Stesso id + stesso contesto = stessa lane (G24) | L0 (lo si cambia solo per creare lane indipendenti) |
| `EVENTO_INSTANCE_ID` | `EVENTO_BUNDLE_INSTANCE_ID`, poi `HOSTNAME`, poi il nome host | id dell'istanza; compare in `core__bundle_instance` e come `updated_by` nelle tabelle del laboratorio | L0 |
| `EVENTO_SERVER_ADDRESSES` | `localhost:3030` | `host:port,...` in ordine di preferenza; porta di default 3030. Prima di G2 si usava solo il primo | L0; lista da L1 |
| `EVENTO_CLUSTER_NAME` | nessuno | il `clusterName` che il broker deve annunciare (`withExpectedClusterName`, G13) | L1 |
| `EVENTO_EXPECTED_SERVER_ID` | `EVENTO_EXPECTED_SERVER_INSTANCE_ID`, poi nessuno | guard a id singolo di 2.4.6 (`withExpectedServerInstanceId`): **blocca il failover** | sconsigliato |
| `EVENTO_ZONE` | nessuna | zona annunciata (`Builder.setZone`, capability `zone:<Z>`), in maiuscolo; è anche la cella se vale `EU`, `AM` o `APAC` | L2 |
| `CELL` | dalla zona, o dalla regione | cella esplicita (raramente necessaria) | L3 |
| `EVENTO_CONTEXTS` | nessuno; **obbligatoria** per `order-query`, `order-saga`, `order-observer` | regioni (`EUROPA,AFRICA`, case-insensitive, `sud-america` ammesso), `ALL` (contesto letterale), oppure `*` da solo | L0 |
| `EVENTO_REGION` | `REGION`, poi nessuna; obbligatoria per il gateway | la regione servita dal gateway, cioè il contesto degli ordini che crea | L0 |
| `EVENTO_CHECKPOINT_MODE` | `CONSUMER_CHECKPOINT_MODE`, poi `DEFAULT` | `DEFAULT`, `WATERMARK` o `ON_START` (`Builder.setCheckpointMode`) | L0 |
| `APP_DB_URL` / `_USER` / `_PASSWORD` / `_POOL_SIZE` | nessuno / `postgres` / `secret` / **12** | database applicativo della cella (read model, side effect) | L0 |
| `CONSUMER_STATE_DB_URL` / `_USER` / `_PASSWORD` / `_POOL_SIZE` | = `APP_DB_*` / – / – / **12** | checkpoint, lock di lane, stato delle saghe | L3 (lane `ALL`: database condiviso) |
| `GLOBAL_VIEW_DB_URL` / … / `_POOL_SIZE` | = `APP_DB_*` / **4** | dove si legge `order_view_global` | L3 |
| `DATA_TARGETS` | vuoto | `nome=jdbc:...;nome=jdbc:...` (anche `,` davanti a `nome=jdbc:`); fan-out della lane `ALL`, diagnostica `?db=` del gateway. Un pool da 3 per target, 0 connessioni idle | L3 |
| `COMMAND_TIMEOUT_MS` | 30000 | timeout di comandi e query del gateway; diventa la deadline sul server (N3) | L0 |
| `EVENTO_HEARTBEAT_READ_IDLE_S` | nessuno (framework: 45 s) | rilevamento di un broker silenzioso (`Builder.setHeartbeatReadIdle`, G22) | L1 |
| `EVENTO_FAILBACK_WINDOW_S` | nessuno (framework: 30 s) | quanto il primo indirizzo deve essere sano prima che un bundle in failover ci torni (`Builder.setFailbackStabilityWindow`, N16); nel compose vuoto = default; `/whoami` riporta la feature `failback` | L1 |
| `JAVA_OPTS` | nel compose `LAB_JAVA_OPTS` | opzioni JVM | – |

Pool Hikari creati da [`LabDatabases`](../lab-support/src/main/java/com/evento/lab/cluster/support/LabDatabases.java):
`maximumPoolSize = max(3, <POOL_SIZE>)`, `minimumIdle` 1 (0 per i target), `connectionTimeout`
10 s, `validationTimeout` 3 s, `initializationFailTimeout=-1` (un target irraggiungibile all'avvio
non blocca il bundle). Un database uguale (stesso URL e utente) condivide il pool.

## 2. Variabili del compose e degli script

Fonte: [`../docker/compose.yaml`](../docker/compose.yaml), [`../scripts/`](../scripts/).

| Variabile | Default | Dove | Effetto |
|---|---|---|---|
| `LAB_TAG` | `dev` nel compose; negli script il tag dell'`evento-eu` in esecuzione | tutto | tag delle immagini (`before`, `after`) |
| `FRAMEWORK_REF` | albero di lavoro | `build-images.sh` | costruisce contro un altro ref del framework (worktree temporaneo) |
| `KEEP_FRAMEWORK_WORKTREE` | `false` | `build-images.sh` | tiene il worktree temporaneo |
| `GRADLE_ARGS` | `--offline` | `build-images.sh` | flag Gradle aggiuntivi |
| `SKIP_GRADLE` | `false` | `build-images.sh` | riusa i jar esistenti |
| `UP_TIMEOUT` | 900 | `up.sh` | attesa massima degli healthcheck (s) |
| `BUS_VIA_LB` | `false` | `up.sh` | i bundle usano il VIP della cella `bus-<cella>:3030` |
| `BUS_ADDR_EU` / `_AM` / `_APAC` | `evento-<c>:3030,<altri due via toxiproxy>` | compose | lista endpoint della cella |
| `REPLICA` | `false` | `up.sh` | aggiunge `order-command-eu-b` (profilo `replica`, S02) |
| `REGIONAL_OBSERVERS` | `false` | `up.sh` | aggiunge gli observer per cella (profilo `regional-observers`) |
| `COLD_START` | `false` | `up.sh` | aggiunge `compose.cold-start.yaml` (niente catena di avvio, S10) |
| `REPAIR_CATALOG` | `true` | `up.sh` | esegue `repair-catalog.sh` dopo l'avvio (su `:after` solo verifica) |
| `REPAIR_PASSES` / `REPAIR_PARALLEL` / `REPAIR_SETTLE_S` | 3 / 4 / 90 | `repair-catalog.sh` | passate, parallelismo, attesa per N6 |
| `PICK_STRATEGY` | `locality-preferred` | compose → `EVENTO_SERVER_BUS_PICK_STRATEGY` | pick degli handler dei server |
| `CONSUMER_CHECKPOINT_MODE` | `DEFAULT` | compose → `EVENTO_CHECKPOINT_MODE` | per tutti i consumer |
| `LAB_JAVA_OPTS` | `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k` | compose | JVM di server e bundle |
| `WAN_LATENCY_SCALE` / `WAN_JITTER_MS` | 100 (%) / 5 | `toxiproxy-init`, `wan.sh` | scala e jitter della matrice RTT |
| `TOXIPROXY_URL` | `http://localhost:8474` | `wan.sh`, `apply-wan.sh` | API di toxiproxy |
| `SYNC_STANDBY_NAMES` | vuoto | `pg/primary-init.sh` | `synchronous_standby_names` del primario (ES: `FIRST 1 (es_pg_am)`) |
| `EXTRA_DATABASES` | vuoto | `pg/primary-init.sh` | database in più (`consumers` sul cluster ES) |
| `REPLICATION_USER` / `REPLICATION_PASSWORD` | `replicator` / `replicator` | `pg/*.sh` | ruolo di replica |
| `PRIMARY_HOST` / `PRIMARY_PORT` / `STANDBY_NAME` | – | `pg/replica-entrypoint.sh` | da chi fare `pg_basebackup -R`; `application_name` dello standby |

## 3. Driver degli scenari (`evento-lab-cluster-chaos`)

| Variabile | Default | Effetto |
|---|---|---|
| `EVENTO_LAB_CLUSTER` | – (`run-scenario.sh` la mette a `true`) | abilita i test di scenario |
| `EVENTO_LAB_LABEL` | `run` | cartella dei risultati `results/<S>/<label>/` |
| `EVENTO_LAB_RESULTS_DIR`, `EVENTO_LAB_COMPOSE_FILE` | `../results`, `../docker/compose.yaml` (relativi al modulo) | percorsi |
| `EVENTO_LAB_LB_URL` / `EVENTO_LAB_TOXIPROXY_URL` | `http://localhost:8080` / `http://localhost:8474` | endpoint dal host |
| `EVENTO_LAB_ES_JDBC`, `EVENTO_LAB_CONSUMERS_JDBC`, `EVENTO_LAB_APP_JDBC_EU` / `_AM` / `_APAC`, `EVENTO_LAB_DB_USER` / `_PASSWORD` | porte host del compose, `postgres` / `secret` | accesso degli invarianti ai database |
| `EVENTO_LAB_HTTP_TIMEOUT_S` | 45 | timeout HTTP del driver |
| `EVENTO_LAB_READY_TIMEOUT_S` | 420 | attesa dei servizi pronti |
| `EVENTO_LAB_RESTORE_PLACEMENT` | `true` | riavvia i bundle fuori cella prima di ogni scenario (L22); con il failback N16 è una rete di sicurezza che normalmente non trova nulla |
| `EVENTO_LAB_PLACEMENT_SETTLE_S` | 20 | attesa dopo quel riavvio |
| `EVENTO_LAB_SKIP_RECREATE` | `false` | S08/S09 non ricreano lo stack alla fine |
| `LAB_BASH` | Git Bash su Windows | bash usata per `repair-catalog.sh` (L16) |
| `LAB_FAULT_RATE` | 4 | ordini/min per regione negli scenari con guasto |
| `S00_RATE`, `S00_ITEMS`, `S00_COMPLETE_RATIO`, `S00_DURATION_S`, `S00_CONVERGENCE_S` | 20, 2, 0.5, 60, 90 | S00 |
| `S01_PROBES`, `S01_SETTLE_S` | 5, 10 | S01 |
| `S02_RATE`, `S02_DURATION_S`, `S02_KILL_AFTER_S`, `S02_CONVERGENCE_S` | 60, 70, 20, 120 | S02 |
| `S03_RATE`, `S03_DURATION_S`, `S03_KILL_AFTER_S`, `S03_CONVERGENCE_S`, `S03_FAILBACK_WAIT_S` | `LAB_FAULT_RATE`, 110, 20, 180, 180 | S03 (l'ultima: attesa del failback N16 dopo il ritorno di `evento-apac`) |
| `S04_OUTAGE_S`, `S04_PAUSE_S`, `S04_SETTLE_S` | 45, 60, 60 | S04 |
| `S05_WORKERS_PER_REGION`, `S05_COMMANDS_PER_WORKER`, `S05_HOT_ORDERS`, `S05_CONVERGENCE_S` | 2, 30, 2, 120 | S05 |
| `S06_RTTS_MS`, `S06_STEP_S`, `S06_RATE`, `S06_CONVERGENCE_S` | `0,50,150,300`, 40, 30, 180 | S06 |
| `S07_PARTITION_S`, `S07_START_AFTER_S`, `S07_AFTER_HEAL_S`, `S07_RATE`, `S07_CONVERGENCE_S` | 60, 20, 40, `LAB_FAULT_RATE`, 240 | S07 |
| `S08_PROMOTE`, `S08_PROMOTE_DELAY_S`, `S08_KILL_AFTER_S`, `S08_DURATION_S`, `S08_RATE`, `S08_CONVERGENCE_S` | `es-pg-am`, 5, 30, 120, `LAB_FAULT_RATE`, 180 | S08 (`es-pg-apac` per la variante asincrona) |
| `S09_PROMOTE`, `S09_PROMOTE_DELAY_S`, `S09_KILL_AFTER_S`, `S09_DURATION_S`, `S09_RATE`, `S09_CONVERGENCE_S` | `app-pg-eu-r`, 5, 30, 120, `LAB_FAULT_RATE`, 240 | S09 |
| `S10_ITERATIONS`, `S10_BOOT_TIMEOUT_S` | 3, 360 | S10 |
| `S11_ORDERS`, `S11_LEAK_WAIT_S`, `S11_CONVERGENCE_S` | 5, 60, 90 | S11 |
| `S12_RATE`, `S12_PHASE_S`, `S12_IDLE_S`, `S12_NOISE_FLOOR_MS` | 120, 60, 30, 1000 | S12 (L19) |
| `S13_BUS_ADDRESSES`, `S13_WAIT_S`, `S13_FAILBACK_WAIT_S` | `bus-eu:3030`, 60, 180 | S13 (l'ultima: attesa del failback N16 dei JVM EU dopo il ritorno di `evento-eu`) |
| `S14_KILL_DELAY_MS`, `S14_PROBES_PER_KIND`, `S14_RETRY_REGION` | 150, 8, `EUROPA` | S14 (2000 per catturare G5, L17) |
| `S15_IMAGE`, `S15_FAKETIME_ENV` | – | S15: immagine con libfaketime e il suo ambiente (L18) |
| `S16_EVENTS`, `S16_OUTAGE_EVENTS`, `S16_OUTAGE_S`, `S16_CONVERGENCE_S` | 5, 4, 40, 120 | S16 |
| `S17_ORDERS`, `S17_CONVERGENCE_S` | 2, 90 | S17 |

## 4. Proprietà del server che riguardano il cluster

Fonte: [`application.properties`](../../evento-server/src/main/resources/application.properties),
`BusProperties`, `DistributedLockConfiguration`, `EventStore`, `EventStoreRequestHandlerBridge`,
`BundlePresence`, `SchemaInitializationConfiguration`.

| Proprietà | Variabile d'ambiente | Default | Significato |
|---|---|---|---|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/evento` | event store; in cluster multi-host con `targetServerType=primary` (L4) |
| `spring.datasource.username` / `password` | `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `postgres` / `secret` | |
| `spring.datasource.hikari.maximum-pool-size` | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | 20 (min idle 5) | pool dati del server |
| `spring.datasource.hikari.data-source-properties.{tcpKeepAlive,loginTimeout,connectTimeout,socketTimeout}` | – | `true`, 30, 30, 30 | timeout del driver |
| `evento.lock.pool.maximum-size` | `EVENTO_LOCK_POOL_MAXIMUM_SIZE` | 10 | connessioni del lock pool (G3): lock tenuti contemporaneamente |
| `evento.lock.pool.connection-timeout-ms` | `EVENTO_LOCK_POOL_CONNECTION_TIMEOUT_MS` | 1000 | attesa di una connessione del lock pool |
| `evento.lock.acquire.timeout.ms` | `EVENTO_LOCK_ACQUIRE_TIMEOUT_MS` | 60000 | limite di ogni attesa di lock distribuito (o la deadline del comando) |
| `evento.es.mode` | `EVENTO_ES_MODE` | `CPES` in `application.properties` (`APES` nel codice) | **CPES** obbligatorio con più server |
| `evento.es.fetch.max.age.ms` | `EVENTO_ES_FETCH_MAX_AGE_MS` | 30000 | gate di staleness dei fetch; ≤ 0 lo disattiva (S15) |
| `evento.es.fetch.concurrency` | `EVENTO_ES_FETCH_CONCURRENCY` | 4 | fetch concorrenti per server |
| `evento.es.fetch.delay` | `EVENTO_ES_FETCH_DELAY` | 69 | finestra di lettura APES (ms) |
| `evento.es.aggregate.{state,events}.cache.size` / `.expiry` | – | 1024 / 150000 | cache per server, non invalidate tra server (G19) |
| `evento.es.schema.enforce-primary-key` | `EVENTO_ES_SCHEMA_ENFORCE_PRIMARY_KEY` | `true` | PK su `es__events` aggiunta all'avvio (G6) |
| `spring.sql.init.mode` | – | `always` | `schema.sql` a ogni avvio, sotto advisory lock dopo G14 |
| `evento.cluster.name` | `EVENTO_CLUSTER_NAME` | `evento-server` | annunciato in `Welcome.clusterName` (G13); cambialo |
| `evento.cluster.node.server.id` | `EVENTO_CLUSTER_NODE_SERVER_ID` | `iris-evento-server` | diventa `evento.server.bus.server-instance-id`; **univoco** per server (G4, G7) |
| `evento.server.instance.id` | `EVENTO_SERVER_INSTANCE_ID` | `server-0` | id usato da `ConsumerService`; univoco |
| `evento.server.bus.port` | `EVENTO_SERVER_BUS_PORT` | 3030 | porta del bus |
| `evento.server.bus.pick-strategy` | `EVENTO_SERVER_BUS_PICK_STRATEGY` | `random` | `random`, `first`, `locality-preferred` (G12) |
| `evento.server.bus.heartbeat-write-idle` / `heartbeat-read-idle` | `EVENTO_SERVER_BUS_HEARTBEAT_WRITE_IDLE` / `_READ_IDLE` | 15 s / 45 s | heartbeat lato server |
| `evento.server.bus.connect-timeout` | – | 5 s | |
| `evento.server.bus.business-executor-{core-size,max-size,queue-capacity}` | – | max(8, 2×CPU), 8×CPU, 256 | esecutore delle richieste (la discovery ci gira, G18) |
| `evento.server.bus.auth-token` | `EVENTO_SERVER_BUS_AUTH_TOKEN` | vuoto | segreto condiviso dei bundle |
| `evento.server.presence.heartbeat-interval-ms` | `EVENTO_SERVER_PRESENCE_HEARTBEAT_INTERVAL_MS` | 30000 | aggiornamento di `core__bundle_instance` (G7) |
| `evento.server.presence.stale-after-ms` | `EVENTO_SERVER_PRESENCE_STALE_AFTER_MS` | 180000 | dopo quanto una riga di presenza non conta più |
| `spring.task.scheduling.pool.size` | – | 2 | presenza e pulizia della telemetria non si accodano |
| `evento.telemetry.ttl`, `evento.performance.capture.rate` | – | 365, 1 | telemetria (la pulizia è eletta su un server) |

Metriche utili: `evento.server.requests.expired{stage=queued|lock_wait|before_forward|before_append}` (N3),
`evento.server.forwarding.table.size`, `evento.server.forwarded`. Endpoint: `/actuator/health/readiness`
(porta 3000), `GET /api/cluster-status/zones`, `GET /api/cluster-status/attended-view`.

## 5. API del bundle (framework)

Chiamate da `BundleRunner` / `FrameworkFeatures` (per riflessione, così la stessa sorgente compila
anche contro 2.4.6; `/whoami` dice quali sono state applicate).

| API | Default | Effetto |
|---|---|---|
| `new EventoServerMessageBusConfiguration(ClusterNodeAddress...)` | – | lista ordinata di endpoint (G2) |
| `EventoServerMessageBusConfiguration.withExpectedClusterName(String)` | nessuno | rifiuta broker di un altro cluster (G13) |
| `EventoServerMessageBusConfiguration.withExpectedServerInstanceId(String)` | nessuno | accetta un solo broker (blocca il failover) |
| `EventoBundle.Builder.setZone(String)` | nessuna | capability `zone:<Z>` per `LOCALITY_PREFERRED` |
| `EventoBundle.Builder.setComponentContexts(Class, String...)` | voce assente = `*` | un engine per contesto |
| `EventoBundle.Builder.setCheckpointMode(CheckpointMode)` | `DEFAULT` | anche `WATERMARK`, `ON_START` |
| `EventoBundle.Builder.setHeartbeatReadIdle(Duration)` | 45 s (`NettyTransportConfig.defaults()`; write-idle 15 s) | rilevamento di un broker silenzioso |
| `EventoBundle.Builder.setDefaultRequestTimeout(Duration)` | 30 s | timeout (e deadline) di default |
| `EventoBundle.Builder.setMaxInFlightRequests(int)` | 2048 | richieste in volo per bundle |
| `EventoBundle.Builder.setFailbackEnabled(Boolean)` | `null` = attivo con 2+ indirizzi | failback all'endpoint 0 dopo un failover (N16); `false` = torna solo alla disconnessione successiva |
| `EventoBundle.Builder.setFailbackProbeInterval(Duration)` | 5 s | cadenza della sonda TCP + `Ping` verso l'endpoint 0 (nessun `Hello`; il server risponde solo quando è pronto); anche il ritardo massimo del ritorno reattivo dopo un `no handler for` |
| `EventoBundle.Builder.setFailbackStabilityWindow(Duration)` | 30 s | quanto l'endpoint 0 deve rispondere a ogni sonda prima del ritorno; una sonda fallita la azzera; ×2 dopo ogni ritorno fallito (max 16×); dopo 3 rifiuti di fila dell'handshake il failback si sospende; un `no handler for` dal server attuale la salta (ritorno reattivo) |
| `EventoBundle.Builder.setFailbackJitter(Duration)` | 10 s | ritardo casuale massimo aggiunto per istanza; i bundle senza handler aspettano in più l'intero intervallo (prima: una finestra intera, 30 s) |
| `EventoBundle.Builder.setFailbackDrainTimeout(Duration)` | 10 s | attesa massima delle richieste in volo; le uscenti rimaste falliscono con `BrokerChangedException` |
| `EventoBundle.Builder.setTransportConfig(NettyTransportConfig)` | `NettyTransportConfig.defaults()` (connect 5 s) | trasporto completo |
| `EventoBundle.Builder.setConsumerEngineConfigBuilder(...)` | in-memory | nel laboratorio JDBC: `JdbcConsumerLock`, `JdbcConsumerStateStore`, `JdbcSagaStateStore`, `JdbcDeadEventQueue`, `JdbcDedupeStore` |

## 6. Postgres nel laboratorio

Da `x-pg-command` nel compose, uguali per tutti i nodi:

| Parametro | Valore | Perché |
|---|---|---|
| `max_connections` | 200 | budget: 120 al picco sul primario ES |
| `shared_buffers` / `work_mem` | 32MB / 2MB | contenitori piccoli |
| `wal_level`, `max_wal_senders`, `wal_keep_size` | `replica`, 10, 512MB | streaming replication |
| `hot_standby`, `hot_standby_feedback` | `on`, `on` | letture sulle repliche |
| `synchronous_commit` | `on` | con `synchronous_standby_names` sul primario ES |
| `wal_sender_timeout` / `wal_receiver_timeout` | 30s / 30s | rilevamento della replica rotta |
| `tcp_keepalives_idle` / `_interval` / `_count` | 10 / 5 / 3 | backend orfani (e i loro lock) chiusi in ~25 s (G25, N11) |
| `client_connection_check_interval` | 5s | idem durante le query lunghe |
