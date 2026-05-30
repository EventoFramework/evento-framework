# Evento Framework v2.0 — major rewrite (P2P resilience + SOLID + modern stack)

## Context

Il framework `evento-framework` (`/Users/ggalazzo/workspace/personal/evento-framework/`, attualmente versione `ev1.15.5` su Spring Boot 3.2.2 + Java 21) soffre di **instabilità nella connessione P2P** tra `evento-bundle` e `evento-server`, e di una pesante **erosione architetturale** che rende difficile manutenere e estendere il codice.

Cause principali dell'instabilità (verificate leggendo il codice):

1. **Heartbeat unidirezionale** (server→bundle). Il bundle non rileva mai un server morto: la socket "zombie" resta aperta finché il prossimo `send()` non scatta in timeout (`MessageBus.java:241-268`).
2. **Lock tenuto durante sleep di reconnect** (`EventoSocketConnection.java:184-272`): `synchronized(connectingLock) { ... Sleep.apply(reconnectDelayMillis); ... }` blocca *tutti* i `send()` concorrenti per la durata della pausa → cascate di timeout percepiti dai chiamanti.
3. **Reconnect a delay fisso** (~2s, no backoff, no jitter) → thundering herd al restart del server.
4. **Race lato server**: `MessageBus.send()` (riga 638-691) sincronizza su uno stream che `leave()` (riga 822-851) può chiudere in parallelo, generando IOException ripetute.
5. **`System.exit(1)` in `ClusterConnection.connect()`**: il bundle muore ungovernabilmente se non trova nessun nodo.
6. **Listener iterati dentro `synchronized` su `ArrayList`**: rischio deadlock e CME.
7. **Shutdown unbounded**: `MessageBus.destroy()` può attendere fino a `disableDelayMillis × maxDisableAttempts` (default ~150s) per le correlation pendenti.

In parallelo, problemi strutturali (SOLID):

- `MessageBus.java` (932 righe) — accept loop + routing + registry + heartbeat + lifecycle + listener (SRP grave)
- `EventoSocketConnection.java` (465 righe) — I/O + reconnect + heartbeat reply + state flags
- `EventoServerClient.java` (452 righe) — correlation + dispatcher + consumer registry
- Dispatch via `switch` su tipo concreto → OCP violato
- Nessuna astrazione `Transport` → DIP violato
- `@PostConstruct init()` con side-effect (avvia thread + scheduler) → lifecycle non esplicito
- Java serialization "accetta tutto sotto `com.evento.*`" → superficie d'attacco gadget chain
- 4 listener type duplicati (`viewListeners`, `availableViewListeners`, `joinListeners`, `leaveListeners`) invece di un singolo `Consumer<BusEvent>`

### Scope concordato (decisioni utente)

1. **Major release** (`v2.0.0`). **Niente retrocompatibilità wire-format**: libertà totale di ridisegnare il filo.
2. **Riscrittura from-scratch** di 4 layer: trasporto, bus/routing server, client bundle, consumer state store / saga / projector.
3. **Java 25 LTS** (rilascio ott 2025). Include **JEP 491** (synchronize senza pinning di virtual threads), structured concurrency, scoped values. Richiede bump Spring Boot a `3.5.x+` (verificare compatibilità al merge).
4. **Wire format nuovo**: length-prefixed frame + envelope binario + **Jackson CBOR** payload. Polimorfismo con whitelist tipizzata via `@JsonTypeInfo` (no più `Serializable`-anything).
5. **Netty 4.1** come trasporto sottostante (event-loop + backpressure nativa).
6. **3 PR sequenziali su branch `next`**. `next` → `main` come tag `v2.0.0` solo dopo soak completo dell'integrazione.

---

## Architettura target (high-level)

