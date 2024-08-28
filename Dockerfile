FROM armdocker.rnd.ericsson.se/proj-orchestration-so/so-base:1.0.0-17

ENV JAVA_OPTS ""
# It's needed for self-sign certificate solution.
ENV CACERT_PATH=""
ENV CACERT_NAME=""
ENV DEFAULT_JAVA_CERTS="/usr/lib64/jvm/java-1.8.0-openjdk-1.8.0/jre/lib/security/cacerts"

VOLUME /tmp

COPY ./target/eso-api-gateway-1.0.0-SNAPSHOT.jar eso-api-gateway.jar
COPY entryPoint.sh /entryPoint.sh

CMD ["sh", "-c","/entryPoint.sh"]