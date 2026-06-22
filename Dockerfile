FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

FROM grafana/k6:0.54.0 AS k6

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar app.jar
COPY --from=k6 /usr/bin/k6 /usr/local/bin/k6
COPY load-test-sync.js load-test-buffered-async.js ./

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
