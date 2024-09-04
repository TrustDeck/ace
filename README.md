# ACE - Advanced Confidentiality Engine

[![Version](https://img.shields.io/badge/version-v0.54.0--BETA-green)](https://github.com/TrustDeck/ace/releases/tag/v0.54.0-BETA)
[![License](https://img.shields.io/badge/License-Apache_2.0-green)](https://github.com/TrustDeck/ace/blob/main/LICENSE)

This service provides a robust solution for pseudonymization and features high scalability and an integrated audit trail. Its architecture enables the creation of domain hierarchies with inheritable properties that provide both configuration and customization capabilities. Complemented by a modern REST interface and state-of-the-art web technologies, ACE is well suited for the use by trusted third party personnel as well as integration into data processing pipelines. It is built with Java and SpringBoot, the service integrates with Keycloak for authentication, HikariCP for efficient database connection pooling, jOOQ for type-safe SQL query construction, and PostgreSQL as the backend database.

## Features

- Pseudonymize sensitive data in real-time
- Secure user authentication using Keycloak
- High scalability by using PostgreSQL with HikariCP
- Type-safe SQL queries with jOOQ

## Prerequisites

- Java 21 or later
- Maven
- PostgreSQL 15.3 or later
- Keycloak 23.0.5 or later
- jOOQ

## Setup & Installation

Please refer to the official [documentation](https://github.com/TrustDeck/ace-docs) for details on how to setup and run ACE.

## Benchmarking ACE

Please refer to the [benchmarking repository](https://github.com/TrustDeck/ace-benchmark) for details and code.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
