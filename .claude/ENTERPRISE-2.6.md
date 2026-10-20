# Evento 2.6+: enterprise readiness contro Axon 5 / Axoniq

Analisi del 2026-10-16, fatta su `main` a `b50c282a` (2.6 merged, **non rilasciata**: i tag si
fermano a `v2.5.2`, `CHANGELOG.md` ha tutto sotto `[Unreleased]`). Verifica sul codice di una
competitor analysis contro Axon Framework 5.4 / Axoniq (ottobre 2026), decisioni da prendere e
roadmap. Complementare a [`ENTERPRISE-PLAN.md`](ENTERPRISE-PLAN.md) (assessment di maggio, item
1-14) e a [`L5-limits-roadmap.md`](../evento-lab-cluster/docs/L5-limits-roadmap.md) (limiti di
cluster G/N).

Come usarlo: ogni punto ha un id (`L*` licenza, `S*` sicurezza, `C*` correttezza, `J*` Java,
`O*` osservabilità, `A*` adozione, `F*` funzionalità, `R*` release). Spunta la casella quando il
punto è chiuso e scrivi accanto commit/PR. Le decisioni `D1..D10` hanno uno stato: **da decidere**,
**decisa** (con data e scelta), **rimandata**.

## Stato al 2026-10-20 (verifica sul codice, sessione 7)

