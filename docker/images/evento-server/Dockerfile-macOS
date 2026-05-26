FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /evento
COPY . .
RUN chmod +x gradlew && ./gradlew :evento-server:bootJar --no-daemon

FROM eclipse-temurin:25-jre-noble

EXPOSE 3000
EXPOSE 3030

RUN mkdir /app

COPY --from=build /evento/evento-server/build/libs/*.jar /app/spring-boot-application.jar

ENTRYPOINT ["java","-jar","/app/spring-boot-application.jar"]