```
┌────────────────────────────────────────────────────────────┐
│                  evento-transport-api                       │
│  Transport, Codec, Envelope, sealed Message hierarchy,      │
│  ConnectionState, ReconnectStrategy, HandshakeProtocol      │
└────────────────────────────────────────────────────────────┘
                            ▲
            ┌───────────────┴───────────────┐
            │                               │
┌───────────────────────┐         ┌────────────────────────┐
│ evento-transport-netty│         │ evento-transport-test  │
│  Netty pipeline +     │         │  In-memory transport   │
│  Jackson CBOR codec   │         │  per unit test         │
└───────────────────────┘         └────────────────────────┘
            ▲
            │
┌───────────────────────┐         ┌────────────────────────┐
│   evento-server       │◄────────│    evento-bundle       │
│  SocketServer +       │  TCP    │  EventoBundleClient +  │
│  Router + Registry +  │ Netty   │  CorrelationTracker +  │
│  Heartbeat + Lifecycle│         │  ConsumerRegistry      │
└───────────────────────┘         └────────────────────────┘
                                              ▲
                                              │
                                  ┌───────────────────────────┐
                                  │  evento-consumer-state    │
                                  │  ConsumerStateStore SPI + │
                                  │  Saga + Projector engine  │
                                  └───────────────────────────┘
```

---

## Critical files / moduli toccati

**Da eliminare/sostituire** (riscrivere from scratch):
- `evento-server/src/main/java/com/evento/server/bus/MessageBus.java` (932 righe)
- `evento-bundle/src/main/java/com/evento/application/bus/EventoSocketConnection.java` (465 righe)
- `evento-bundle/src/main/java/com/evento/application/bus/EventoServerClient.java` (452 righe)
- `evento-bundle/src/main/java/com/evento/application/bus/ClusterConnection.java`
- `evento-bundle/src/main/java/com/evento/application/bus/EventoSocketConfig.java`
- `evento-common/src/main/java/com/evento/common/serialization/ObjectMapperUtils.java` (rimpiazzato da nuovo `CborCodec`)
- `evento-consumer-state-store/**` (riscritto)

**Da introdurre**: vedi mappa moduli sotto.

**Da preservare** (API pubbliche per chi usa il framework):
- Annotation API (`@Aggregate`, `@CommandHandler`, `@EventHandler`, `@Saga`, ecc.)
- Bundle bootstrap API (`EventoBundle.Builder`)
- Modeling API (`AggregateState`, `Event`, `Command`, `Query`)
- Spring/Boot starter API per il server (controller REST esistenti)

---

## Wire protocol v2 (nuovo)

Ogni messaggio su socket è:

```
[4 bytes len BE]
[1 byte protocolVersion]    // 0x02
[1 byte messageType]        // tag su sealed hierarchy
[16 bytes correlationId]    // UUID o zeri
[2 bytes flags]             // bit0=isResponse, bit1=isCompressed, bit2=hasException...
[2 bytes reserved]
[N bytes CBOR payload]
```

- `LengthFieldBasedFrameDecoder(maxFrame=16MB, lengthFieldOffset=0, lengthFieldLength=4)` lato in
- `LengthFieldPrepender(4)` lato out
- Payload: Jackson CBOR con `@JsonTypeInfo` + whitelist registry (`MessageTypeRegistry.register(byte tag, Class<? extends Message>)`)
- Compressione opzionale (LZ4) sopra una soglia configurabile (default 8KB)
- Handshake versionato: client invia `Hello(protocolVersion, bundleId, instanceId, bundleVersion, capabilities)`, server risponde `Welcome(serverVersion, acceptedCapabilities)` o `Reject(reason)`.
- Heartbeat: `Ping(seq, ts)` / `Pong(seq, ts)` come tipi di primo livello. `IdleStateHandler` di Netty triggera Ping su lato sleep e chiude canale se Pong non arriva.

---

## PR 1 / Phase 1 — Nuovo trasporto + Java 25 + integrazione

**Branch:** `next` (commit PR1) · **Effort:** L (~3-4 settimane) · **Wire-format nuovo, rompe v1.**

Obiettivo: il framework continua a buildare e a girare end-to-end, ma sopra un trasporto totalmente nuovo. Server e bundle internamente sono ancora "vecchio stile" ma usano la nuova API via adapter sottili. La PR2 e PR3 li riscriveranno.

### 1.1 Upgrade toolchain

- `build.gradle` root: `sourceCompatibility/targetCompatibility = "25"`.
- Plugin Spring Boot: `id 'org.springframework.boot' version '3.5.x'` (versione esatta da verificare al merge; Spring Boot 3.5 supporta Java 24 ufficialmente, Java 25 con preview off).
- Gradle wrapper: `gradle-8.10+` (Java 25 toolchain support).
- `gradle.properties`: rimuovere preview flags se accesi.
- CI: bump Docker base image a `eclipse-temurin:25-jdk`.

