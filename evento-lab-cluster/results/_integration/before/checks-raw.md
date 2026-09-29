# Integration checks: `before` (2026-09-29T11:55:23Z)

## Images
```
app-pg-am postgres:17-alpine
app-pg-am-r postgres:17-alpine
app-pg-apac postgres:17-alpine
app-pg-apac-r postgres:17-alpine
app-pg-eu postgres:17-alpine
app-pg-eu-r postgres:17-alpine
es-pg-am postgres:17-alpine
es-pg-apac postgres:17-alpine
es-pg-eu postgres:17-alpine
evento-am evento-lab-cluster/server:before
evento-apac evento-lab-cluster/server:before
evento-eu evento-lab-cluster/server:before
gateway-africa evento-lab-cluster/order-gateway:before
gateway-america evento-lab-cluster/order-gateway:before
gateway-australia-nz evento-lab-cluster/order-gateway:before
gateway-cina evento-lab-cluster/order-gateway:before
gateway-europa evento-lab-cluster/order-gateway:before
gateway-india evento-lab-cluster/order-gateway:before
gateway-sud-america evento-lab-cluster/order-gateway:before
lb nginx:alpine
order-command-am evento-lab-cluster/order-command:before
order-command-apac evento-lab-cluster/order-command:before
order-command-eu evento-lab-cluster/order-command:before
order-observer evento-lab-cluster/order-observer:before
order-query-am evento-lab-cluster/order-query:before
order-query-apac evento-lab-cluster/order-query:before
order-query-cina evento-lab-cluster/order-query:before
order-query-eu evento-lab-cluster/order-query:before
order-query-global-apac evento-lab-cluster/order-query:before
order-query-global-eu evento-lab-cluster/order-query:before
order-query-wildcard evento-lab-cluster/order-query:before
order-saga-am evento-lab-cluster/order-saga:before
order-saga-apac evento-lab-cluster/order-saga:before
order-saga-eu evento-lab-cluster/order-saga:before
toxiproxy ghcr.io/shopify/toxiproxy:2.12.0
toxiproxy-init nginx:alpine
```

- `evento-lab-cluster/server:before` framework-ref `e7b823fe`
- `evento-lab-cluster/order-command:before` framework-ref `e7b823fe`
- `evento-lab-cluster/order-query:before` framework-ref `e7b823fe`
- `evento-lab-cluster/order-saga:before` framework-ref `e7b823fe`
- `evento-lab-cluster/order-observer:before` framework-ref `e7b823fe`
- `evento-lab-cluster/order-gateway:before` framework-ref `e7b823fe`

## Framework features per deployment (/whoami)

| container | zone | contexts | consumer plan | features (available) | bus |
|---|---|---|---|---|---|
| gateway-africa | EU | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-eu:3030 READY |
| gateway-america | AM | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-am:3030 READY |
| gateway-australia-nz | APAC | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-apac:3030 READY |
| gateway-cina | APAC | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-apac:3030 READY |
| gateway-europa | EU | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-eu:3030 READY |
| gateway-india | APAC | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-apac:3030 READY |
| gateway-sud-america | AM | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-am:3030 READY |
| order-command-am | AM | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-am:3030 READY |
| order-command-apac | APAC | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-apac:3030 READY |
| order-command-eu | EU | None | {} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-eu:3030 READY |
| order-observer | EU | ['*'] | {'NotificationObserver': ['*']} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-eu:3030 READY |
| order-query-am | AM | ['AMERICA', 'SUD_AMERICA'] | {'OrderProjector': ['AMERICA', 'SUD_AMERICA'], 'CatalogProjector': []} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-am:3030 READY |
| order-query-apac | APAC | ['INDIA', 'AUSTRALIA_NZ'] | {'OrderProjector': ['INDIA', 'AUSTRALIA_NZ'], 'CatalogProjector': []} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-apac:3030 READY |
| order-query-cina | APAC | ['CINA'] | {'OrderProjector': ['CINA'], 'CatalogProjector': []} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-apac:3030 READY |
| order-query-eu | EU | ['EUROPA', 'AFRICA'] | {'OrderProjector': ['EUROPA', 'AFRICA'], 'CatalogProjector': []} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-eu:3030 READY |
| order-query-global-apac | APAC | ['ALL'] | {'OrderProjector': [], 'CatalogProjector': ['ALL']} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-apac:3030 READY |
| order-query-global-eu | EU | ['ALL'] | {'OrderProjector': [], 'CatalogProjector': ['ALL']} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-eu:3030 READY |
| order-query-wildcard | EU | ['*'] | {'OrderProjector': ['*'], 'CatalogProjector': []} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-eu:3030 READY |
| order-saga-am | AM | ['AMERICA', 'SUD_AMERICA'] | {'PaymentSaga': ['AMERICA', 'SUD_AMERICA']} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-am:3030 READY |
| order-saga-apac | APAC | ['CINA', 'INDIA', 'AUSTRALIA_NZ'] | {'PaymentSaga': ['CINA', 'INDIA', 'AUSTRALIA_NZ']} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-apac:3030 READY |
| order-saga-eu | EU | ['EUROPA', 'AFRICA'] | {'PaymentSaga': ['EUROPA', 'AFRICA']} | endpointFailover=NO, expectedClusterName=NO, zone=NO | evento-eu:3030 READY |

