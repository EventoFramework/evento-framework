# Generic "download a jar and run it" base. Was openjdk:19, which is EOL and no
# longer published on Docker Hub; migrated to the digest-pinned Temurin 25 JRE
# used elsewhere in this repo. curl is required by entrypoint.sh.
FROM eclipse-temurin:25-jre-noble@sha256:b27ca47660a8fa837e47a8533b9b1a3a430295cf29ca28d91af4fd121572dc29
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /app
COPY ./entrypoint.sh /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]
