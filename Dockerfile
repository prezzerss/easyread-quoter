# syntax=docker/dockerfile:1

# ---- BUILD ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .

# Compile and copy runtime deps into target/deps
RUN mvn -B -U -DskipTests package dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=target/deps \
 && echo "=== TARGET LAYOUT ===" \
 && ls -al target target/deps || true

# ---- RUNTIME ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# App classes + dependency jars
COPY --from=builder /app/target/classes /app/classes
COPY --from=builder /app/target/deps /app/deps

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080

# Run by class name (no manifest needed)
CMD sh -c 'java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -cp "/app/classes:/app/deps/*" com.easyread.MainApp'




