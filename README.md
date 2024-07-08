[![headline](docs/assets/headline.png)](https://www.eventoframework.com/)

# [Evento Framework](https://www.eventoframework.com/)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/license/mit)
![Code style](https://img.shields.io/badge/code_style-prettier-ff69b4.svg)
[![](https://img.shields.io/github/v/release/EventoFramework/evento-framework)](https://github.com/EventoFramework/evento-framework/releases)
[![](https://img.shields.io/maven-central/v/com.eventoframework/evento-bundle?logo=gradle)](https://central.sonatype.com/artifact/com.eventoframework/evento-bundle)
[![](https://img.shields.io/docker/v/eventoframework/evento-server?logo=docker&label=image)
](https://hub.docker.com/r/eventoframework/evento-server)


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

## Getting Started

To start using Evento Framework, see the [Getting Started documentation](https://docs.eventoframework.com/getting-started/quick-start).

## Documentation

The documentation of Evento Framework includes:

- [Main documentation](https://docs.eventoframework.com/)
- [RECQ Patterns](https://docs.eventoframework.com/recq-patterns/recq-patterns)
- [Tutorial](https://docs.eventoframework.com/getting-started/todolist-recq-tutorial)
- [Evento Framework](https://docs.eventoframework.com/evento-framework/evento-framework-introcution)
- [Evento Server](https://docs.eventoframework.com/evento-server/evento-server-introduction)
- [Evento GUI](https://docs.eventoframework.com/evento-gui/explore-recq-systems-visually)
- [Evento CLI](https://docs.eventoframework.com/evento-cli/evento-cli-introduction)

## Community & Author

The Evento Framework is in its early stages, actively evolving with the contributions from the developer community. We need your help to shape Evento into a robust and widely adopted framework for distributed applications. Here's how you can get involved:

-   **Star⭐️ the Repository on GitHub:** Show your support and help us gain visibility! Find the Evento Framework on GitHub: [https://github.com/EventoFramework/evento-framework](https://github.com/EventoFramework/evento-framework)
-   **Follow on LinkedIn:** Stay up-to-date on the latest developments and announcements. Connect with me, the author, on LinkedIn: [Gabor Galazzo](https://www.linkedin.com/in/gabor-galazzo/)
-   **Contribute Code or Documentation:** We welcome contributions of all kinds. If you have an idea or find a bug, don't hesitate to submit a pull request or issue on GitHub.

By joining forces, we can make Evento a valuable tool for building the next generation of distributed applications!

## License
Copyright 2020-2024 © Gabor Galazzo. All rights reserved.

Licensed under the MIT Licence: http://www.apache.org/licenses/MIT