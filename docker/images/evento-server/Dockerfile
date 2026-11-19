# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk-noble@sha256:2e6f25a996cdbd9344d03372b7195e7f159a1c83a2199da4102e164ebb2b2edd AS build
WORKDIR /evento
COPY . .
RUN chmod +x gradlew && ./gradlew :evento-server:bootJar --no-daemon

# CVE-2026-114938 (Java SE compiler, CVSS 8.1): fixed in Temurin 25.0.5.1+2 (2026-11-18).
# The eclipse-temurin images do not carry it yet, so the runtime is replaced with Adoptium's
# JRE, checked by SHA-256. Adoptium has published it for x64 only so far: arm64 keeps the
# base image's runtime until its build is out. Drop this stage once the base carries it.
FROM eclipse-temurin:25-jre-noble@sha256:af7b616f7f2c23bcff0bf706daf8aaab088c6f1843fdab637dcc6bd70ae573c3 AS jre
ADD --checksum=sha256:2edef9e4f81ec5ee426d023336d4cfbd7b4644e99884e7a73a6a867b70b17acf     https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5.1%2B2/OpenJDK25U-jre_x64_linux_hotspot_25.0.5.1_2.tar.gz /tmp/jre-x64.tar.gz
RUN if [ "$(uname -m)" = "x86_64" ]; then mkdir /jre && tar -xzf /tmp/jre-x64.tar.gz -C /jre --strip-components=1;     else cp -a /opt/java/openjdk /jre; fi && rm /tmp/jre-x64.tar.gz

FROM eclipse-temurin:25-jre-noble@sha256:af7b616f7f2c23bcff0bf706daf8aaab088c6f1843fdab637dcc6bd70ae573c3

RUN rm -rf /opt/java/openjdk
COPY --from=jre /jre /opt/java/openjdk

EXPOSE 3000
EXPOSE 3030

RUN mkdir /app

COPY --from=build /evento/evento-server/build/libs/*.jar /app/spring-boot-application.jar

ENTRYPOINT ["java","-XX:+ExitOnOutOfMemoryError","-jar","/app/spring-boot-application.jar"]