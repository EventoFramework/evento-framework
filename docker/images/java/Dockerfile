# Generic "download a jar and run it" base. Was openjdk:19, which is EOL and no
# longer published on Docker Hub; migrated to the digest-pinned Temurin 25 JRE
# used elsewhere in this repo. curl is required by entrypoint.sh.
FROM eclipse-temurin:25-jre-noble@sha256:af7b616f7f2c23bcff0bf706daf8aaab088c6f1843fdab637dcc6bd70ae573c3
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /app
COPY ./entrypoint.sh /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]