## Bundle presence (core__bundle_instance)
```
ERROR:  relation "core__bundle_instance" does not exist
LINE 1: ...g_agg(instance_id, ' ' order by instance_id) from core__bund...
                                                             ^
```

## Consumers per context (/api/consumer on each server)

Instances are the ones live on *that* server (the dashboard view is local, G17).

### evento-eu (:13000)
```
order-observer_NotificationObserver_1_*       *              order-observer
order-query_CatalogProjector_1_ALL            ALL            order-query-global-eu
order-query_OrderProjector_1_AFRICA           AFRICA         order-query-eu
order-saga_PaymentSaga_1_AMERICA              AMERICA        -
```

### evento-am (:13001)
```
order-observer_NotificationObserver_1_*       *              -
order-query_CatalogProjector_1_ALL            ALL            -
order-query_OrderProjector_1_AFRICA           AFRICA         order-query-am
order-saga_PaymentSaga_1_AMERICA              AMERICA        order-saga-am
```

### evento-apac (:13002)
```
order-observer_NotificationObserver_1_*       *              -
order-query_CatalogProjector_1_ALL            ALL            order-query-global-apac
order-query_OrderProjector_1_AFRICA           AFRICA         order-query-apac,order-query-cina
order-saga_PaymentSaga_1_AMERICA              AMERICA        order-saga-apac
```

Catalog rows (ES primary):
```
order-observer_NotificationObserver_1_*|order-observer
order-query_CatalogProjector_1_ALL|order-query-global-apac,order-query-global-eu
order-query_OrderProjector_1_AFRICA|order-query-eu
order-query_OrderProjector_1_AMERICA|order-query-am
order-query_OrderProjector_1_AUSTRALIA_NZ|order-query-apac
order-query_OrderProjector_1_CINA|order-query-cina
order-query_OrderProjector_1_EUROPA|order-query-eu
order-query_OrderProjector_1_INDIA|order-query-apac
order-query_OrderProjector_1_SUD_AMERICA|order-query-am
order-saga_PaymentSaga_1_AMERICA|order-saga-am
order-saga_PaymentSaga_1_AUSTRALIA_NZ|order-saga-apac
order-saga_PaymentSaga_1_CINA|order-saga-apac
order-saga_PaymentSaga_1_INDIA|order-saga-apac
order-saga_PaymentSaga_1_SUD_AMERICA|order-saga-am
```

- `order-query_OrderProjector_1_EUROPA` / `_AFRICA` instances: `order-query-eu`
- `order-query_CatalogProjector_1_ALL` consumer ids: 1, registered instances: `order-query-global-apac,order-query-global-eu`
- wildcard lanes: `order-observer_NotificationObserver_1_*@order-observer`
- consumers with context ALL other than the CatalogProjector lane: 0

## LB routing (/whoami through lb:8080 with X-Region)

| X-Region | X-Lab-Region | served by | cell | server |
|---|---|---|---|---|
| EUROPA | europa | gateway-europa | EU | evento-eu:3030 |
| AFRICA | africa | gateway-africa | EU | evento-eu:3030 |
| AMERICA | america | gateway-america | AM | evento-am:3030 |
| SUD_AMERICA | sud_america | gateway-sud-america | AM | evento-am:3030 |
| CINA | cina | gateway-cina | APAC | evento-apac:3030 |
| INDIA | india | gateway-india | APAC | evento-apac:3030 |
| AUSTRALIA_NZ | australia_nz | gateway-australia-nz | APAC | evento-apac:3030 |