### 1.2 Modulo `evento-transport-api`

Nuovo modulo Gradle. Path: `evento-transport-api/`.

```
evento-transport-api/src/main/java/com/evento/transport/
├── Transport.java                    // SPI: connect(), send(Message), onMessage(...), close()
├── TransportFactory.java             // Pluggable: client/server transport
├── ConnectionState.java              // enum DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, CLOSING, CLOSED
├── ConnectionStateMachine.java       // AtomicReference + compareAndTransition
├── ReconnectStrategy.java            // interface
├── ExponentialBackoffWithJitter.java // impl default
├── HandshakeProtocol.java            // versioned Hello/Welcome/Reject
├── codec/
│   ├── Codec.java                    // encode/decode Message ↔ ByteBuffer
│   ├── Envelope.java                 // record(version, type, correlationId, flags, payload)
│   └── MessageTypeRegistry.java      // tag ↔ Class<? extends Message>
└── message/
    ├── Message.java                  // sealed interface
    ├── Hello.java                    // record
    ├── Welcome.java                  // record
    ├── Reject.java                   // record
    ├── Ping.java                     // record (correlationId zeros, payload {seq, ts})
    ├── Pong.java                     // record
    ├── Request.java                  // record (correlationId, payload Object)
    ├── Response.java                 // record (correlationId, body|exception)
    ├── BusEvent.java                 // sealed for join/leave/enable/disable/heartbeat-timeout
    └── Notification.java             // record (one-way)
```

**Cose chiave**:

- `Message` è una **sealed interface** con permits espliciti. Aggiungere un tipo nuovo = aggiungere a permits + registrare tag.
- Tutti i tipi sono **records**, immutabili.
- `Codec` ha un'unica impl di default (`CborCodec`) nel modulo netty, ma il SPI è qui.
- `Transport` non sa nulla di Netty; è una pura SPI.

### 1.3 Modulo `evento-transport-netty`

Path: `evento-transport-netty/`.

```
evento-transport-netty/src/main/java/com/evento/transport/netty/
├── NettyClientTransport.java         // implements Transport (client side)
├── NettyServerTransport.java         // implements Transport (server side, accept loop)
├── CborCodec.java                    // Jackson CBOR + envelope frame
├── EnvelopeFrameDecoder.java         // LengthFieldBasedFrameDecoder + envelope parse
├── EnvelopeFrameEncoder.java         // envelope build + LengthFieldPrepender
├── HeartbeatHandler.java             // IdleStateHandler bridge
├── BackpressureHandler.java          // channelWritabilityChanged → DEGRADED
└── ChannelInitializerFactory.java    // builds the pipeline
```

Pipeline server:
```
ch.pipeline()
  .addLast(new EnvelopeFrameDecoder())
  .addLast(new EnvelopeFrameEncoder())
  .addLast(new CborCodec(typeRegistry, classloader))
  .addLast(new IdleStateHandler(hbReadIdle, hbWriteIdle, 0))
  .addLast(new HeartbeatHandler())
  .addLast(new BackpressureHandler())
  .addLast(new MessageInboundHandler(transportListener));
```

- **Boss/worker**: `NioEventLoopGroup(1)` boss, `NioEventLoopGroup()` worker (auto-size). Su Linux: `EpollEventLoopGroup` (riflessione/optional).
- **Backpressure**: `WRITE_BUFFER_HIGH_WATER_MARK=64KB`, `LOW=32KB`. `BackpressureHandler.channelWritabilityChanged()` → state machine `DEGRADED` finché ritorna writable.
- **Handler business eseguiti su `VirtualThreadEventExecutor`** (custom adapter) per non bloccare l'EventLoop con la deserialization CBOR + dispatch.
- **CBOR + whitelist**: `ObjectMapper m = new CBORMapper()` con `m.activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfSubType(Message.class).build(), DefaultTyping.NON_FINAL)` — vincolato alla sealed hierarchy `Message`.
- **Compressione opzionale**: `LZ4FrameDecoder/Encoder` dietro flag in handshake capabilities.

