FROM eclipse-temurin:25-jdk-noble@sha256:2e6f25a996cdbd9344d03372b7195e7f159a1c83a2199da4102e164ebb2b2edd AS build
WORKDIR /evento
COPY . .
RUN chmod +x gradlew && ./gradlew :evento-server:bootJar --no-daemon

FROM eclipse-temurin:25-jre-noble@sha256:af7b616f7f2c23bcff0bf706daf8aaab088c6f1843fdab637dcc6bd70ae573c3

EXPOSE 3000
EXPOSE 3030

RUN mkdir /app

COPY --from=build /evento/evento-server/build/libs/*.jar /app/spring-boot-application.jar

ENTRYPOINT ["java","-XX:+ExitOnOutOfMemoryError","-jar","/app/spring-boot-application.jar"]