# Risultati B1: throughput di scrittura dei comandi

```
date:     2026-10-19T15:05Z
commit:   77c0e77c
cpu:      Intel(R) Core(TM) i7-6700K CPU @ 4.00GHz (8 logical CPUs)
memory:   15.6 GiB for Docker
docker:   29.6.1 on Docker Desktop (containerized), kernel 6.18.54.1-microsoft-standard-WSL2
pinning:  infrastructure (PostgreSQL 16, evento-server, Axon Server) on CPUs 0-3, applications on CPUs 4-7
per level: 20 s warm-up, 60 s measured
evento:   run 3, 2026-10-20, at ab11ec7e: optimistic concurrency, group commit, 32 bus threads per CPU
axon:     run 2 above (same machine, the day before); its code did not change
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
| 1 | 215 cmd/s | 218 cmd/s | 239 cmd/s | 247 cmd/s |
| 8 | 1141 cmd/s | 1030 cmd/s | 1265 cmd/s | 1338 cmd/s |
| 32 | 2555 cmd/s | 2474 cmd/s | 0 cmd/s | 2893 cmd/s |
| 128 | 3852 cmd/s | 3907 cmd/s | 0 cmd/s | 1 cmd/s |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 4.5 / 7.2 ms | 4.4 / 6.6 ms | 4.1 / 5.9 ms | 3.9 / 5.6 ms |
| 8 | 6.9 / 12.8 ms | 7.5 / 12.5 ms | 6.7 / 10.5 ms | 6.5 / 9.6 ms |
| 32 | 12.0 / 23.8 ms | 12.7 / 22.3 ms | 11.8 / 30031.9 ms | 10.8 / 20.0 ms |
| 128 | 32.0 / 64.3 ms | 31.6 / 60.1 ms | 30015.5 / 30015.5 ms | 38.7 / 30097.4 ms |

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
| 1 | 212 cmd/s | 224 cmd/s | 92 cmd/s | 93 cmd/s |
| 8 | 982 cmd/s | 962 cmd/s | 162 cmd/s | 226 cmd/s |
| 32 | 1847 cmd/s | 1938 cmd/s | 0 cmd/s | 243 cmd/s |
| 128 | 2269 cmd/s | 2476 cmd/s | 0 cmd/s | 0 cmd/s |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 4.5 / 8.1 ms | 4.3 / 6.7 ms | 10.4 / 18.8 ms | 10.3 / 18.7 ms |
| 8 | 7.3 / 17.7 ms | 7.7 / 15.9 ms | 45.5 / 106.6 ms | 34.5 / 63.8 ms |
| 32 | 15.7 / 42.5 ms | 15.4 / 35.7 ms | – | 127.6 / 236.9 ms |
| 128 | 47.0 / 196.7 ms | 41.5 / 188.8 ms | – | 35.4 / 30081.0 ms |

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
| 1 | 53 cmd/s | 53 cmd/s | 3 cmd/s | 3 cmd/s |
| 8 | 51 cmd/s | 53 cmd/s | 4 cmd/s | 7 cmd/s |
| 32 | 51 cmd/s | 52 cmd/s | 0 cmd/s | 8 cmd/s |
| 128 | 49 cmd/s | 50 cmd/s | 0 cmd/s | 0 cmd/s |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 18.4 / 31.6 ms | 18.3 / 40.0 ms | 293.6 / 391.9 ms | 293.4 / 390.9 ms |
| 8 | 152.3 / 214.4 ms | 150.0 / 204.9 ms | 1213.4 / 1933.3 ms | 1036.3 / 1378.3 ms |
| 32 | 619.5 / 823.8 ms | 605.7 / 806.9 ms | – | 3565.6 / 4919.3 ms |
| 128 | 2465.8 / 3231.7 ms | 2365.4 / 3239.9 ms | – | 30031.9 / 30048.3 ms |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa | axon5-jpa-pool64 |
|---:|---:|---:|---:|---:|
| 1 | 0% | 0% | 0% | 0% |
| 8 | 0% | 0% | 33% | 3% |
| 32 | 0% | 0% | 100% | 12% |
| 128 | 0% | 0% | 100% | 96% |

Errori visti: AppendEventsTransactionRejectedException, CannotCreateTransactionException, JDBCConnectionException.