### 1.4 Bridge minimale lato server (per ora)

`evento-server/src/main/java/com/evento/server/bus/MessageBus.java` viene **modificato** (non ancora cancellato — quello succede in PR2) per:

- usare `NettyServerTransport` invece del `ServerSocket` raw (riga 152-232 → cancellato).
- ricevere `Message` (sealed) invece di `Serializable` → switch ancora presente, ma sui tipi sealed.
- mantenere temporaneamente tutto il routing/registry esistente per non rompere `DashboardController` & co.

Stesso trattamento per `EventoSocketConnection.java` lato bundle: usa `NettyClientTransport`, mantiene il resto.

### 1.5 Heartbeat e reconnect (built-in nel trasporto)

- `HeartbeatHandler` invia `Ping` se idle in write per `hbInterval` ms. Riceve `Pong` con seq match.
- `IdleStateHandler` con `readerIdleTime = 3 × hbInterval` triggera `userEventTriggered(IdleStateEvent)` → chiude canale → state machine va in `DISCONNECTED` → ReconnectStrategy decide il prossimo backoff.
- `ExponentialBackoffWithJitter`: `min(maxBackoff, base × 2^(attempt-1)) × (1 ± jitter)`. Default `base=500ms`, `max=30s`, `jitter=0.2`. Reset a 0 al handshake riuscito.
- **No System.exit anywhere**: `Transport` espone `onStateChange(Consumer<ConnectionState>)` e il chiamante decide.

### 1.6 Test PR1

- **Unit `CborCodecTest`**: round-trip su ciascun tipo `Message`. Whitelist enforcement: tentare di deserializzare un payload con tipo non in whitelist → fail.
- **Unit `EnvelopeFrameTest`**: malformed frames, frame troppo grandi, frame split su chunk multipli.
- **Unit `ConnectionStateMachineTest`**: transizioni legali/illegali, thread safety con 100 thread.
- **Unit `ExponentialBackoffWithJitterTest`**: math + jitter range.
- **Integration `TransportIntegrationTest`**: client+server in-process via Netty + localhost. Casi: connect, send/receive, kill server, reconnect, kill rete (drop tcp via Toxiproxy o `NetworkEmulator` jvm-only), shutdown.
- **Integration `EndToEndV2Test`**: server completo + bundle `evento-demo` → registrazione, 1k command, 1k query, 100 events, heartbeat 60s.

### Output PR1

- ✅ Framework compila su Java 25.
- ✅ Server + bundle scambiano messaggi sul nuovo wire format v2.
- ✅ Tutti i test esistenti passano (con adattamenti minori).
- ✅ Nessun bundle v1 funziona — è una breaking release attesa.

### Rischi PR1

- **R1**: Spring Boot 3.5 + Java 25 compatibilità non garantita. *Mitigazione*: smoke test al primo step di upgrade. Se Spring Boot 3.5 non supporta Java 25 → ripiego Java 24 fino al rilascio di Spring Boot 3.6.
- **R2**: blocking I/O dentro EventLoop di Netty (CBOR decode, dispatch). *Mitigazione*: tutti gli handler business su executor virtuale dedicato, mai sull'EventLoop.
- **R3**: CBOR polymorphic deserialization è superficie d'attacco se whitelist è troppo lasca. *Mitigazione*: vincolo `allowIfSubType(Message.class)` + ogni messaggio deve essere registrato tramite tag esplicito in `MessageTypeRegistry`. Niente "accetta tutto sotto package".
- **R4**: latenza CBOR + virtual thread switch può crescere su microcarichi. *Mitigazione*: benchmark JMH dedicato (in `evento-transport-netty/src/jmh/`).

---

## PR 2 / Phase 2 — Server rewrite

**Branch:** `next` (commit PR2 dopo PR1) · **Effort:** XL (~4-5 settimane) · **Wire-format invariato dopo PR1.**

Obiettivo: cancellare `MessageBus.java` e ricostruire il server lato bus/routing/lifecycle con classi piccole, focused, testabili. Tutto dietro un'unica facade Spring (`MessageBusFacade`) per non rompere i consumer Spring esistenti (`DashboardController`, `AutoDiscoveryService`, `BundleDeployService`, `ClusterStatusController`).

