# One runtime image for every JVM of the benchmark. The context is a staging directory made by
# scripts/build.sh: app/ holds either one boot jar (app/app.jar) or an installed distribution
# (app/lib/*.jar with MAIN set), nothing is compiled inside the image.
FROM eclipse-temurin:25-jre

WORKDIR /app
COPY app/ /app/

ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -Dorg.slf4j.simpleLogger.defaultLogLevel=warn"
ENV MAIN=""
ENTRYPOINT ["sh", "-c", "if [ -n \"$MAIN\" ]; then exec java $JAVA_OPTS -cp '/app/lib/*' $MAIN; else exec java $JAVA_OPTS -jar /app/app.jar; fi"]
