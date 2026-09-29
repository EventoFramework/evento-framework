# S15 (clock skew): a lab image plus libfaketime, so one JVM can run with its clock skewed (behind: the staleness gate trips; ahead: harmless).
#
#   docker build -f docker/faketime/Dockerfile --build-arg BASE=evento-lab-cluster/order-query:before \
#     -t evento-lab-cluster/order-query:before-faketime docker/faketime
#   S15_IMAGE=evento-lab-cluster/order-query:before-faketime \
#   S15_FAKETIME_ENV='LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1,FAKETIME=-40s,FAKETIME_DONT_FAKE_MONOTONIC=1' \
#     scripts/run-scenario.sh S15ClockSkewTest before
#
# FAKETIME_DONT_FAKE_MONOTONIC=1 keeps System.nanoTime real (JVM timers, Netty); only the wall
# clock (System.currentTimeMillis, Instant.now) is skewed, which is what the fetch staleness
# gate compares.
ARG BASE
FROM ${BASE}
RUN apt-get update \
 && apt-get install -y --no-install-recommends libfaketime \
 && rm -rf /var/lib/apt/lists/* \
 && ls /usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1