### 2.1 Nuovo design (composizione)

Tutti i file in `evento-server/src/main/java/com/evento/server/bus/v2/`:

| Componente | Responsabilità unica | Dipendenze |
|---|---|---|
| `SocketServer` | Bind + accept loop. Wrapper su `NettyServerTransport`. `start()` / `stop()` espliciti. | `NettyServerTransport` |
| `ConnectionRegistry` | Mappa atomica `NodeAddress → Connection`. API `register/unregister/enable/disable/snapshot()`. | – |
| `ClusterRegistry` | Mappa `payloadType → Set<NodeAddress>`. `registerHandlers(node, list)`, `removeNode(node)`, `pickHandler(type)`. | `ConnectionRegistry` |
| `MessageRouter` | Riceve `Message`, dispatcha via `Map<Class<? extends Message>, Handler>` registrato in `start()`. Niente switch. | `ClusterRegistry`, `CorrelationStore` |
| `CorrelationStore` | `ConcurrentHashMap<UUID, PendingCorrelation>`. `track`, `complete`, `expire`. Scheduler cleanup. | – |
| `HeartbeatService` | Invia `Ping` periodici a ogni connection. Traccia `lastPongMs[node]`. Failure detection → `ConnectionRegistry.unregister`. | `ConnectionRegistry` |
| `LifecycleManager` | Orchestrator. `@PostConstruct start()`, `@PreDestroy stop()` con bounded deadline. | tutte sopra |
| `MessageBusFacade` | API pubblica (façade Spring `@Component`). Espone `getCurrentView`, `getCurrentAvailableView`, `isBundleAvailable`, `forward`, `waitUntilAvailable`, `subscribe(Consumer<BusEvent>)`. | `LifecycleManager` |

Listener: **un solo entry point** `subscribe(Consumer<BusEvent>)` dove `BusEvent` è sealed `JoinEvent | LeaveEvent | EnableEvent | DisableEvent | ViewChangedEvent | HeartbeatTimeoutEvent`. I 4 listener type vecchi vengono cancellati. I consumer (Dashboard, AutoDiscovery, BundleDeploy) vengono aggiornati a usare `subscribe` + pattern matching.

### 2.2 SOLID

- **SRP**: ogni classe della tabella ha una responsabilità singola.
- **OCP**: `MessageRouter` registra handler in `Map<Class, Handler>` in `start()`. Aggiungere un message type = `register(NewMessage.class, h)`, niente switch da toccare.
- **LSP**: `Transport` SPI rispettata da Netty/in-memory/test.
- **ISP**: invece di 4 listener type, 1 `Consumer<BusEvent>` con sealed event. Consumer interessati a un solo tipo: `case JoinEvent e -> ...; case LeaveEvent e -> ...; default -> {}`.
- **DIP**: `SocketServer`, `MessageRouter`, `HeartbeatService` dipendono da `Transport` interface, non da Netty.

### 2.3 Concorrenza

- `LifecycleManager.shutdown(Duration deadline)`: hard deadline (default 30s). Scaduto → cancella correlation pendenti con `Response(ShutdownException)`.
- Virtual threads (Java 25, no pinning) per ogni handler invocation. `Executors.newVirtualThreadPerTaskExecutor()`.
- `CorrelationStore`: `ConcurrentHashMap` + scheduler periodico cleanup.
- Race con `leave()`: API `MessageRouter.send(to, msg)` usa snapshot atomico via `ConnectionRegistry.lookup(to)` che ritorna `Optional<Connection>`; se vuoto → `RouteNotFoundException` tipizzata (non `RuntimeException` generica).

### 2.4 Observability

- Micrometer:
  - `evento.server.connections{state}`
  - `evento.server.heartbeat.lag{node}`
  - `evento.server.correlations.pending`
  - `evento.server.message.processing.duration{type}`
- Structured logging via SLF4J + JSON encoder (logback-json o slf4j2-jansi). Key=value su ogni transizione.
- `MessageBusFacade.healthReport()`: ritorna un record `HealthReport(connections, pending, hbLagP99, ...)` per il `/actuator/health` custom contributor.

### 2.5 Eliminazione