## Orders: create in each region, read in-cell and globally

| region | create | in-cell GET (projected by) | global GET | GET from another cell |
|---|---|---|---|---|
| EUROPA | 464 ms | CREATED (order-query-eu) | CREATED (order-query-wildcard) | CINA: HTTP 404 |
| AFRICA | 263 ms | CREATED (order-query-eu) | CREATED (order-query-wildcard) | CINA: HTTP 404 |
| AMERICA | 2420 ms | CREATED (order-query-am) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |
| SUD_AMERICA | 2518 ms | CREATED (order-query-am) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |
| CINA | 4401 ms | CREATED (order-query-cina) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |
| INDIA | 4317 ms | CREATED (order-query-apac) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |
| AUSTRALIA_NZ | 4176 ms | CREATED (order-query-apac) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |

Zones known to each server (/api/cluster-status/zones):

- evento-eu: `{"timestamp":"2026-09-29T11:56:09.047Z","status":500,"error":"Internal Server Error","path":"/api/cluster-status/zones"}`
- evento-am: `{"timestamp":"2026-09-29T11:56:09.194Z","status":500,"error":"Internal Server Error","path":"/api/cluster-status/zones"}`
- evento-apac: `{"timestamp":"2026-09-29T11:56:09.349Z","status":500,"error":"Internal Server Error","path":"/api/cluster-status/zones"}`
- pick strategy: 

## Global price fan-out (POST /catalog/prices, context ALL)

POST via INDIA: `{"sku":"it-before-1790682923-price","region":"INDIA","instance":"gateway-india","latencyMs":1777} HTTP 201`

Visible in 3 of 3 regional databases after 4 s.

| database | appliedEvents | staleSkips | lastSeq | updatedBy |
|---|---|---|---|---|
| eu | 1 | 0 | 15 | order-query-global-apac |
| am | 1 | 0 | 15 | order-query-global-apac |
| apac | 1 | 0 | 15 | order-query-global-apac |

## The ALL lane: one checkpoint, one holder at a time

Checkpoint rows in `consumers`:
```
order-query_CatalogProjector_1_ALL|15|2026-09-29 11:56:16.444699+00
```

Advisory locks held in `consumers` during a burst of 15 prices (50 samples, 200 ms apart;
clients: order-query-global-eu=172.28.1.15  order-query-global-apac=172.28.3.12  toxiproxy=172.28.2.3 172.28.3.3 172.28.1.4 172.28.9.3  ; the APAC replica reaches es-pg-eu through toxiproxy):
```
     12 172.28.1.15#2054844996
     23 172.28.1.4#2054844996
     15 none
```

Samples with more than one holder: 0 of 50.

- app-pg-eu: `16 rows, max applied_events 1, stale_skips 0, writers order-query-global-apac+order-query-global-eu`
- app-pg-am: `16 rows, max applied_events 1, stale_skips 0, writers order-query-global-apac+order-query-global-eu`
- app-pg-apac: `16 rows, max applied_events 1, stale_skips 0, writers order-query-global-apac+order-query-global-eu`

