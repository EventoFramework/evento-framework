# Percorso delle query: quanto costa all'evento-server (benchmark 3.0)

Misura di cosa spende un evento-server per portare la risposta di una query, per dimensione
della risposta e per modalità: risposta classica come in 2.5.2, risposta classica in 3.0
(query "straight"), stream attraverso il broker, stream diretto tra bundle. Più due misure
collaterali: latenza della query servita dal bundle stesso e compressione.

Benchmark: `evento-server/src/test/java/com/evento/server/bench/QueryPathBenchmarkIT.java`
(spento salvo `EVENTO_RUN_BENCH=true`), broker in `BenchBroker.java`. Riproduzione:

```bash
EVENTO_RUN_BENCH=true ./gradlew :evento-server:test --tests '*QueryPathBenchmarkIT' --rerun
# report: evento-server/build/bench/query-path.md
```

## Impianto

- **Il broker gira da solo in una JVM separata** (G1, heap 1 GiB salvo lo scenario C): le
  misure sono solo sue, non dei bundle. I bundle (chi risponde, chi chiede) sono nella JVM
  del test; trasporto TCP su loopback. Host: Windows, 8 CPU, JDK 25.0.4.
- Le richieste nel broker girano su un pool di thread di piattaforma, come nel server Spring
  (`BusBusinessExecutor`), così le loro allocazioni sono contate.
- Metriche del broker: **picco heap** = massimo campionato ogni 2 ms sopra la baseline post-GC;
  **alloc** = byte allocati dai thread del broker; **CPU** = tempo CPU del processo;
  **trattenuto** = heap ancora occupato dopo una GC completa. Mediana di 3 esecuzioni; due run
  completi a distanza hanno dato numeri entro pochi punti percentuali.
- Dati incomprimibili (casuali) negli scenari A–C, per isolare il trasporto dalla compressione.
- **"2.5.2"** riproduce il comportamento della cache di 2.5.2 (ogni risposta trattenuta per il
  replay, limite solo in numero di voci) sul trasporto 3.0. Non include le copie in più che
  2.5.2 faceva nel riassemblaggio dei chunk: **sottostima** il costo reale di 2.5.2.

## A. Risorse del broker per risposta

| Risposta | Modalità | Tempo client ms | MiB/s | Picco heap broker MiB | Alloc broker MiB | CPU broker ms | Trattenuto MiB |
|---|---|---|---|---|---|---|---|
| 1 MiB | classica, cache 2.5.2 | 17 | 58 | 9 | 4,0 | 16 | 3 |
| 8 MiB | classica, cache 2.5.2 | 97 | 82 | 37 | 32,1 | 47 | 9 |
| 32 MiB | classica, cache 2.5.2 | 345 | 93 | 137 | 128,1 | 172 | 33 |
| 64 MiB | classica, cache 2.5.2 | 634 | 101 | 271 | 256,2 | 266 | **65** |
| 1 MiB | classica, 3.0 | 13 | 78 | 8 | 4,1 | 16 | 0 |
| 8 MiB | classica, 3.0 | 69 | 116 | 37 | 32,0 | 47 | 0 |
| 32 MiB | classica, 3.0 | 275 | 116 | 137 | 128,1 | 188 | 0 |
| 64 MiB | classica, 3.0 | 602 | 106 | 272 | 256,2 | 281 | **1** |
| 1 MiB | stream via broker | 10 | 101 | 5 | 4,1 | 31 | 0 |
| 8 MiB | stream via broker | 41 | 195 | 37 | 32,7 | 109 | 1 |
| 32 MiB | stream via broker | 127 | 252 | 138 | 130,8 | 297 | 1 |
| 64 MiB | stream via broker | **243** | **263** | 274 | 261,7 | 547 | 1 |
| 1 MiB | stream diretto | 9 | 106 | 2 | 0 | 0 | 0 |
| 8 MiB | stream diretto | 29 | 273 | 2 | 0 | 0 | 0 |
| 32 MiB | stream diretto | 67 | 476 | 2 | 0 | 0 | 0 |
| 64 MiB | stream diretto | **153** | **417** | **2** | **0** | **0** | 0 |

Lettura:

- **Trattenuto**: in 2.5.2 ogni risposta resta nel broker (65 MiB dopo una sola query da
  64 MiB, per 5 minuti); in 3.0 non resta nulla.
- **Stream via broker**: tempo 2,5 volte minore della risposta classica a 64 MiB (243 contro
  602 ms). Il broker inoltra un frame appena arriva invece di aspettare il messaggio intero, e
  chi chiede consuma mentre il resto viaggia.
- **Stream diretto**: il broker non spende nulla (2 MiB = rumore del campionatore, 0 byte
  allocati, 0 CPU) e il throughput è 4 volte quello della risposta classica.
- **Attenzione al picco heap dello stream via broker**: è uguale a quello della risposta
  classica, ma *non* perché lo stream sia in memoria. Con 1 GiB di heap G1 non raccoglie quasi
  mai, e il picco misura spazzatura non ancora raccolta (≈ i byte allocati). Il live set si
  vede nello scenario C, con heap piccoli.
