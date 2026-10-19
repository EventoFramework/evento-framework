# Risultati B1: throughput di scrittura dei comandi

```
date:     2026-10-19T15:05Z
commit:   77c0e77c
cpu:      Intel(R) Core(TM) i7-6700K CPU @ 4.00GHz (8 logical CPUs)
memory:   15.6 GiB for Docker
docker:   29.6.1 on Docker Desktop (containerized), kernel 6.18.54.1-microsoft-standard-WSL2
pinning:  infrastructure (PostgreSQL 16, evento-server, Axon Server) on CPUs 0-3, applications on CPUs 4-7
per level: 20 s warm-up, 60 s measured
re-run:   evento-APES at a53fbbff (Snowflake ids no longer fail when the clock steps back), same machine, 2026-10-19
```

## Come leggere le tabelle

- **in volo**: comandi inviati e non ancora risposti, sempre esattamente questi (N chiamanti, ognuno
  invia il successivo appena riceve la risposta).
- **throughput**, in **cmd/s** (comandi al secondo): comandi completati con successo, cioè con
  l'evento salvato in modo durevole e la risposta arrivata al chiamante, diviso i 60 s misurati.
  Più alto è meglio.
- **p50 / p99**, in **ms** (millisecondi): latenza vista dal chiamante, dall'invio alla risposta.
  p50 = metà dei comandi è più veloce di così; p99 = 99 comandi su 100 sono più veloci. Più basso è
  meglio. Solo i comandi riusciti.
- **rifiutati**, in **%**: comandi terminati con un errore (conflitto di concorrenza, connessioni
  esaurite, timeout di 30 s) sul totale dei comandi conclusi. Non entrano nel throughput.
- **0 cmd/s** con **100%** rifiutati: il sistema non ha completato nessun comando in quella
  configurazione (collasso).

## Create: ogni comando crea un nuovo aggregato

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 200 cmd/s | 200 cmd/s | 239 cmd/s | 247 cmd/s |
| 8 | 1045 cmd/s | 408 cmd/s | 1265 cmd/s | 1338 cmd/s |
| 32 | 1305 cmd/s | 427 cmd/s | 0 cmd/s | 2893 cmd/s |
| 128 | 1326 cmd/s | 428 cmd/s | 0 cmd/s | 1 cmd/s |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 4.8 / 7.0 ms | 4.8 / 8.0 ms | 4.1 / 5.9 ms | 3.9 / 5.6 ms |
| 8 | 7.5 / 12.7 ms | 18.8 / 30.1 ms | 6.7 / 10.5 ms | 6.5 / 9.6 ms |
| 32 | 24.8 / 41.4 ms | 73.0 / 111.6 ms | 11.8 / 30031.9 ms | 10.8 / 20.0 ms |
| 128 | 97.9 / 152.8 ms | 293.4 / 357.4 ms | 30015.5 / 30015.5 ms | 38.7 / 30097.4 ms |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 0% | 0% | 0% | 0% |
| 8 | 0% | 0% | 0% | 0% |
| 32 | 0% | 0% | 88% | 0% |
| 128 | 0% | 0% | 97% | 61% |

Errori visti: CannotCreateTransactionException, JDBCConnectionException.

## Spread: ogni comando aggiunge un item a uno di 1000 aggregati esistenti, a caso

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 208 cmd/s | 203 cmd/s | 92 cmd/s | 93 cmd/s |
| 8 | 1005 cmd/s | 413 cmd/s | 162 cmd/s | 226 cmd/s |
| 32 | 1199 cmd/s | 430 cmd/s | 0 cmd/s | 243 cmd/s |
| 128 | 1209 cmd/s | 419 cmd/s | 0 cmd/s | 0 cmd/s |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 4.6 / 7.4 ms | 4.7 / 7.7 ms | 10.4 / 18.8 ms | 10.3 / 18.7 ms |
| 8 | 7.6 / 14.7 ms | 19.0 / 27.2 ms | 45.5 / 106.6 ms | 34.5 / 63.8 ms |
| 32 | 26.7 / 48.5 ms | 72.8 / 122.8 ms | – | 127.6 / 236.9 ms |
| 128 | 107.2 / 160.0 ms | 298.8 / 404.2 ms | – | 35.4 / 30081.0 ms |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 0% | 0% | 0% | 0% |
| 8 | 0% | 0% | 0% | 0% |
| 32 | 0% | 0% | 100% | 0% |
| 128 | 0% | 0% | 100% | 82% |

Errori visti: AppendEventsTransactionRejectedException, CannotCreateTransactionException, JDBCConnectionException.

## Hot: ogni comando aggiunge un item allo stesso aggregato

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 54 cmd/s | 52 cmd/s | 3 cmd/s | 3 cmd/s |
| 8 | 52 cmd/s | 52 cmd/s | 4 cmd/s | 7 cmd/s |
| 32 | 51 cmd/s | 52 cmd/s | 0 cmd/s | 8 cmd/s |
| 128 | 49 cmd/s | 50 cmd/s | 0 cmd/s | 0 cmd/s |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 18.5 / 28.5 ms | 18.6 / 31.3 ms | 293.6 / 391.9 ms | 293.4 / 390.9 ms |
| 8 | 152.7 / 204.0 ms | 154.0 / 205.8 ms | 1213.4 / 1933.3 ms | 1036.3 / 1378.3 ms |
| 32 | 615.9 / 813.1 ms | 601.6 / 820.7 ms | – | 3565.6 / 4919.3 ms |
| 128 | 2471.9 / 3403.8 ms | 2367.5 / 3147.8 ms | – | 30031.9 / 30048.3 ms |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 0% | 0% | 0% | 0% |
| 8 | 0% | 0% | 33% | 3% |
| 32 | 0% | 0% | 100% | 12% |
| 128 | 0% | 0% | 100% | 96% |

Errori visti: AppendEventsTransactionRejectedException, CannotCreateTransactionException, JDBCConnectionException.