## WAN toxics
```
am-to-app-pg-apac        [::]:20232       app-pg-apac:5432       enabled=True wan-latency-up:85/5 wan-latency-down:85/5
am-to-app-pg-eu          [::]:20212       app-pg-eu:5432         enabled=True wan-latency-up:40/5 wan-latency-down:40/5
am-to-es-pg-apac         [::]:20231       es-pg-apac:5432        enabled=True wan-latency-up:85/5 wan-latency-down:85/5
am-to-es-pg-eu           [::]:20211       es-pg-eu:5432          enabled=True wan-latency-up:40/5 wan-latency-down:40/5
am-to-evento-apac        [::]:20233       evento-apac:3030       enabled=True wan-latency-up:85/5 wan-latency-down:85/5
am-to-evento-eu          [::]:20213       evento-eu:3030         enabled=True wan-latency-up:40/5 wan-latency-down:40/5
apac-to-app-pg-am        [::]:20322       app-pg-am:5432         enabled=True wan-latency-up:85/5 wan-latency-down:85/5
apac-to-app-pg-eu        [::]:20312       app-pg-eu:5432         enabled=True wan-latency-up:75/5 wan-latency-down:75/5
apac-to-es-pg-am         [::]:20321       es-pg-am:5432          enabled=True wan-latency-up:85/5 wan-latency-down:85/5
apac-to-es-pg-eu         [::]:20311       es-pg-eu:5432          enabled=True wan-latency-up:75/5 wan-latency-down:75/5
apac-to-evento-am        [::]:20323       evento-am:3030         enabled=True wan-latency-up:85/5 wan-latency-down:85/5
apac-to-evento-eu        [::]:20313       evento-eu:3030         enabled=True wan-latency-up:75/5 wan-latency-down:75/5
eu-to-app-pg-am          [::]:20122       app-pg-am:5432         enabled=True wan-latency-up:40/5 wan-latency-down:40/5
eu-to-app-pg-apac        [::]:20132       app-pg-apac:5432       enabled=True wan-latency-up:75/5 wan-latency-down:75/5
eu-to-es-pg-am           [::]:20121       es-pg-am:5432          enabled=True wan-latency-up:40/5 wan-latency-down:40/5
eu-to-es-pg-apac         [::]:20131       es-pg-apac:5432        enabled=True wan-latency-up:75/5 wan-latency-down:75/5
eu-to-evento-am          [::]:20123       evento-am:3030         enabled=True wan-latency-up:40/5 wan-latency-down:40/5
eu-to-evento-apac        [::]:20133       evento-apac:3030       enabled=True wan-latency-up:75/5 wan-latency-down:75/5
```

## Replication
ES primary (es-pg-eu):
```
es_pg_am|streaming|sync|
es_pg_apac|streaming|async|
```

- app-pg-eu: `app_pg_eu_r streaming async lag=0`
- app-pg-am: `app_pg_am_r streaming async lag=0`
- app-pg-apac: `app_pg_apac_r streaming async lag=0`
- ES primary max event seq: 30
- es-pg-am in recovery: t, max event seq: 30
- es-pg-apac in recovery: t, max event seq: 30
- app-pg-eu-r in recovery: t, order_view rows replica/primary: 2/2
- app-pg-am-r in recovery: t, order_view rows replica/primary: 2/2
- app-pg-apac-r in recovery: t, order_view rows replica/primary: 3/3

## Connection budget on the ES primary

- max_connections: 200
- lock pool per server: SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20 EVENTO_LOCK_POOL_MAXIMUM_SIZE=16 
- peak budget: 3 servers x (application pool + lock pool) + 2 ALL replicas x CONSUMER_STATE_DB_POOL_SIZE
```
consumers|172.28.1.15|3
consumers|172.28.1.4|3
evento|172.28.1.4|40
evento|172.28.1.6|20
postgres|local|1
```

- total client backends now: 67

## Catalog completeness (repair-catalog.sh --check)
```
[lab] event=lab_catalog_repair pass=1 missing=order-query-wildcard:consumers=0/1 order-saga-eu:consumers=0/2
exit=1
```

## Restarts and health
```
app-pg-am                  healthy    restarts=0
app-pg-am-r                healthy    restarts=0
app-pg-apac                healthy    restarts=0
app-pg-apac-r              healthy    restarts=0
app-pg-eu                  healthy    restarts=0
app-pg-eu-r                healthy    restarts=0
es-pg-am                   healthy    restarts=0
es-pg-apac                 healthy    restarts=0
es-pg-eu                   healthy    restarts=0
evento-am                  healthy    restarts=0
evento-apac                healthy    restarts=0
evento-eu                  healthy    restarts=0
gateway-africa             healthy    restarts=0
gateway-america            healthy    restarts=0
gateway-australia-nz       healthy    restarts=0
gateway-cina               healthy    restarts=0
gateway-europa             healthy    restarts=0
gateway-india              healthy    restarts=0
gateway-sud-america        healthy    restarts=0
lb                         healthy    restarts=0
order-command-am           healthy    restarts=0
order-command-apac         healthy    restarts=0
order-command-eu           healthy    restarts=0
order-observer             healthy    restarts=0
order-query-am             healthy    restarts=0
order-query-apac           healthy    restarts=0
order-query-cina           healthy    restarts=0
order-query-eu             healthy    restarts=0
order-query-global-apac    healthy    restarts=0
order-query-global-eu      healthy    restarts=0
order-query-wildcard       healthy    restarts=0
order-saga-am              healthy    restarts=0
order-saga-apac            healthy    restarts=0
order-saga-eu              healthy    restarts=0
toxiproxy                  healthy    restarts=0
toxiproxy-init             healthy    restarts=0
```

