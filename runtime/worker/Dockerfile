FROM eclipse-temurin:21.0.11_10-jre-jammy@sha256:8208b3a3733529f42c4b565bfcbf16d914eb628f3ead8488b6b4d6363fcbe7cf
WORKDIR /opt/hhc
COPY --chown=10001:0 runtime/worker/target/runtime-worker-0.1.0-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/opt/hhc/app.jar"]
