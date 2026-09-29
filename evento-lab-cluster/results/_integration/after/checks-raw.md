# Integration checks: `after` (2026-09-29T11:39:43Z)

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
evento-am evento-lab-cluster/server:after
evento-apac evento-lab-cluster/server:after
evento-eu evento-lab-cluster/server:after
gateway-africa evento-lab-cluster/order-gateway:after
gateway-america evento-lab-cluster/order-gateway:after
gateway-australia-nz evento-lab-cluster/order-gateway:after
gateway-cina evento-lab-cluster/order-gateway:after
gateway-europa evento-lab-cluster/order-gateway:after
gateway-india evento-lab-cluster/order-gateway:after
gateway-sud-america evento-lab-cluster/order-gateway:after
lb nginx:alpine
order-command-am evento-lab-cluster/order-command:after
order-command-apac evento-lab-cluster/order-command:after
order-command-eu evento-lab-cluster/order-command:after
order-observer evento-lab-cluster/order-observer:after
order-query-am evento-lab-cluster/order-query:after
order-query-apac evento-lab-cluster/order-query:after
order-query-cina evento-lab-cluster/order-query:after
order-query-eu evento-lab-cluster/order-query:after
order-query-global-apac evento-lab-cluster/order-query:after
order-query-global-eu evento-lab-cluster/order-query:after
order-query-wildcard evento-lab-cluster/order-query:after
order-saga-am evento-lab-cluster/order-saga:after
order-saga-apac evento-lab-cluster/order-saga:after
order-saga-eu evento-lab-cluster/order-saga:after
toxiproxy ghcr.io/shopify/toxiproxy:2.12.0
toxiproxy-init nginx:alpine
```

- `evento-lab-cluster/server:after` framework-ref `working-tree`
- `evento-lab-cluster/order-command:after` framework-ref `working-tree`
- `evento-lab-cluster/order-query:after` framework-ref `working-tree`
- `evento-lab-cluster/order-saga:after` framework-ref `working-tree`
- `evento-lab-cluster/order-observer:after` framework-ref `working-tree`
- `evento-lab-cluster/order-gateway:after` framework-ref `working-tree`

## Framework features per deployment (/whoami)

| container | zone | contexts | consumer plan | features (available) | bus |
|---|---|---|---|---|---|
| gateway-africa | EU | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-eu:3030 READY |
| gateway-america | AM | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-am:3030 READY |
| gateway-australia-nz | APAC | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-apac:3030 READY |
| gateway-cina | APAC | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-apac:3030 READY |
| gateway-europa | EU | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-eu:3030 READY |
| gateway-india | APAC | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-apac:3030 READY |
| gateway-sud-america | AM | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-am:3030 READY |
| order-command-am | AM | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-am:3030 READY |
| order-command-apac | APAC | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-apac:3030 READY |
| order-command-eu | EU | None | {} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-eu:3030 READY |
| order-observer | EU | ['*'] | {'NotificationObserver': ['*']} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-eu:3030 READY |
| order-query-am | AM | ['AMERICA', 'SUD_AMERICA'] | {'OrderProjector': ['AMERICA', 'SUD_AMERICA'], 'CatalogProjector': []} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-am:3030 READY |
| order-query-apac | APAC | ['INDIA', 'AUSTRALIA_NZ'] | {'OrderProjector': ['INDIA', 'AUSTRALIA_NZ'], 'CatalogProjector': []} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-apac:3030 READY |
| order-query-cina | APAC | ['CINA'] | {'OrderProjector': ['CINA'], 'CatalogProjector': []} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-apac:3030 READY |
| order-query-eu | EU | ['EUROPA', 'AFRICA'] | {'OrderProjector': ['EUROPA', 'AFRICA'], 'CatalogProjector': []} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-eu:3030 READY |
| order-query-global-apac | APAC | ['ALL'] | {'OrderProjector': [], 'CatalogProjector': ['ALL']} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-apac:3030 READY |
| order-query-global-eu | EU | ['ALL'] | {'OrderProjector': [], 'CatalogProjector': ['ALL']} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-eu:3030 READY |
| order-query-wildcard | EU | ['*'] | {'OrderProjector': ['*'], 'CatalogProjector': []} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-eu:3030 READY |
| order-saga-am | AM | ['AMERICA', 'SUD_AMERICA'] | {'PaymentSaga': ['AMERICA', 'SUD_AMERICA']} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-am:3030 READY |
| order-saga-apac | APAC | ['CINA', 'INDIA', 'AUSTRALIA_NZ'] | {'PaymentSaga': ['CINA', 'INDIA', 'AUSTRALIA_NZ']} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-apac:3030 READY |
| order-saga-eu | EU | ['EUROPA', 'AFRICA'] | {'PaymentSaga': ['EUROPA', 'AFRICA']} | endpointFailover=yes, expectedClusterName=yes, zone=yes | evento-eu:3030 READY |

## Bundle presence (core__bundle_instance)
```
evento-am|gateway-america gateway-sud-america order-command-am order-query-am order-saga-am
evento-apac|gateway-australia-nz gateway-cina gateway-india order-command-apac order-query-apac order-query-cina order-query-global-apac order-saga-apac
evento-eu|gateway-africa gateway-europa order-command-eu order-observer order-query-eu order-query-global-eu order-query-wildcard order-saga-eu
```

## Consumers per context (/api/consumer on each server)

Instances are the ones live on *that* server (the dashboard view is local, G17).

### evento-eu (:13000)
```
order-observer_NotificationObserver_1_*       *              order-observer
order-query_CatalogProjector_1_ALL            ALL            order-query-global-eu
order-query_OrderProjector_1_*                *              order-query-wildcard
order-query_OrderProjector_1_AFRICA           AFRICA         order-query-eu
order-query_OrderProjector_1_AMERICA          AMERICA        -
order-query_OrderProjector_1_AUSTRALIA_NZ     AUSTRALIA_NZ   -
order-query_OrderProjector_1_CINA             CINA           -
order-query_OrderProjector_1_EUROPA           EUROPA         order-query-eu
order-query_OrderProjector_1_INDIA            INDIA          -
order-query_OrderProjector_1_SUD_AMERICA      SUD_AMERICA    -
order-saga_PaymentSaga_1_AFRICA               AFRICA         order-saga-eu
order-saga_PaymentSaga_1_AMERICA              AMERICA        -
order-saga_PaymentSaga_1_AUSTRALIA_NZ         AUSTRALIA_NZ   -
order-saga_PaymentSaga_1_CINA                 CINA           -
order-saga_PaymentSaga_1_EUROPA               EUROPA         order-saga-eu
order-saga_PaymentSaga_1_INDIA                INDIA          -
order-saga_PaymentSaga_1_SUD_AMERICA          SUD_AMERICA    -
```

### evento-am (:13001)
```
order-observer_NotificationObserver_1_*       *              -
order-query_CatalogProjector_1_ALL            ALL            -
order-query_OrderProjector_1_*                *              -
order-query_OrderProjector_1_AFRICA           AFRICA         -
order-query_OrderProjector_1_AMERICA          AMERICA        order-query-am
order-query_OrderProjector_1_AUSTRALIA_NZ     AUSTRALIA_NZ   -
order-query_OrderProjector_1_CINA             CINA           -
order-query_OrderProjector_1_EUROPA           EUROPA         -
order-query_OrderProjector_1_INDIA            INDIA          -
order-query_OrderProjector_1_SUD_AMERICA      SUD_AMERICA    order-query-am
order-saga_PaymentSaga_1_AFRICA               AFRICA         -
order-saga_PaymentSaga_1_AMERICA              AMERICA        order-saga-am
order-saga_PaymentSaga_1_AUSTRALIA_NZ         AUSTRALIA_NZ   -
order-saga_PaymentSaga_1_CINA                 CINA           -
order-saga_PaymentSaga_1_EUROPA               EUROPA         -
order-saga_PaymentSaga_1_INDIA                INDIA          -
order-saga_PaymentSaga_1_SUD_AMERICA          SUD_AMERICA    order-saga-am
```

### evento-apac (:13002)
```
order-observer_NotificationObserver_1_*       *              -
order-query_CatalogProjector_1_ALL            ALL            order-query-global-apac
order-query_OrderProjector_1_*                *              -
order-query_OrderProjector_1_AFRICA           AFRICA         -
order-query_OrderProjector_1_AMERICA          AMERICA        -
order-query_OrderProjector_1_AUSTRALIA_NZ     AUSTRALIA_NZ   order-query-apac
order-query_OrderProjector_1_CINA             CINA           order-query-cina
order-query_OrderProjector_1_EUROPA           EUROPA         -
order-query_OrderProjector_1_INDIA            INDIA          order-query-apac
order-query_OrderProjector_1_SUD_AMERICA      SUD_AMERICA    -
order-saga_PaymentSaga_1_AFRICA               AFRICA         -
order-saga_PaymentSaga_1_AMERICA              AMERICA        -
order-saga_PaymentSaga_1_AUSTRALIA_NZ         AUSTRALIA_NZ   order-saga-apac
order-saga_PaymentSaga_1_CINA                 CINA           order-saga-apac
order-saga_PaymentSaga_1_EUROPA               EUROPA         -
order-saga_PaymentSaga_1_INDIA                INDIA          order-saga-apac
order-saga_PaymentSaga_1_SUD_AMERICA          SUD_AMERICA    -
```

Catalog rows (ES primary):
```
order-observer_NotificationObserver_1_*|order-observer
order-query_CatalogProjector_1_ALL|order-query-global-apac,order-query-global-eu
order-query_OrderProjector_1_*|order-query-wildcard
order-query_OrderProjector_1_AFRICA|order-query-eu
order-query_OrderProjector_1_AMERICA|order-query-am
order-query_OrderProjector_1_AUSTRALIA_NZ|order-query-apac
order-query_OrderProjector_1_CINA|order-query-cina
order-query_OrderProjector_1_EUROPA|order-query-eu
order-query_OrderProjector_1_INDIA|order-query-apac
order-query_OrderProjector_1_SUD_AMERICA|order-query-am
order-saga_PaymentSaga_1_AFRICA|order-saga-eu
order-saga_PaymentSaga_1_AMERICA|order-saga-am
order-saga_PaymentSaga_1_AUSTRALIA_NZ|order-saga-apac
order-saga_PaymentSaga_1_CINA|order-saga-apac
order-saga_PaymentSaga_1_EUROPA|order-saga-eu
order-saga_PaymentSaga_1_INDIA|order-saga-apac
order-saga_PaymentSaga_1_SUD_AMERICA|order-saga-am
```

- `order-query_OrderProjector_1_EUROPA` / `_AFRICA` instances: `order-query-eu`
- `order-query_CatalogProjector_1_ALL` consumer ids: 1, registered instances: `order-query-global-apac,order-query-global-eu`
- wildcard lanes: `order-observer_NotificationObserver_1_*@order-observer order-query_OrderProjector_1_*@order-query-wildcard`
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
| EUROPA | 205 ms | CREATED (order-query-eu) | CREATED (order-query-wildcard) | CINA: HTTP 404 |
| AFRICA | 197 ms | CREATED (order-query-eu) | CREATED (order-query-wildcard) | CINA: HTTP 404 |
| AMERICA | 1495 ms | CREATED (order-query-am) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |
| SUD_AMERICA | 1321 ms | CREATED (order-query-am) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |
| CINA | 2447 ms | CREATED (order-query-cina) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |
| INDIA | 2268 ms | CREATED (order-query-apac) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |
| AUSTRALIA_NZ | 2464 ms | CREATED (order-query-apac) | CREATED (order-query-wildcard) | EUROPA: HTTP 404 |

Zones known to each server (/api/cluster-status/zones):

- evento-eu: `{"gateway-africa":"EU","order-query-eu":"EU","order-observer":"EU","order-saga-eu":"EU","gateway-europa":"EU","order-query-global-eu":"EU","order-query-wildcard":"EU","order-command-eu":"EU"}`
- evento-am: `{"order-saga-am":"AM","order-query-am":"AM","gateway-america":"AM","order-command-am":"AM","gateway-sud-america":"AM"}`
- evento-apac: `{"order-saga-apac":"APAC","order-query-global-apac":"APAC","order-query-apac":"APAC","order-command-apac":"APAC","gateway-australia-nz":"APAC","gateway-cina":"APAC","gateway-india":"APAC","order-query-cina":"APAC"}`
- pick strategy:       3 pick_strategy=LOCALITY_PREFERRED 

## Global price fan-out (POST /catalog/prices, context ALL)

POST via INDIA: `{"sku":"it-after-1790681983-price","region":"INDIA","instance":"gateway-india","latencyMs":979} HTTP 201`

Visible in 3 of 3 regional databases after 1 s.

| database | appliedEvents | staleSkips | lastSeq | updatedBy |
|---|---|---|---|---|
| eu | 1 | 0 | 116 | order-query-global-eu |
| am | 1 | 0 | 116 | order-query-global-eu |
| apac | 1 | 0 | 116 | order-query-global-eu |

## The ALL lane: one checkpoint, one holder at a time

Checkpoint rows in `consumers`:
```
order-query_CatalogProjector_1_ALL|116|2026-09-29 11:31:32.505732+00
```

Advisory locks held in `consumers` during a burst of 15 prices (50 samples, 200 ms apart;
clients: order-query-global-eu=172.28.1.15  order-query-global-apac=172.28.3.9  toxiproxy=172.28.2.2 172.28.3.3 172.28.1.4 172.28.9.3  ; the APAC replica reaches es-pg-eu through toxiproxy):
```
      4 172.28.1.15#2054844996
     29 172.28.1.4#2054844996
     17 none
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
es_pg_am|streaming|sync|00:00:00.092628
es_pg_apac|streaming|async|00:00:00.154379
```

- app-pg-eu: `app_pg_eu_r streaming async lag=00:00:00.003074`
- app-pg-am: `app_pg_am_r streaming async lag=00:00:00.002738`
- app-pg-apac: `app_pg_apac_r streaming async lag=0`
- ES primary max event seq: 131
- es-pg-am in recovery: t, max event seq: 131
- es-pg-apac in recovery: t, max event seq: 131
- app-pg-eu-r in recovery: t, order_view rows replica/primary: 6/6
- app-pg-am-r in recovery: t, order_view rows replica/primary: 6/6
- app-pg-apac-r in recovery: t, order_view rows replica/primary: 9/9

## Connection budget on the ES primary

- max_connections: 200
- lock pool per server: EVENTO_LOCK_POOL_MAXIMUM_SIZE=16 SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20 
- peak budget: 3 servers x (application pool + lock pool) + 2 ALL replicas x CONSUMER_STATE_DB_POOL_SIZE
```
consumers|172.28.1.15|2
consumers|172.28.1.4|2
evento|172.28.1.4|33
evento|172.28.1.6|31
postgres|local|1
```

- total client backends now: 69

## Catalog completeness (repair-catalog.sh --check)
```
[lab] event=lab_catalog_repair pass=1 status=complete
exit=0
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