- Delete: `evento-server/src/main/java/com/evento/server/bus/MessageBus.java`.
- I controller (`DashboardController`, ecc.) puntano a `MessageBusFacade` invece che a `MessageBus`. Stesso package, stesso nome bean Spring se vogliamo evitare migration (`@Component("messageBus")` sulla facade).

### Test PR2

- **Unit per ogni componente** (~7 file, ~20 test classes).
- **Contract test facade**: enumerare via grep tutti i call site (`grep -rn "messageBus\." evento-server/` + GUI), verificare che ogni metodo della vecchia API esista sulla facade con stessa firma (o equivalente migrato).
- **Concurrency test**: 100 thread che fanno join/leave/send simultanei, 10k iterazioni; verifica zero NPE, zero stato inconsistente, zero leak di correlation.
- **Shutdown test**: lifecycle.start → carico → stop con deadline 5s → verifica completion entro 5.5s + tutte le correlation completate (con esito o ShutdownException).

### Rischi PR2

- **R1**: facade incompleta rompe i controller Spring esistenti. *Mitigazione*: contract test enumerativo + matrix di smoke test API.
- **R2**: lifecycle migration può cambiare timing di startup → test che dipendono dall'ordine bean potrebbero rompersi. *Mitigazione*: revisione `@DependsOn` esistenti.

---

## PR 3 / Phase 3 — Bundle + ConsumerStateStore + Saga/Projector

**Branch:** `next` (commit PR3 dopo PR2) · **Effort:** XL (~5-6 settimane) · **Wire-format invariato.**

Obiettivo: cancellare il lato client e il consumer state store. Ridisegnarli da zero con SPI pulite.

### 3.1 Bundle client rewrite

Tutto in `evento-bundle/src/main/java/com/evento/application/bus/v2/`:

| Componente | Responsabilità | Dipendenze |
|---|---|---|
| `EventoBundleClient` | Façade `implements EventoServer`. API pubblica invariata (`request`, `send`, ecc.). Bean Spring. | i sotto |
| `BundleTransport` | Wrapper su `NettyClientTransport` con `ClusterFailoverStrategy` per multi-server. | `NettyClientTransport` |
| `ClusterFailoverStrategy` | Interface + impl `RoundRobin`, `Random`, `Sticky`. **Niente `System.exit`** mai. | – |
| `CorrelationTracker` | Mappa `UUID → CompletableFuture`. `track(id, future, timeout)`, `complete`, `expire`. Cleanup task. | – |
| `RequestDispatcher` | `<T> CompletableFuture<T> request(Object body, Duration timeout)`. Usa Transport + Correlation. | `BundleTransport`, `CorrelationTracker` |
| `IncomingMessageDispatcher` | `Map<Class<? extends Message>, Handler>` per i messaggi dal server. Sostituisce switch riga 101-162. | – |
| `ConsumerRegistry` | Registra command/event/query/saga/observer handler al server in `start()`. | `RequestDispatcher` |
| `HandshakeBootstrap` | Esegue `Hello/Welcome` esplicito. Versione + capabilities. | `BundleTransport` |
| `BundleLifecycle` | `start()` / `stop(Duration deadline)` esplicito. Bounded shutdown. | tutti sopra |

`EventoSocketConnection`, `EventoServerClient`, `ClusterConnection`, `EventoSocketConfig` → **cancellati**.

### 3.2 ConsumerStateStore — nuova SPI

In `evento-consumer-state-store/src/main/java/com/evento/consumer/v2/`:

```java
public sealed interface ConsumerCheckpoint permits EventCheckpoint, SagaCheckpoint, ProjectorCheckpoint { }

public interface ConsumerStateStore extends AutoCloseable {
    Optional<ConsumerCheckpoint> read(String consumerId);
    void commit(String consumerId, ConsumerCheckpoint checkpoint, long expectedVersion) throws OptimisticLockException;
    void delete(String consumerId);
    Stream<String> listConsumers();
}

public interface DedupeStore {
    boolean tryClaim(String consumerId, String eventId);  // returns false if already seen
    void release(String consumerId, String eventId);
}
```

Impl di default:
- `InMemoryConsumerStateStore` (test)
- `JdbcConsumerStateStore` (Postgres/MySQL, con Flyway migrations)
- `MongoConsumerStateStore` (futuro, separato)
- `JdbcDedupeStore` con tabella `consumer_dedupe(consumer_id, event_id, ts)` + cleanup periodico.

