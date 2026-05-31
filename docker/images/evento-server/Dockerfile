FROM eclipse-temurin:25-jdk-noble@sha256:29d2d8af5d12f9ee7aec18f4fb2cd8bc8e6501b748ac62631acd31c867cfa262 AS build
WORKDIR /evento
COPY . .
RUN chmod +x gradlew && ./gradlew :evento-server:bootJar --no-daemon

FROM eclipse-temurin:25-jre-noble@sha256:b27ca47660a8fa837e47a8533b9b1a3a430295cf29ca28d91af4fd121572dc29

EXPOSE 3000
EXPOSE 3030

RUN mkdir /app

COPY --from=build /evento/evento-server/build/libs/*.jar /app/spring-boot-application.jar

ENTRYPOINT ["java","-jar","/app/spring-boot-application.jar"]