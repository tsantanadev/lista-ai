# Read Me First
The following was discovered as part of building this project:

* The JVM level was changed from '25' to '24' as the Kotlin version does not support Java 25 yet.

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.0/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.0/gradle-plugin/packaging-oci-image.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.0.0/reference/using/devtools.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/4.0.0/reference/features/dev-services.html#features.dev-services.docker-compose)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.0/reference/web/servlet.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.0/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Liquibase Migration](https://docs.spring.io/spring-boot/4.0.0/how-to/data-initialization.html#howto.data-initialization.migration-tool.liquibase)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.0.0/reference/actuator/index.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)

### Docker Compose support
This project contains a Docker Compose file named `compose.yaml`.
In this file, the following services have been defined:

* postgres: [`postgres:latest`](https://hub.docker.com/_/postgres)

Please review the tags of the used images and set them to the same as you're running in production.

