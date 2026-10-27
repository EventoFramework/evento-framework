[![headline](docs/assets/headline.png)](https://www.eventoframework.com/)

# [Evento Framework](https://www.eventoframework.com/)

[![License: client libraries](https://img.shields.io/badge/client%20libraries-Apache%202.0-blue.svg)](LICENSE.txt) [![License: server](https://img.shields.io/badge/server-Apache%202.0%20%2B%20Commons%20Clause-blue.svg)](LICENSE.txt)
[![CI](https://github.com/EventoFramework/evento-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/EventoFramework/evento-framework/actions/workflows/ci.yml)
[![CodeQL](https://github.com/EventoFramework/evento-framework/actions/workflows/codeql.yml/badge.svg)](https://github.com/EventoFramework/evento-framework/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/EventoFramework/evento-framework/badge)](https://securityscorecards.dev/viewer/?uri=github.com/EventoFramework/evento-framework)
[![release](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2FEventoFramework%2Fevento-framework%2Fmain%2Fevento-server%2Fversion.json&query=%24.version&prefix=v&label=release&color=blue)](https://github.com/EventoFramework/evento-framework/releases)
[![](https://img.shields.io/maven-central/v/com.eventoframework/evento-bundle?logo=gradle)](https://central.sonatype.com/artifact/com.eventoframework/evento-bundle)
[![image](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2FEventoFramework%2Fevento-framework%2Fmain%2Fevento-server%2Fversion.json&query=%24.version&label=image&logo=docker&color=blue)](https://hub.docker.com/r/eventoframework/evento-server)


## What is Evento Framework?

The Evento Framework equips developers with a robust toolkit for building and managing distributed applications that leverage the Event Sourcing and CQRS (Command Query Responsibility Segregation) architectural patterns. This potent combination empowers you to create:

-   **Resilient Systems:** Event sourcing guarantees data consistency and facilitates effortless recovery from unforeseen disruptions.
-   **Scalable Architectures:** Evento's distributed nature allows you to seamlessly scale your application horizontally to meet ever-growing demands.
-   **Maintainable Codebases:** The well-defined separation of concerns between commands, events, and queries promotes clean code and fosters easier reasoning about system behavior.
-   **Visualize Your System:** Gain a bird's-eye view of your application's architecture through intuitive GUIs provided by compatible monitoring tools. These tools can integrate seamlessly with Evento, allowing you to visualize components, event flows, and command execution.
-   **Comprehend Component Behavior:** Delve into the behavior of individual components within your application. Explore event handling, command processing, and query execution at a granular level to identify bottlenecks or unexpected interactions.
-   **Enriched Telemetry:** Evento gathers and exposes detailed telemetry data about your application's performance. This data includes metrics on event delivery, command execution times, and query response latencies. By analyzing this telemetry, you can optimize your application and ensure it delivers exceptional performance.

[![headline](docs/assets/cta.png)](https://www.eventoframework.com/)

**Evento Framework: The Foundation Based on [RECQ Architecture](https://www.eventoframework.com/recq-patterns/)**

The Evento Framework, built upon the principles of the [RECQ (Reactive, Event-Driven Commands and Queries)](https://www.eventoframework.com/recq-patterns/) architecture, provides a comprehensive set of abstractions and APIs to navigate the world of event-driven development. RECQ emphasizes:

-   **Reactive Programming:** Evento embraces reactive principles, promoting responsiveness and elasticity in your applications.
-   **Event-Driven Communication:** Communication within your system primarily happens through events, leading to a loosely coupled and modular architecture.
-   **Command/Query Separation:** Evento adheres to CQRS, ensuring commands and queries flow through distinct channels, enhancing data integrity and performance.

**[Key Features of the Evento Framework](https://docs.eventoframework.com/evento-framework/evento-framework-introcution)**

The Evento Framework serves as the cornerstone of your development experience. It provides a comprehensive set of abstractions and APIs to navigate the world of event-driven development. Here's a glimpse into what you can accomplish with Evento:

-   **[Component-Based Architecture](https://docs.eventoframework.com/evento-framework/component):** Define your application logic through well-structured components annotated with `@Aggregate`, `@Projector`, or `@Saga`. These components encapsulate the core functionalities of your application.
-   **Automatic Discovery:** Evento intelligently scans your bundles (packaged application units) to identify these components, eliminating the need for tedious manual configuration.
-   **Performance Monitoring (Optional):** Gain valuable insights into the performance of your application by leveraging built-in monitoring capabilities.
-   **Flexible Configuration:** Tailored Evento's behavior to your specific project requirements through a rich set of configuration options.

**[Evento Server: The Orchestrator](https://docs.eventoframework.com/evento-server/evento-server-introduction)**

The Evento Server acts as the central nervous system for your distributed application. It performs several critical tasks:

-   **Bundle Management:** The server oversees the registration and discovery of your Evento bundles. It understands how these bundles interact and orchestrates communication between them.
-   **Event Processing:** The server acts as a robust event conduit, ensuring that events are delivered efficiently to the appropriate components within your bundles.
-   **Command Routing:** When commands are issued, the server deftly directs them to the designated command handlers within your bundles for efficient execution.
-   **Query Handling:** Similarly, the server acts as an intermediary for queries, directing them to the corresponding query handlers in your bundles to retrieve the requested data.

**Together, the Evento Framework and its Server form a cohesive ecosystem that simplifies and streamlines the creation of distributed applications. Embrace the Evento philosophy and experience the power of event sourcing and CQRS!**

## Evento 3

**Evento 3 is available**: everything below is in it, and all of it is public.

**Write your domain**
- Seven RECQ components, each an annotated Java class: `@Aggregate`, `@Service`, `@Projector`, `@Projection`, `@Saga`, `@Observer` and invokers. Bundles announce their components to the server on their own.
- Payload classes that can change shape or name without losing their history (`@Revision` and upcasters); several events from one command, stored atomically.
- Plain Java, Spring, Micronaut or Quarkus, and a Spring Boot starter (Boot 4 and 3.5). Java 25 with virtual threads, for the server and the client libraries.

**Correct under failure**
- Exactly-once commands: idempotency keys make a retry safe on whichever server it lands; sagas, projectors and observers derive theirs automatically, and a service can close the last window with an outcome store.
- No forked aggregates: every event carries its aggregate's version under a unique index, so two commands that decided from the same state can never both be stored. Commands run without a lock and retry on the new state when they lose.
- Projector, saga and observer progress on PostgreSQL or MySQL, dead event queues, parallel consumers with per-aggregate ordering.
- Consumers wait for the server instead of polling it: they fetch as soon as events they handle are appended, and an idle system costs the event store almost nothing.

**Run it as a cluster**
- Several servers on one PostgreSQL event store, from a hot standby to a cell per region; bundles fail over when their server dies and fail back when it is healthy again; requests answered close to the caller.
- Highly available consumers: each consumer, per context, in as many replicas as you like; one holds the lane, another takes over when it goes, without losing or repeating an event.
- Correctness lives in the database: servers never call one another.

```java
var bus = new EventoServerMessageBusConfiguration(
        new ClusterNodeAddress("evento-eu", 3030),     // this cell's server first
        new ClusterNodeAddress("evento-am", 3030))
    .withExpectedClusterName("orders-prod");
```

**Query at any size**
- Streamed and paged answers with flow control, even directly bundle to bundle; a bundle answers its own queries in process; read-your-writes with `sendTracked` and `QueryOptions.after(token)`.

**Secure and compliant**
- Secure by default: in production the server requires a bus token, TLS, a payload allowlist, real passwords and explicit CORS origins. Single sign-on and audit trail for the console.
- Personal data encrypted per data subject: deleting the subject's key erases it from the whole history.

**See and measure**
- A console generated from your code: catalog, flows, application graph, what-if performance analysis. OpenTelemetry tracing, Prometheus metrics.

**Test it**
- Fixtures, an in-process broker and a containerised server (`evento-test`, `evento-test-broker`).

See the [documentation](https://docs.eventoframework.com/evento-3) for each of them.

## How Evento is tested

- **Over 1,000 automated tests** on every change, over real TCP connections, against PostgreSQL 16 and 17 and MySQL 8.4, with several complete servers on one database.
- **Nineteen chaos scenarios** on a three-region lab ([`evento-lab-cluster`](evento-lab-cluster)): servers killed, frozen and restarted, a region cut off, database failovers, cold starts, clock skew, retry storms. After each one the lab checks that no acknowledged command is lost, nothing is executed twice, no aggregate forks, the read models converge and the catalog lists every live bundle. Every invariant holds in every scenario ([results](evento-lab-cluster/results/V3-SUMMARY.md)).
- **Rolling upgrades**: servers and bundles of different versions together, in every order, and a running cluster upgraded one process at a time.
- **Highly available consumers** ([`evento-lab-microservices`](evento-lab-microservices)): one consumer per context in three replicas, the replica holding each lane killed twice mid-stream; every event applied exactly once and in order.
- **Real applications**: the full test suites of a marketplace and of a manufacturing-execution system, green on Evento 3.
- **Benchmarks**: about 3,900 commands a second on one 4-core server (p99 about 60 ms); a command round trip of 7 ms (median) on one machine, timed phase by phase, and measured across three regions; what a query answer costs the server, classic or streamed. `QueryPathBenchmarkIT` and `CommandLatencyBenchmarkIT` (`EVENTO_RUN_BENCH=true`) and the harness under [`benchmarks/`](benchmarks) reproduce them.

Details in [How Evento Is Tested](https://docs.eventoframework.com/testing-and-verification).

## Getting Started

To start using Evento Framework, see the [Getting Started documentation](https://docs.eventoframework.com/getting-started/quick-start).

## Documentation

The documentation of Evento Framework includes:

- [Main documentation](https://docs.eventoframework.com/)
- [RECQ Patterns](https://docs.eventoframework.com/recq-patterns/recq-patterns)
- [Tutorial](https://docs.eventoframework.com/getting-started/todolist-recq-tutorial)
- [Evento Framework](https://docs.eventoframework.com/evento-framework/evento-framework-introcution)
- [Evento Server](https://docs.eventoframework.com/evento-server/evento-server-introduction)
- [Evento Server Cluster](https://docs.eventoframework.com/evento-server/evento-server-cluster)
- [How Evento Is Tested](https://docs.eventoframework.com/testing-and-verification)
- [Evento GUI](https://docs.eventoframework.com/evento-gui/explore-recq-systems-visually)

## Repository Resources

- [Contributing guide](CONTRIBUTING.md) — environment setup, tests, branching, commits, and PR process.
- [Security policy](SECURITY.md) — supported versions and responsible disclosure process.
- [Adopters](ADOPTERS.md) — who uses Evento; add your organisation with a pull request.
- [Compatibility policy](COMPATIBILITY.md) — semantic versioning, which server and bundle versions work together, upgrade order, deprecation and support.
- [Support guide](SUPPORT.md) — where to ask questions, report bugs, and ask for commercial authorisations.
- [Governance](GOVERNANCE.md) and [maintainers](MAINTAINERS.md) — project roles, decisions, releases, and ownership.
- [Agent notes](CLAUDE.md) — repository orientation for developer agents and maintainers.

## Community & Author

The Evento Framework is in its early stages, actively evolving with the contributions from the developer community. We need your help to shape Evento into a robust and widely adopted framework for distributed applications. Here's how you can get involved:

-   **Star⭐️ the Repository on GitHub:** Show your support and help us gain visibility! Find the Evento Framework on GitHub: [https://github.com/EventoFramework/evento-framework](https://github.com/EventoFramework/evento-framework)
-   **Follow on LinkedIn:** Stay up-to-date on the latest developments and announcements. Connect with me, the author, on LinkedIn: [Gabor Galazzo](https://www.linkedin.com/in/gabor-galazzo/)
-   **Contribute Code or Documentation:** We welcome contributions of all kinds. If you have an idea or find a bug, don't hesitate to submit a pull request or issue on GitHub.

By joining forces, we can make Evento a valuable tool for building the next generation of distributed applications!

## License
Copyright 2020-2026 © Gabor Galazzo.

From **3.0.0** everything is public, every feature included (single sign-on and audit trail of the
console too): there is no closed edition.

- **Client libraries — Apache-2.0.** `evento-transport-api`, `evento-transport-netty`, `evento-common`,
  `evento-bundle`, `evento-consumer-state-store-jdbc`, `evento-spring-boot-starter`,
  `evento-opentelemetry`, `evento-test`: the jars your application links against, with no obligation
  on it.
- **Server, web console and the rest — Apache-2.0 with the Commons Clause.** Use, change and run
  `evento-server` and `evento-gui` freely, in production, for your own applications, also to sell
  your own products built on them. What needs a written authorisation is **selling Evento itself**:
  offering it as a hosted service, reselling it, or selling consulting, support or training about it
  ([`LICENSE-COMMERCIAL.txt`](LICENSE-COMMERCIAL.txt)).
- **The names Evento and RECQ** are not for commercial offerings without authorisation
  ([`TRADEMARKS.md`](TRADEMARKS.md)); the RECQ ideas are free for anyone to apply.

Details in [`LICENSE.txt`](LICENSE.txt); contributions go through the [CLA](CLA.md).