- **Costo**: lo stream via broker usa più CPU del broker (547 contro 281 ms a 64 MiB) per il
  lavoro per-frame (decodifica di ogni frame da 256 KiB, crediti). Il broker alloca ~4 byte per
  byte inoltrato in tutte le modalità che passano da lui: decodifica il messaggio intero (con
  la copia del payload) anche se lo inoltra grezzo. È il prossimo punto da ottimizzare (vedi
  limiti).

## B. Memoria trattenuta dopo 25 query da 4 MiB

| Broker | Cache dedup MiB | Heap trattenuto dopo GC MiB |
|---|---|---|
| cache 2.5.2 (risposte delle query trattenute 5 min) | 100,0 | 125,4 |
| 3.0 (query straight) | **0,0** | **0,1** |

In 2.5.2 la memoria trattenuta cresce linearmente con il traffico di query fino al limite di
50 000 voci: con risposte da 4 MiB sarebbero fino a ~195 GiB teorici, cioè un OOM ben prima.

## C. La risposta passa da un broker con heap piccolo?

| Heap broker | Risposta | Modalità | Esito | Picco heap MiB |
|---|---|---|---|---|
| 256 MiB | 32 MiB | classica, 3.0 | consegnata | 122 |
| 256 MiB | 64 MiB | classica, 3.0 | consegnata | 211 |
| 256 MiB | 96 MiB | classica, 3.0 | **broker morto, OutOfMemoryError** | – |
| 256 MiB | 128 MiB | classica, 3.0 | **broker morto, OutOfMemoryError** | – |
| 256 MiB | **1024 MiB** | stream via broker | **consegnata** | 151 |
| 64 MiB | 8 MiB | classica, 3.0 | consegnata | 37 |
| 64 MiB | 16 MiB | classica, 3.0 | **broker morto, OutOfMemoryError** | – |
| 64 MiB | 32 MiB | classica, 3.0 | **broker morto, OutOfMemoryError** | – |
| 64 MiB | **1024 MiB** | stream via broker | **consegnata** | 36 |

È la prova del limite di memoria. Una risposta classica deve stare intera nel broker, con
circa tre copie contemporanee: con 256 MiB di heap il massimo è tra 64 e 96 MiB, con 64 MiB tra
8 e 16 MiB. Oltre, **il broker muore e con lui tutte le richieste di tutti i bundle**. Uno
stream da 1 GiB passa da un broker con 64 MiB di heap: il broker tiene solo la finestra di
credito (16 frame × 256 KiB = 4 MiB per stream), indipendentemente dalla dimensione della
risposta.

## D. Query piccola (risposta 1 KiB), 3000 chiamate sequenziali

| Percorso | p50 µs | p95 µs | p99 µs | CPU broker ms |
|---|---|---|---|---|
| attraverso il broker | 456 | 622 | 741 | 3672 |
| servita dal bundle stesso (3.0) | **28** | **34** | **41** | **0** |

Circa 16 volte meno latenza per la query che il bundle sa servire da sé, e zero lavoro per il
broker. La query segue comunque lo stesso percorso lato handler (codec, interceptor, tracing,
metriche): i 28 µs includono la codifica e la decodifica CBOR.

## E. Compressione, risposta JSON-like da 16 MiB (risposta classica)

Sul filo: 16,0 MiB senza, 3,0 MiB compressa (5,3×).

| Chi chiede accetta la compressione | Tempo client ms | Picco heap broker MiB | Alloc broker MiB | CPU broker ms |
|---|---|---|---|---|
| no | 141 | 70 | 64,1 | 94 |
| sì | 144 | **13** | **12,0** | **16** |

Il broker lavora 5 volte meno (memoria, allocazioni, CPU) e la rete porta 5 volte meno byte.
Il tempo client resta uguale su loopback, dove la banda è gratis e la compressione costa CPU
all'handler: su una rete reale, e soprattutto tra regioni, il guadagno arriva anche lì.

## Limiti della misura

- Una sola macchina, loopback: nessuna latenza o banda di rete reale. Lo stream diretto e la
  compressione guadagnano di più su una rete vera, che qui non è misurata.
- Il "picco heap" con heap grande misura anche spazzatura: per il live set fa fede lo scenario C.
- Il confronto con 2.5.2 riproduce la sua cache, non le sue copie nel riassemblaggio dei chunk
  (rimosse in 3.0): il costo reale di 2.5.2 era più alto di quello in tabella.
- Il read-your-writes (attesa aggiunta dopo un comando) non è misurato qui: richiede event
  store e proiettori, e il test `ReadYourWritesIT` ne verifica il comportamento, non il costo.

## Prossimi miglioramenti indicati dai numeri

1. **Inoltro senza decodifica completa**: il broker decodifica ogni messaggio (copiando il
   payload) solo per leggere l'header di instradamento. Leggere l'header e inoltrare il frame
   grezzo dimezzerebbe allocazioni e CPU del broker in tutte le modalità relay.
2. **Frame di stream più grandi** o coalescenza dei crediti, per ridurre il costo per-frame
   dello stream via broker (oggi più CPU della risposta classica).
3. Misura su rete reale, con le WAN di toxiproxy del lab cluster.
