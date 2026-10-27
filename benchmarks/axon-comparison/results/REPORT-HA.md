# Risultati B2: alta affidabilità su due AZ

```
date:     2026-10-20T00:22Z
commit:   95cb2eb7 (lock waiters queue in PostgreSQL; + local changes: the B2 harness)
cpu:      Intel(R) Core(TM) i7-6700K CPU @ 4.00GHz (8 logical CPUs), Docker Desktop on WSL2
topology: 2 AZ; PostgreSQL 16 primary (AZ a) + synchronous standby (AZ b, tmpfs); 2 Evento servers or 2 Axon instances, one per AZ
pinning:  infrastructure on CPUs 0-3 (both PostgreSQL, both servers, toxiproxy), applications on 4-7
per level: 15 s warm-up, 45 s measured; spread and hot orders shared by the two AZs
```

## Come leggere le tabelle

- **Topologia**: due AZ. AZ a: PostgreSQL primario, un server (o un'istanza Axon), il suo bundle e il
  suo generatore di carico. AZ b: lo standby **sincrono** (ogni commit aspetta che sia anche lì), un
  secondo server con bundle e carico, che raggiunge il database attraverso l'AZ. **RTT** = round trip
  aggiunto tra le AZ (replica e connessioni al database dall'AZ b).
- **in volo**: comandi inviati e non ancora risposti, in totale (metà per AZ).
- **throughput**, in **cmd/s**: comandi completati con successo al secondo, somma delle due AZ, e tra
  parentesi la parte di ciascuna (AZ a / AZ b): un sistema che serve bene solo l'AZ del primario lo
  mostra lì. Più alto è meglio.
- **p50 / p99**, in **ms**: latenza vista dal chiamante; la peggiore delle due AZ. Più basso è meglio.
  Un'AZ che non ha completato nessun comando nella finestra è segnata **ferma**.
- **rifiutati**, in **%**: comandi terminati con un errore (conflitto, connessioni esaurite, timeout di
  30 s) sul totale. Non entrano nel throughput.
- Tutto su una macchina (stesse CPU di B1): misura il costo della replica sincrona e della latenza tra
  AZ per ciascun design, non il guadagno di più hardware.

## RTT tra le AZ: 0 ms

### Create: ogni comando crea un nuovo aggregato

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 763 cmd/s (401 / 361) | 403 cmd/s (202 / 201) | 926 cmd/s (528 / 398) |
| 32 | 1550 cmd/s (788 / 762) | 1335 cmd/s (676 / 658) | 1788 cmd/s (1001 / 788) |
| 128 | 2261 cmd/s (1138 / 1123) | 2305 cmd/s (1164 / 1140) | 1927 cmd/s (1045 / 882) |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 10.0 / 25.7 ms | 19.2 / 33.4 ms | 9.4 / 19.1 ms |
| 32 | 20.1 / 40.6 ms | 23.7 / 40.2 ms | 19.6 / 34.5 ms |
| 128 | 55.4 / 112.8 ms | 54.2 / 91.1 ms | 70.5 / 129.5 ms |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 0% | 0% | 0% |
| 32 | 0% | 0% | 0% |
| 128 | 0% | 0% | 0% |

### Spread: ogni comando aggiunge un item a uno di 1000 aggregati condivisi dai due server

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 808 cmd/s (416 / 393) | 413 cmd/s (206 / 206) | 212 cmd/s (134 / 78) |
| 32 | 1519 cmd/s (760 / 759) | 1271 cmd/s (640 / 631) | 238 cmd/s (144 / 94) |
| 128 | 1616 cmd/s (762 / 854) | 2076 cmd/s (1039 / 1036) | 321 cmd/s (188 / 132) |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 9.5 / 22.3 ms | 19.1 / 34.6 ms | 49.4 / 97.6 ms |
| 32 | 19.4 / 53.6 ms | 23.9 / 55.8 ms | 158.1 / 399.9 ms |
| 128 | 51.1 / 1058.8 ms | 52.8 / 194.9 ms | 430.8 / 1207.3 ms |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 0% | 0% | 0% |
| 32 | 0% | 0% | 0% |
| 128 | 0% | 0% | 9% |

Errori visti: AppendEventsTransactionRejectedException, EntityMissingForInstanceCommandHandlerException.

### Hot: ogni comando aggiunge un item allo stesso aggregato, attraverso entrambi i server

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 58 cmd/s (29 / 29) | 58 cmd/s (29 / 29) | 7 cmd/s (5 / 2) |
| 32 | 58 cmd/s (29 / 29) | 58 cmd/s (29 / 29) | 6 cmd/s (4 / 2) |
| 128 | 55 cmd/s (28 / 28) | 55 cmd/s (28 / 27) | 0 cmd/s (0 / 0) |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 140.3 / 186.0 ms | 139.9 / 203.8 ms | 1847.3 / 2951.2 ms |
| 32 | 548.9 / 687.6 ms | 552.4 / 748.0 ms | 5804.0 / 6758.4 ms |
| 128 | 2275.3 / 2773.0 ms | 2275.3 / 2897.9 ms | 1679.4 / 2334.7 ms (un'AZ ferma) |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 0% | 0% | 4% |
| 32 | 0% | 0% | 19% |
| 128 | 0% | 0% | 17% |

Errori visti: AppendEventsTransactionRejectedException.

## RTT tra le AZ: 2 ms

### Create: ogni comando crea un nuovo aggregato

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 524 cmd/s (366 / 159) | 224 cmd/s (115 / 109) | 529 cmd/s (454 / 75) |
| 32 | 1418 cmd/s (887 / 531) | 811 cmd/s (422 / 389) | 1504 cmd/s (1202 / 302) |
| 128 | 2161 cmd/s (1204 / 956) | 1931 cmd/s (1035 / 896) | 1935 cmd/s (1323 / 613) |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 24.4 / 37.7 ms | 34.9 / 53.2 ms | 53.0 / 62.0 ms |
| 32 | 28.9 / 49.8 ms | 38.3 / 58.2 ms | 52.5 / 64.4 ms |
| 128 | 65.2 / 116.7 ms | 71.0 / 108.2 ms | 102.3 / 160.5 ms |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 0% | 0% | 0% |
| 32 | 0% | 0% | 0% |
| 128 | 0% | 0% | 0% |

### Spread: ogni comando aggiunge un item a uno di 1000 aggregati condivisi dai due server

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 582 cmd/s (364 / 218) | 230 cmd/s (117 / 114) | 187 cmd/s (175 / 12) |
| 32 | 1407 cmd/s (805 / 601) | 813 cmd/s (412 / 400) | 238 cmd/s (205 / 33) |
| 128 | 1835 cmd/s (958 / 877) | 1771 cmd/s (906 / 865) | 267 cmd/s (228 / 40) |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 17.3 / 36.3 ms | 34.3 / 52.2 ms | 325.6 / 631.8 ms |
| 32 | 24.7 / 68.9 ms | 37.6 / 95.4 ms | 449.5 / 1074.2 ms |
| 128 | 52.7 / 315.9 ms | 60.6 / 250.2 ms | 1204.2 / 5070.8 ms |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 0% | 0% | 0% |
| 32 | 0% | 0% | 1% |
| 128 | 0% | 0% | 1% |

Errori visti: AppendEventsTransactionRejectedException, EntityMissingForInstanceCommandHandlerException.

### Hot: ogni comando aggiunge un item allo stesso aggregato, attraverso entrambi i server

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 45 cmd/s (22 / 22) | 45 cmd/s (22 / 23) | 7 cmd/s (7 / 0) |
| 32 | 46 cmd/s (23 / 23) | 44 cmd/s (22 / 23) | 7 cmd/s (7 / 0) |
| 128 | 44 cmd/s (22 / 22) | 44 cmd/s (22 / 22) | 0 cmd/s (0 / 0) |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 175.6 / 267.0 ms | 175.9 / 250.8 ms | 17743.9 / 17743.9 ms |
| 32 | 683.0 / 897.5 ms | 724.5 / 867.3 ms | 27590.7 / 27705.3 ms |
| 128 | 3006.5 / 3465.2 ms | 2912.3 / 3434.5 ms | 719.4 / 1211.4 ms (un'AZ ferma) |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 0% | 0% | 2% |
| 32 | 0% | 0% | 13% |
| 128 | 0% | 0% | 57% |

Errori visti: AppendEventsTransactionRejectedException.

## RTT tra le AZ: 10 ms

### Create: ogni comando crea un nuovo aggregato

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 286 cmd/s (224 / 62) | 91 cmd/s (52 / 39) | – |
| 32 | 958 cmd/s (720 / 238) | 359 cmd/s (205 / 154) | – |
| 128 | 1986 cmd/s (1529 / 457) | 1181 cmd/s (717 / 464) | – |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 64.2 / 75.8 ms | 99.6 / 119.0 ms | – |
| 32 | 66.0 / 83.6 ms | 101.4 / 131.8 ms | – |
| 128 | 135.3 / 250.0 ms | 129.6 / 196.2 ms | – |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 0% | 0% | – |
| 32 | 0% | 0% | – |
| 128 | 0% | 0% | – |

### Spread: ogni comando aggiunge un item a uno di 1000 aggregati condivisi dai due server

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 305 cmd/s (218 / 88) | 94 cmd/s (50 / 44) | – |
| 32 | 994 cmd/s (677 / 317) | 371 cmd/s (198 / 173) | – |
| 128 | 1593 cmd/s (1040 / 553) | 1182 cmd/s (681 / 502) | – |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 41.4 / 102.8 ms | 89.0 / 139.5 ms | – |
| 32 | 46.0 / 150.1 ms | 86.4 / 239.2 ms | – |
| 128 | 67.6 / 939.5 ms | 94.5 / 458.0 ms | – |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 0% | 0% | – |
| 32 | 0% | 0% | – |
| 128 | 0% | 0% | – |

### Hot: ogni comando aggiunge un item allo stesso aggregato, attraverso entrambi i server

**Throughput** (cmd/s, più alto è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 26 cmd/s (16 / 10) | 26 cmd/s (16 / 10) | – |
| 32 | 26 cmd/s (16 / 10) | 26 cmd/s (16 / 10) | – |
| 128 | 24 cmd/s (15 / 9) | 24 cmd/s (15 / 9) | – |

**Latenza** (ms, p50 / p99, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 419.8 / 458.5 ms | 417.3 / 465.7 ms | – |
| 32 | 1688.6 / 1765.4 ms | 1678.3 / 1786.9 ms | – |
| 128 | 6668.3 / 6844.4 ms | 6578.2 / 6885.4 ms | – |

**Rifiutati** (% dei comandi conclusi, più basso è meglio)

| in volo | evento-APES | evento-CPES | axon5-jpa-pool64 |
|---:|---:|---:|---:|
| 8 | 0% | 0% | – |
| 32 | 0% | 0% | – |
| 128 | 0% | 0% | – |

