# ---- Build stage: compile and test with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B clean package

# ---- Runtime stage: only the JRE and the jar ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/lambda-webframework.jar app.jar

# Safe production defaults; the platform can override them.
ENV PORT=8080 \
    APP_ENV=production \
    GREETING_PREFIX=Hello \
    WORKER_THREADS=8 \
    SHUTDOWN_TIMEOUT_SECONDS=5

EXPOSE 8080
# Exec form: java is PID 1 and receives the SIGTERM sent by "docker stop",
# which triggers the framework's graceful shutdown (well within Docker's 10 s grace period).
ENTRYPOINT ["java", "-jar", "app.jar"]
