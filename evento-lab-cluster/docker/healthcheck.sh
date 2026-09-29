#!/bin/bash
# HTTP health probe for the JVM containers. eclipse-temurin:25-jre ships neither curl nor
# wget, so this speaks HTTP/1.0 over bash's /dev/tcp.
#
#   healthcheck.sh PORT PATH      exit 0 on an HTTP 200, 1 otherwise
port="${1:-8080}"
path="${2:-/actuator/health}"
exec 3<>"/dev/tcp/127.0.0.1/${port}" || exit 1
printf 'GET %s HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n' "$path" >&3
IFS= read -r -t 5 status <&3 || exit 1
case "$status" in
  "HTTP/1."?" 200"*) exit 0 ;;
  *) exit 1 ;;
esac
