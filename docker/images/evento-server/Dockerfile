FROM gradle:8.6-jdk21-alpine AS build
COPY --chown=gradle:gradle . /evento
WORKDIR /evento
RUN gradle :evento-server:bootJar --no-daemon

FROM openjdk:20

EXPOSE 3000
EXPOSE 3030

RUN mkdir /app

COPY --from=build /evento/evento-server/build/libs/*.jar /app/spring-boot-application.jar

ENTRYPOINT ["java","-jar","/app/spring-boot-application.jar"]