### 3.3 Saga + Projector engine

In `evento-bundle/src/main/java/com/evento/application/consumer/v2/`:

- `SagaEngine`: legge eventi dal server, deserializza, lookup saga state via `ConsumerStateStore`, invoca `@SagaEventHandler` con stato + event, persiste nuovo stato + commit checkpoint atomico (1 transazione DB se Jdbc).
- `ProjectorEngine`: idem ma senza state lookup, solo dispatch su `@EventHandler`. Checkpoint atomico via `DedupeStore`.
- `ObserverEngine`: side effects "at least once". Dedupe via `DedupeStore`. Configurabile retry policy.
- Tutti gli engine usano **structured concurrency** (Java 25 `StructuredTaskScope.ShutdownOnFailure`) per parallelismo controllato.

### 3.4 Backpressure end-to-end

- Bundle invia event al server con `Channel.isWritable()` check; se non writable → coda bounded (default 1000 msg). Coda piena → `BackpressureException` al chiamante.
- Server idem nel `MessageRouter.send(to, msg)`.

### Test PR3

- **Unit** per ognuno dei 9+ componenti.
- **Integration**: bundle reale con saga/projector contro server reale. Verifica:
  - command → event → projector update → query consistency.
  - saga happy path + retry.
  - reconnect mid-saga: nessun event perso, dedupe funziona.
  - osservabile via Micrometer.
- **Chaos test** (manuale, in CI nightly): bundle + server in Docker, `pumba` per network chaos (delay, packet loss, kill). Run 30 min. Verifica:
  - throughput non degrada >50% sotto 10% packet loss.
  - zero correlation leak.
  - bundle si recupera entro 60s dopo ogni evento di chaos.

### Rischi PR3

- **R1**: ConsumerStateStore migration data breaking. *Mitigazione*: tool `evento-cli migrate-state-v1-to-v2` separato, documentato. La major release dichiara breaking.
- **R2**: structured concurrency in Java 25 è stabile? *Verifica al merge*. JEP 505 (4th preview in JDK 24) → previsto stabile in 25. Se non lo è, fallback a `ExecutorService` con virtual threads.
- **R3**: saga richiede atomicità tra state commit e ack del messaggio. *Mitigazione*: pattern outbox per JdbcConsumerStateStore (ack solo dopo commit DB).

---

## Sequenziamento e rilascio

| PR | Branch | Effort | Deliverable | Verifica prima di merge |
|---|---|---|---|---|
| PR1 | `next` | L | Trasporto v2 + Java 25 | TransportIT, EndToEndV2Test, JMH baseline |
| PR2 | `next` | XL | Server bus rewrite | ContractTest, ConcurrencyTest, soak 24h staging |
| PR3 | `next` | XL | Bundle + consumer rewrite | E2E IT, chaos test 30min, soak 48h staging |

Dopo merge PR3:
1. Soak `next` 1 settimana in staging con `evento-demo` + traffic generator (10-100 req/s).
2. Bump version → `2.0.0-rc1` → release candidate Maven.
3. Soak 1-2 settimane con utenti early adopter.
4. Merge `next` → `main`, tag `v2.0.0`, push Maven Central.

---

## Verification end-to-end

Per ogni PR:

1. **Build**: `./gradlew clean build` verde su tutti i moduli, niente warning di deprecation, niente preview-flags accesi.
2. **Test**: `./gradlew test` + `./gradlew integrationTest`.
3. **JMH baseline** (solo PR1): `./gradlew :evento-transport-netty:jmh` — throughput, latency p50/p99 documentati nel changelog.
4. **Coverage**: `./gradlew jacocoTestReport`, soglia minima 70% sui nuovi moduli.
5. **Smoke E2E**: `docker-compose -f docker/v2-stack.yml up` con `evento-demo` configurato. Run 10 minuti, verifica via Micrometer:
   - `evento.server.correlations.pending` non cresce nel tempo (no leak).
   - `evento.server.connections{state=available}` stabile.
   - heap stabile, no OOM.
6. **Chaos** (PR3): `pumba` random network chaos per 30 min, verifica recovery completo.