Branch `enterprise/2.6-readiness` (PR #281, aperta, mergeable; CI in corso sull'ultimo push), versione
ancora `2.5.2`, nessun tag `v2.6.0`. Dalla sessione 5: concorrenza ottimistica e group commit (B1 rifatto),
B2 HA multi-AZ, lock equo tra server, APES deprecato con passaggio a CPES, **L5** (tutto pubblico,
Apache-2.0 + Commons Clause dalla 2.6.0, OIDC e audit nel server, `TRADEMARKS.md`, repo enterprise
archiviato), autore dei commit riscritto su tutta la storia (hash nuovi). **D1 superata da L5.**

Minimo indispensabile (sezione in fondo): fatti C1, S1-S3, Q1, B1, J1, O1; L1/L2 fatti ma senza
revisione legale; **mancano R1, R2, F4, O2**.

## Stato al 2026-10-19 (sessione 5)

Decise dall'utente: **D9 (a)** tutto il branch esce come 2.6.0; **D8 (a)** nessuna migrazione da Axon;
**D10 (b)** l'hub resta com'è (l'evoluzione possibile è annotata sotto D10). Risposte: nessun cliente
con licenza commerciale, nessun codice di terzi, **le funzioni enterprise della GUI sotto licenza
commerciale** (vedi L4), obiettivo di scrittura sull'event store: **pari o migliore di Axon Server**
(vedi B1). Pushati `evento-doc` (`docs/enterprise-2.6`) ed `evento-www`
(`licensing/apache-client-libraries`).

Poi, stesso giorno: **L4 (b)** OIDC e audit spostati nel repository privato
`EventoFramework/evento-server-enterprise` (autoconfigurazione sui punti di estensione
`ConsoleSignIn` / `AdminActionListener` del server); la storia del branch è stata riscritta prima del
push perché quel codice non comparisse mai nel repository pubblico (vecchia storia solo in locale,
branch `backup/2.6-before-enterprise-split`). **D2 (a)** con la fence `aggregate_seq` (C1) e il
benchmark contro Axon Server. La tesi non ha scadenza. Tutte le decisioni D1-D10 sono prese.

## Stato al 2026-10-16 (sessione 2)

Decise dall'utente: **D1 (b)**, **D3 (b)**, **D4**, **D5**, **D6** (OpenTelemetry attivabile, zero
overhead se non usato), **D7**. Implementate sul branch `enterprise/2.6-readiness`, non ancora
committate: vedi la roadmap (caselle spuntate) e `CHANGELOG.md [Unreleased]`. Da decidere: D2, D8,
D9, D10. Se queste modifiche escono nella 2.6.0 o in una 2.7.0 è parte di D9.

Trovato durante il lavoro (non era nella prima analisi):
- **`EventoBundle.Builder` non passava il token al client**: un bundle costruito col builder non poteva
  collegarsi a un server con `evento.server.bus.auth-token`. Corretto (`setAuthToken`).
- **I jar di `evento-common` ed `evento-bundle` contenevano `log4j2.properties` / `log4j2.xml`**, che
  sostituivano la configurazione di log dell'applicazione. Rimossi insieme a `log4j-core`.
- **Nessun arresto ordinato del bundle**: c'era solo l'hook JVM sui consumer. Aggiunto `EventoBundle.stop(Duration)`.
- **Le classi payload annidate non sono instradabili**: il nome del payload è la parte dopo l'ultimo
  punto (`Outer$Inner`), mentre gli handler sono registrati col nome semplice. Limite del framework, non
  corretto: le fixture lo riproducono (falliscono come fallirebbe il bundle). Da documentare o correggere.

## Due correzioni di fatto

- **La 2.6 non è rilasciata.** Versioning (#256) e streaming (#255) sono su `main`, non in un tag.
- **Il server non ricostruisce l'aggregato.** Legge snapshot ed eventi (cache LRU,
  `EventStore.java:239-263`) e li manda al bundle, che riapplica gli eventi e restituisce **un solo
  evento** per comando (`CommandBrokerHandler.java:478`). Conta per D8 e D10.

## Le 3 decisioni da prendere per prime

1. **D1 Licenza**: il `LICENSE.txt` attuale è legalmente incoerente (vedi L1). Decide anche il
   confine client/server su cui poggiano D3, D5 e D7.
2. **D9 Release e compatibilità**: rilasciare la 2.6 e fissare cosa entra in 2.x e cosa solo in 3.0.
   Senza questa regola D4 (cambia i default) e D2 (cambia lo schema) non si pianificano.
3. **D2 Consistenza**: G6 è un difetto di correttezza, più ampio di "un nodo perde il lock" (vedi C1).

---

## 1. Verifica dei gap

Esito: ✅ confermato · ❌ smentito · 🟡 parziale. Effort: S (giorni) · M (1-2 settimane) · L (un mese) ·
XL (più mesi o non tecnico).

### Bloccanti per l'adozione enterprise

| Id | Gap | Esito | Evidenza | Effort |
|---|---|---|---|---|
| L1 | Licenza AGPL anche sulle librerie client | ✅ e più grave | `LICENSE.txt:12-20` limita l'AGPL a "personal use, academic research" e impone la licenza commerciale a chi è for-profit in produzione: restrizioni aggiuntive che l'AGPL vieta (§7, §10), quindi chi riceve il software può ignorarle. Per un ufficio legale è una licenza ambigua, peggio dell'AGPL. POM: "Dual License: AGPL-3.0 or Commercial" senza SPDX (`evento-common/build.gradle:59`) | S tecnico, M legale |
| L2 | Nessun CLA | ✅ (nuovo) | `CONTRIBUTING.md:188`: il contributo entra con la stessa licenza con cui esce, non dà diritto di rilicenziare. Il dual licensing si blocca al primo contributo esterno | S |
| L3 | Bus factor: un maintainer, nessun adopter con nome | ✅ | `git shortlog`: 838 commit di un solo autore + Dependabot; `MAINTAINERS.md` | XL, non tecnico |
| C1 | G6: nessuna fence `aggregate_seq` | ✅ e più ampio | Finestra tra `verifyHeld` e INSERT (`CommandBrokerHandler.java:480-487`, lo dice il commento). In più `DomainCommand.getLockId()` è sovrascrivibile (`DomainCommand.java:33`): se torna `null` non c'è né lock né verifica (`CommandBrokerHandler.java:399-414`), e due comandi concorrenti sullo stesso aggregato biforcano la storia **senza alcun guasto** | M |
| S1 | Il bus accetta qualunque bundle | ✅ | `BusConfiguration.java:150-154`: `TokenValidator.acceptAll()` con un warning | S |
| S2 | TLS del bus non configurabile | ✅ server, ❌ bundle | Il server costruisce `NettyTransportConfig` senza `SslContext` (`BusConfiguration.java:123-135`). Il bundle può da codice: `EventoBundle.Builder` è `@Setter` su `transportConfig` + `NettyTransportConfig.withSslContext` (`:77`) | S-M |
| S3 | Deserializzazione aperta | 🟡 | Allowlist esistente ma opt-in, aperta di default con un warning una tantum (`PayloadTypeAllowlist.java:43-78`) | S invertire, M renderla usabile |
| S4 | GUI con utente fisso, niente RBAC/OIDC | 🟡 | Utente unico `evento/secret` (`application.properties:49-51`). I ruoli esistono: `@Secured("ROLE_WEB")` sui controller, `ROLE_ADMIN` in `ConsumerController.java:75`. Mancano utenti multipli e identity provider | M |
| S5 | CORS `*` di default | ✅ (nuovo) | `WebConfig.java:46` | S |
| J1 | Java 25 obbligatorio | ❌ come vincolo tecnico | Toolchain fissata in un punto (`build.gradle:28`). Prova con `--release 21` (init script esterno): transport-api, transport-netty, common, consumer-state-store-jdbc compilano **senza modifiche**; bundle 1 errore (`ProcessedRequestCache.java:63`), server 3 (`ForwardingDedupCache.java:94,96`, `ExpiringLruCache.java:41`), tutti la classe annidata `Entry` che collide con `Map.Entry` (rinomina). ScopedValue/StructuredTaskScope **non usati** (solo Javadoc, `EngineSupervisor.java:26`). Costo vero a runtime: su 21 i virtual thread si bloccano sul carrier dentro `synchronized` (risolto in JDK 24, JEP 491); 56 `synchronized` nelle librerie client | S compilare, M validare |

### Gap funzionali

| Id | Gap | Esito | Evidenza | Effort |
|---|---|---|---|---|
| O1 | Tracing OpenTelemetry | ✅ | `TracingAgent.java:83` (`transaction.run()`), `:93` (`correlate` no-op). Nessun `traceparent`/`opentelemetry` nel repo | M + M |
| O2 | Lag dei consumer | ✅ | Nessuna metrica di lag tra quelle registrate | S-M |
| O3 | Timer su query e service command | 🟡 | Solo `evento.server.command.phase` (`CommandPhaseMetrics.java:83`); il bundle ha `BundleLatencies` senza Micrometer | S-M |
| F1 | Scarto delle richieste già scadute | ❌ in larga parte | Il broker scarta in 4 punti (`BusLifecycle.java:704-708`, `evento.server.requests.expired{stage}` in `BusMetricsBinder.java:78`, CHANGELOG N3). Mancano: scarto lato bundle prima dell'handler, admission control, backpressure verso il chiamante | S-M |
| F2 | Test fixture | ✅ | `EmbeddedBroker` in `evento-lab/src/test`, dipende da classi interne del server (`BusLifecycle`), non pubblicato | M |
| F3 | Starter Spring Boot | ✅ | Nessuna autoconfigurazione | M |
| F4 | Flyway per l'event store | ✅ | `evento-server/build.gradle:59` esclude Flyway; `schema.sql` + `AdvisoryLockedSchemaInitializer` + `db/upgrade`. Lo store dei consumer usa Flyway | M |
| F5 | Deadline / scheduling | ✅ | Nessuna implementazione | L |
| F6 | Multi-tenancy | ✅ | Javadoc di `DomainCommand.java:19`: il tenant è un campo ordinario del comando | L-XL |
| F7 | Lock globale di append (N5) | ✅ | `EventStore.java:70-71`: `pg_advisory_xact_lock(hashtext('es-lock'))` | XL per toglierlo |
| F8 | Migrazione da Axon 4 | ✅ | Nulla | M guida, L importatore |
| F9 | Più eventi per comando | ✅ (nuovo) | Il bundle restituisce un solo `DomainEventMessage` (`CommandBrokerHandler.java:478`); Axon permette più `apply()` | M-L |

### Già coperti nella 2.6 (non rilasciati)

| Funzione | Esito | Evidenza | Limiti |
|---|---|---|---|
| Upcasting `@Revision` / `Upcaster` | ✅ | `common/versioning/Revision`, `Upcaster` (#256) | niente split, downcast, upcast dello stato delle saghe |
| Query in streaming e paginate | ✅ | `QueryStream`, `Page` (#255) | stream non sopravvivono a un cambio di sessione |
| Read-your-writes | ✅ | `ConsistencyToken` | con handler async `ON_START` aspetta la quiescenza |

### Gap non elencati nella competitor analysis

| Id | Gap | Evidenza | Effort |
|---|---|---|---|
| A1 | Il bundle impone dipendenze all'applicazione | `log4j-core`/`log4j-api` come `implementation` (`evento-bundle/build.gradle:41-42`), in conflitto con Logback/SLF4J di Spring Boot; `reflections 0.10.2` (abbandonata) sovrapposta a classgraph e asm; okhttp; `hibernate-validator` esposto come `api` | S-M |
| A2 | Server non pubblicato come libreria | Solo jar boot e immagine; blocca un broker embedded per i test (F2) | S-M |
| F10 | Diritto alla cancellazione (GDPR) | Nessun crypto-shredding nell'event store | M-L |
| F11 | Chiavi di idempotenza ed esiti senza scadenza | L5, G5: indice e tabella dei claim crescono soltanto | S-M |
| Q1 | Nessun test REST e autenticazione | `ENTERPRISE-PLAN.md` item 12 | M |
| Q2 | Shutdown del trasporto senza deadline | `ENTERPRISE-PLAN.md` item 10 | S |
| Q3 | Limiti di cluster aperti | L5: G21 datasource di lettura, N4 affinità della lane `ALL`, N15 `lock_timeout` legato alla deadline, gate di staleness dipendente dagli orologi (S15), G17 dashboard locale | M ciascuno |
| B1 | Manca il tetto misurato del modello a celle | Ci sono le latenze (8 in volo = 19 ms in coda su `es-lock`, `command-latency.md`), non gli append/s sostenibili sotto `es-lock` | S-M |

---

## 2. Decision record

### D1 Licenza — decisa 2026-10-16: (b); superata il 2026-10-20 da L5

Dalla 2.6.0: client Apache-2.0, il resto Apache-2.0 + Commons Clause (nessuna edizione chiusa; vendere
Evento stesso richiede autorizzazione). È l'opzione (c) source-available, senza conversione a scadenza.
Il testo sotto resta come storia della decisione.

- **Opzioni:**
  - (a) tutto AGPL + commerciale, come oggi ma con un testo corretto;
  - (b) librerie client Apache-2.0, server AGPL + commerciale;
  - (c) client Apache-2.0, server source-available (BSL/FSL che diventa Apache dopo N anni).
- **Trade-off:** (a) massima leva commerciale, adozione bloccata (un'app che usa `evento-bundle`
  diventa AGPL). (b) modello MongoDB pre-SSPL / Grafana: chi linka la libreria non ha obblighi; il
  server si deploya, non si linka, quindi l'AGPL lì è accettabile. (c) protegge di più, ma non è OSI e
  alcuni uffici legali la trattano come proprietaria.
- **Fattibilità:** unico autore, quindi rilicenziare oggi non richiede permessi. Dopo il primo
  contributo esterno senza CLA non sarà più possibile.
- **Business:** a pagamento ciò che serve in produzione: licenza commerciale del server (niente
  obblighi AGPL, garanzie, indennizzo), supporto con SLA e LTS, funzioni enterprise della GUI
  (OIDC/SSO, audit, RBAC fine). Catalogo e grafo restano gratuiti: sono l'esca contro Axoniq Platform.
- **Reversibilità:** il passaggio ad Apache è irreversibile per le versioni pubblicate.
- **Sblocca:** D3, D5, D7.
- **Raccomandazione:** (b), più `LICENSE.txt` riscritto come AGPL-3.0 testuale con offerta
  commerciale separata, SPDX nei POM, CLA o DCO con cessione dei diritti.
- **Domande aperte:**
  - [x] Ci sono clienti con licenza commerciale e il contratto cita i moduli? No (2026-10-19).
  - [x] È mai entrato codice di terzi (non Dependabot)? No, unico sviluppatore (2026-10-19).
  - [x] La GUI resta tutta AGPL o ha una parte commerciale? Le funzioni enterprise (OIDC/SSO, audit,
        RBAC fine) sotto licenza commerciale (2026-10-19). Conseguenza: L4.

### D2 Consistenza e scala — decisa 2026-10-19: (a)

`es-lock` resta; C1 (numero di sequenza per aggregato con vincolo unico, che valida anche il lock) si
fa; B1 misura il tetto per cella contro Axon Server. Se il benchmark resta sotto, si apre (b). Analisi
originale:

- **Fatto chiave:** la fence `aggregate_seq` **non** permette di togliere `es-lock`. `es-lock` fa
  coincidere ordine di sequenza e ordine di commit (`EventStore.java:48-61`), la garanzia su cui i
  consumer leggono senza saltare eventi. La fence garantisce la consistenza **per aggregato**: toglie
  il lock `RESOURCE:`, un round trip e la dipendenza da `verifyHeld`, non il lock globale.
- **Opzioni:**
  - (a) G6 + CPES invariata + modello a celle formalizzato con limiti misurati;
  - (b) G6 + lettura dei consumer sicura rispetto ai gap con watermark sui transaction id
    (`pg_current_xact_id` / `pg_snapshot_xmin`, approccio "high water mark" di Marten) e append
    senza lock globale;
  - (c) ordine solo per partizione o contesto.
- **Impatto sui consumer:** (b) mantiene l'ordine totale, con visibilità ritardata dalla transazione
  più lunga. (c) rompe saghe e projector che dipendono da eventi di aggregati diversi: 3.0 e modello
  mentale nuovo.
- **Codice e compatibilità (G6):** colonna nullable + indice unico parziale creato `CONCURRENTLY`
  (come G5); righe vecchie fuori dall'indice; additivo, quindi 2.x. Prerequisito: F4 (Flyway).
- **Raccomandazione:** (a) subito, con la fence obbligatoria anche quando `getLockId()` è `null`;
  benchmark del tetto per cella (B1); (b) come spike di ricerca per la 3.0 (capitolo di tesi);
  (c) mai come default.
- **Domanda aperta:**
  - [x] Quanti append/s per cella deve reggere il cliente tipo? Pari o meglio di Axon Server sullo
        stesso hardware (2026-10-19): B1 diventa un benchmark comparativo.

### D3 Baseline Java — decisa 2026-10-16: (b)

- **Uso reale di Java 25:** poche API (vedi J1); la dipendenza vera è il comportamento dei virtual
  thread a runtime.
- **Opzioni:** (a) restare a 25; (b) client `--release 21`, server 25; (c) tutto 21.
- **Trade-off:** il server si consegna come immagine, la JVM la scegli tu: tenerlo a 25 è gratis. Il
  client gira nella JVM del cliente: lì serve il 21.
- **Costo di (b):** rinomina delle classi `Entry`; matrice CI 21/25 con le suite lab; audit dei
  `synchronized` sui percorsi caldi (`ReentrantLock`) o fallback a platform thread su JDK < 24;
  `JdkVersionGuard` esteso al 21.
- **Raccomandazione:** (b) nella 2.7. Stesso confine di D1: client Apache-2.0 su 21+, server AGPL su 25.

### D4 Sicurezza di default — decisa 2026-10-16: profilo dev/prod in 2.x, prod di default in 3.0

- **Precedente:** Elasticsearch 8 (sicurezza attiva di default, credenziali generate al primo avvio).
- **Transizione:**
  - 2.7: `evento.server.security.profile=dev|prod` *(proposta)*. In `prod` il server **non parte**
    senza token del bus, TLS, allowlist strict e utente GUI non di default. In `dev` (oggi implicito)
    warning nei log e banner nella GUI.
  - 3.0: default `prod`.
- **Ordine:** token + TLS/mTLS del bus via properties (PEM/keystore) → allowlist strict con i package
  dell'applicazione rilevati dal component scan → CORS ristretto → OIDC nella GUI (claim mappati su
  `WEB`/`ADMIN`, che esistono già) → audit log delle azioni admin.
- **Reversibilità:** alta, è configurazione.

### D5 Integrazione Spring — decisa 2026-10-16: starter separato, core agnostico

- **Raccomandazione:** entrambi. `evento-spring-boot-starter` separato con autoconfigurazione:
  costruisce `EventoBundle.Builder` dalle properties, passa i bean come injector, lifecycle con
  `SmartLifecycle` e readiness, metriche sul `MeterRegistry`. Il core resta agnostico.
- **Prerequisito:** A1 (via `log4j-core` da `implementation`).

### D6 Osservabilità — decisa 2026-10-16: propagazione nel core, OTel in modulo opzionale

- **Raccomandazione:** propagazione nel core, OTel in un modulo. Campo `traceContext` (W3C
  `traceparent`/`tracestate`) nei metadati di `Request` ed `Event` con capability `trace:1`;
  `evento-opentelemetry` implementa lo SPI `TracingAgent`; lato server Micrometer Observation.
- **Perché:** OTel diretto nel core impone versioni all'applicazione; il solo SPI senza campo sul
  filo non propaga nulla.

### D7 Test fixture — decisa 2026-10-16: `evento-test` con fixture pure

- **API:** gli handler di comando sono puri (stato + comando → evento, `CommandBrokerHandler.java:98`):
  `AggregateFixture.of(X.class).given(events...).when(cmd).expectEvent(e) / expectException(...)`;
  stesso schema per saghe (`whenEvent` → `expectCommands`) e projector, senza broker.
- **Pubblicazione:** `evento-test` Apache-2.0 con le sole fixture pure; `evento-test-broker` a parte
  (incapsula `BusLifecycle` + `InMemoryTransport`, dipende dal server: va deciso con D1).

### D8 Migrazione da Axon 4 — decisa 2026-10-19: (a) nessuna

Evento non punta a chi migra da Axon: niente guida, importatore o compat layer. F8 esce dalla roadmap.
F9 (più eventi per comando) resta, per merito suo. Analisi originale:

- **Somiglianze:** `@Aggregate`, `@EventSourcingHandler`, `@CommandHandler`, `@QueryHandler`,
  `@Saga`, `@EventHandler`, gateway: vocabolario comune all'80%.
- **Differenze:** aggregato senza stato con `State` come parametro vs aggregato con stato e
  `apply()`; **un evento per comando** (F9); payload che estendono classi base (`DomainCommand`) vs
  POJO con `@TargetAggregateIdentifier`; deadline assenti (F5).
- **Raccomandazione:** niente compat layer (la semantica è diversa); OpenRewrite rimandato. Sì a guida
  di mapping + **importatore della `domain_event_entry`** di Axon (con upcaster dei payload
  XStream/Jackson) + F9 in roadmap.

### D9 Release e supporto — decisa 2026-10-19: (a) tutto nella 2.6.0

Il branch `enterprise/2.6-readiness` esce come 2.6.0 insieme a quanto già su `main`. Regole confermate
come sotto (semver sul filo, matrice N/N-1, prima i server; LTS solo commerciale).

- **Subito:** rilasciare 2.6.0 (#255, #256, #257) prima dei cambi di default.
- **Semver sul filo:** in 2.x solo campi e capability nuove (`stream:1`, `revision:1` provano il
  meccanismo); il byte `PROTOCOL_VERSION` (`HandshakeProtocol.java:14`) cambia solo in un major.
  Matrice documentata: server N con bundle N e N-1; upgrade prima i server, poi i bundle (come G5).
- **LTS:** solo nel contratto commerciale (ultima minor di ogni major, 18 mesi); nella community,
  patch solo per la minor corrente. Dopo L5 il contratto commerciale è un'autorizzazione a vendere, non
  una licenza d'uso: l'LTS a pagamento va riformulato come servizio di supporto (da decidere).

### D10 Architettura hub-and-spoke — decisa 2026-10-19: (b) resta com'è

Nessun lavoro pianificato. **Nota per il futuro:** se servono meno round trip per comando, l'evoluzione
incrementale qui sotto (cache degli aggregati nel bundle + routing affine, dopo C1) è additiva e non
richiede di cambiare architettura. Analisi originale:

- **Valutazione:** non è un limite strutturale per team medi. I costi misurati vengono da `es-lock` e
  dai round trip WAN (7-9 per comando), non dall'hub.
- **Evoluzione incrementale:**
  1. G6 dà a ogni evento la sua `aggregate_seq`;
  2. cache degli aggregati nel bundle: dichiara "ho X fino a seq N", il server manda solo il resto
     (capability nuova, additiva);
  3. routing affine per `aggregateId` (hash consistente nel pick) per alzare gli hit;
  4. il routing diretto stile `DirectStream` resta per le query: per i comandi il broker è il punto in
     cui si serializza l'append.
- **Riscrittura:** solo per scalare oltre una singola istanza PostgreSQL per cella (torna a D2 c).

### Domande aperte trasversali

- [ ] Quanto fattura oggi la licenza commerciale e quale ricavo ti aspetti tra licenza, supporto e GUI?
- [x] La tesi ha una scadenza che deve guidare l'ordine (per esempio lo spike D2 b)? No (2026-10-19).

---

## 3. Roadmap

Le dipendenze sono indicate con `←`.

### Fase 1 — fiducia (2.6.0 → 2.7)

- [ ] R1 Rilascio 2.6.0 (D9)
- [ ] R2 Politica di compatibilità scritta: semver, matrice server/bundle, ordine di upgrade (D9)
- [x] L1 `LICENSE.txt` riscritto per modulo (client Apache-2.0, resto AGPL-3.0 o commerciale), testi
      canonici in `licenses/`, `LICENSE.txt` per modulo, `NOTICE.txt`, SPDX nei POM, LICENSE/NOTICE nei
      jar, ambito di `LICENSE-COMMERCIAL.txt` ristretto ai componenti AGPL, README e CONTRIBUTING (D1).
      Sito (`evento-www`, branch `licensing/apache-client-libraries`) ed evento-doc (branch
      `docs/enterprise-2.6`, pagina Licensing) pushati, non pubblicati. Superato da L5 per il server
- [x] L2 `CLA.md` (bozza su modello ICLA Apache + diritto di rilicenziare), richiesto da CONTRIBUTING (D1).
      **Resta**: revisione di un legale prima del primo contributo esterno
- [ ] F4 Flyway sull'event store, con baseline dei database esistenti
- [x] C1 Fence `aggregate_seq`, obbligatoria anche senza lock (2026-10-19). Colonna nullable e
      indice unico parziale `(aggregate_id, aggregate_seq)` online come G5 (F4 non serviva), script
      `db/upgrade/es-events-aggregate-seq.sql`; versione letta nella stessa query della storia,
      append alla versione successiva. Test: `AggregateFencePostgresIT` (CPES e APES) e il caso senza lock
      su due server (8 comandi: prima 8 eventi salvati, ora 1). Poi (2026-10-20): retry automatico lato
      server e comandi senza lock per default (`evento.server.commands.optimistic`), fino a 5 tentativi,
      `AggregateConflictException` solo dopo; group commit (`AppendBatcher`)
- [x] S1 + S2 `evento.server.security.profile=dev|prod` (`SecurityProfileGuard`, 6 controlli, in `prod` il
      server non parte); TLS/mTLS del bus da properties (`evento.server.bus.tls.*`, `BusTlsProperties`);
      `EventoBundle.Builder.setAuthToken`. Test: `SecurityProfileGuardTest`, `BusTlsPropertiesIT` (mTLS su TCP) (D4)
- [x] S3 Allowlist "live" (`PayloadTypeAllowlist.restrictTo`), bundle: `setRestrictPayloadTypes` (package
      base) + `setAllowedPayloadPackages`; aperta di default in 2.x (D4)
- [x] S5 CORS da `EVENTO_SERVER_WEB_CORS_ALLOWED_ORIGINS`, `*` vietato in `prod`; `*` resta il default in `dev` (D4)
- [x] J1 Librerie client `--release 21` (lista `clientLibraries` nel `build.gradle` di root), una rinomina
      (`ProcessedRequestCache`), lock di `JdbcConsumerStateStore` da monitor a `ReentrantLock`; job CI
      `client-libraries-jdk21`; verificato in locale su corretto-21.0.12.1: 402 test, 0 falliti
      (i 74 JDBC saltati senza Docker). Audit `synchronized`: solo quel lock bloccava su I/O (D3)
- [x] A1 Via `log4j-core` (e le config log4j nei jar) e `okhttp`; librerie solo su SLF4J; `reflections`
      sostituita da ClassGraph (`ComponentScan`, stessa semantica), `javassist` dichiarata.
      `hibernate-validator` rimosso: nessun codice usava la bean validation
- [ ] O2 Metrica di lag dei consumer
- [x] B1 Benchmark del tetto di append/s per cella (`benchmarks/axon-comparison`, risultati in
      `results/REPORT.md`). Dopo concorrenza ottimistica + group commit + executor 32/CPU (2026-10-20):
      una cella su un i7-6700K regge ~3900 cmd/s in CPES e in APES (create, 128 in volo, p99 ~60 ms;
      prima 430 e 1300), ~2300-2500 su spread, ~50 cmd/s su un aggregato conteso, senza rifiuti e senza
      collasso. CPES diventa il default anche nel codice. Axon
      Framework 5.4 open source (JPA, pool di default) collassa da 32 in volo e su spread/hot è 5-15× sotto
      (nessuno snapshot: ricarica la storia). Il confronto con **Axon Server** è stato misurato ma i risultati
      restano fuori dal repository (ToS Axoniq, `results-private/`, git-ignored). Ha trovato due bug, corretti:
      deadlock del trasporto sotto carico e id Snowflake che fallivano quando l'orologio torna indietro.
      Da decidere (utente): se aprire D2 (b) alla luce del confronto privato
- [x] B2 Benchmark HA su due AZ (`results/REPORT-HA.md`, 2026-10-20): primario + standby sincrono,
      un server per AZ, RTT 0/2/10 ms tra le AZ, aggregati condivisi tra i server. Con la replica
      sincrona CPES tiene `es-lock` anche durante l'attesa della replica: APES rende 2-3× a pochi
      comandi in volo o con AZ lontane (10 ms, create: 286 contro 91 cmd/s a 8 in volo, 1986 contro
      1181 a 128); CPES lo eguaglia a 0 ms con molto carico e su spread a 128 in volo ha p99 migliore.
      Quindi APES è **deprecato** (dove è sicuro non è più veloce, dove è più veloce non è sicuro;
      un database APES passa a CPES riavviando i server in CPES). Con più server non è sicuro per i consumer
      (finestra di lettura di 69 ms contro il tempo tra id e commit, più orologi, G20; B2 misura solo
      i comandi): CPES resta il default e la modalità per il cluster. Da valutare: dare a CPES il
      commit parallelo senza perdere l'ordine. Ha trovato la starvation del lock tra server su un
      aggregato conteso (corretta: chi aspetta si mette in coda in PostgreSQL)
- [x] L5 (2026-10-20, decisione dell'utente: "fai tutto open source, l'unico vincolo è che nessuno può
      rivendere Evento senza autorizzazione") Tutto pubblico: OIDC e audit tornano in `evento-server`
      (`com.evento.server.web.oidc` / `.audit`), server, GUI e resto sotto **Apache-2.0 + Commons
      Clause** (niente SaaS di Evento, rivendita, consulenze/supporto/formazione su Evento senza
      autorizzazione scritta), client ancora Apache-2.0, `TRADEMARKS.md` per i nomi Evento e RECQ
      (le idee RECQ non sono proteggibili), `LICENSE-COMMERCIAL.txt` diventa accordo di
      autorizzazione, CLA aggiornato. Non più OSI per il server (source-available): il badge OpenSSF
      Best Practices (`floss_license`) non è più raggiungibile per il server. Fatti anche: evento-doc e
      sito pushati (sito non pubblicato), repo privato archiviato, termini validi dalla 2.6.0, autore dei
      commit riscritto. **Resta**: revisione del legale (licenza, marchi, CLA), registrazione dei marchi
- [x] L4 (superata da L5) Funzioni enterprise sotto licenza commerciale: OIDC/SSO e audit nel repository privato
      `evento-server-enterprise` (Spring Boot autoconfigurazione, build inclusa `../evento`, boot jar
      distribuibile). Nel server pubblico solo i punti di estensione `ConsoleSignIn` e
      `AdminActionListener`; con `evento.server.web.auth=oidc` senza il modulo il server non parte.
      Verificato col jar avviato su PostgreSQL. `LICENSE.txt` e `LICENSE-COMMERCIAL.txt` lo citano
      (da far rivedere al legale con L2). Modulo archiviato il 2026-10-20: nulla resta
- [x] Q1 Test di autenticazione web: `WebSecurityTest` (MockMvc, Basic e OIDC), `OidcRolesTest`;
      `ConsoleApiTest`: tutti i controller della console con la catena di sicurezza vera e i servizi
      mock (ruoli per endpoint, risposte, audit). Ha trovato un 500 al posto di 400 (pagine > 1000)

### Fase 2 — adozione (2.8-2.9)

- [x] F3 `evento-spring-boot-starter`: `evento.bundle.*` (server, token, TLS/mTLS, zona, allowlist, stato
      consumer JDBC/memoria, tracing), `SmartLifecycle` con stop ordinato, bean `CommandGateway`/`QueryGateway`,
      `EventoBundleCustomizer`. Compilato sulle API comuni a Boot 3 e 4, testato su 4.1.1 e 3.5.16
      (`boot3Test`); `EventoStarterIT` su TCP reale con token e trace (D5).
      Poi: metriche Micrometer (`evento.bundle.connected`, `.request{payload}`, `.handler{type}`), readiness
      legata alla connessione (opt-in), `EventoBundleStartedEvent`: soluzioni uguali per Boot 3 e 4 al posto
      di un `HealthIndicator`, che ha package diversi nelle due versioni
- [x] F2 `evento-test`: `AggregateFixture`, `SagaFixture`, `ProjectorFixture`, `ServiceFixture`,
      `RecordingCommandGateway`, `StubQueryGateway` (D7).
      **Resta**: `evento-test-broker` (dipende dalla pubblicazione del server, A2, ancora non fatta;
      il vincolo di licenza di D1 non c'è più)
- [x] O1 `evento-opentelemetry` (`OpenTelemetryTracingAgent`, OTel API 1.66.0): span per handler, W3C nei
      metadati, `linkingConsumers()`; `CorrelatingGateways` per i gateway usati dal codice applicativo, solo
      se l'agente non è quello no-op. Nessuna modifica al filo (D6).
      Poi: span del server per ogni comando (`CommandTracing`, fasi come attributi, agente Java o bean
      `OpenTelemetry`), `TracingEventFlowIT`: richiesta → server → aggregato → projector in una trace.
      Poi: span `CLIENT` per ogni comando e query inviati (`send <Command>` / `query <Query>`, fino alla
      risposta), nuovo hook `TracingAgent.traceSend`; l'IT verifica richiesta → send → server → aggregato
      → projector
- [ ] O3 Timer su query e service command
- [x] S4 (in Evento Enterprise dal 2026-10-19, di nuovo nel server dal 2026-10-20, vedi L5) OIDC per la console: `evento.server.web.auth=oidc`, authorization code con PKCE gestito dal
      server (sessione HttpOnly, CSRF da cookie, nessun token nel browser), gruppi → `WEB`/`ADMIN`
      (`OidcRoles`, claim annidati), Basic resta per i client macchina; GUI: `/api/auth/mode`, bottone
      "Sign in with your organisation". Testato con MockMvc, **non** contro un identity provider reale (D4).
      Poi: audit trail (`AuditConfiguration`, audit di Spring Boot): `ADMIN_ACTION` con esito reale,
      login falliti, login/logout OIDC, rifiuti a utenti autenticati; una riga `key=value` sul logger
      `evento.audit` (il record durevole, da mandare al SIEM) e `/actuator/auditevents` solo ADMIN.
      La rimozione di un bundle richiede ADMIN; la GUI non fa più logout su 403.
      **Poi**: tabella di audit su database quando c'è Flyway (F4)
- [-] F8 Guida di mapping Axon 4 + importatore `domain_event_entry`: fuori roadmap (D8 a)
- [ ] F9 Più eventi per comando
- [ ] F11 Retention di chiavi di idempotenza ed esiti
- [ ] Payload annidati non instradabili (`Message.getPayloadName` prende l'ultima parte dopo il punto,
      `Outer$Inner`): da documentare o correggere (trovato in sessione 2, ancora aperto)
- [ ] F1 Scarto lato bundle delle richieste scadute + admission control
- [ ] L3 Prime case study e adopter con nome

### Fase 3 — competizione (3.0)

- [ ] Default `prod` (D4)
- [ ] F5 Deadline e scheduling
- [-] Cache degli aggregati lato bundle + routing affine ← C1: non pianificato (D10 b), resta come
      evoluzione possibile
- [ ] Spike: lettura con watermark sui transaction id, append senza `es-lock` ← C1 (D2 b)
- [ ] F10 Crypto-shredding
- [ ] F6 Multi-tenancy, solo se un cliente la paga

### Minimo indispensabile per dire "production ready per enterprise"

Licenza pulita (L1, L2) · G6 chiuso (C1) · default sicuri attivabili e coperti da test (S1-S3, Q1) ·
2.6 rilasciata con politica di compatibilità (R1, R2) · tetto per cella pubblicato (B1) · client su
Java 21 (J1) · Flyway (F4) · lag (O2) e tracing propagato (O1).

### Cosa non fare

- Inseguire DCB.
- Cluster multi-region con ordine globale.
- Compat layer per Axon.
- Store diversi da PostgreSQL.
- LTS gratuito.
- Multi-tenancy "per completezza".

Su questi terreni Axon ha 187 contributor. Il vantaggio di Evento è la GUI (catalogo, flussi, grafo,
modello di performance) e "solo PostgreSQL, e lo capisci tutto".
