# Generic runtime image for the lab JVMs (evento-server and every order-* bundle).
#
# The build context is a staging directory prepared by scripts/build-images.sh that holds
# exactly two files: the boot jar (explicit name, never a glob -- see L1 in
# .claude/CLUSTER-READINESS.md) and healthcheck.sh. Nothing is compiled inside the image.
#
#   docker build -f docker/Dockerfile --build-arg JAR=evento-server-2.4.6.jar -t ... <context>
FROM eclipse-temurin:25-jre

ARG JAR
RUN test -n "${JAR}" || (echo "build-arg JAR is required" >&2; exit 1)

WORKDIR /app
COPY ${JAR} /app/app.jar
COPY healthcheck.sh /app/healthcheck.sh
RUN chmod 0755 /app/healthcheck.sh

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
