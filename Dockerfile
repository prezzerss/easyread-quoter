# syntax=docker/dockerfile:1

# ---- BUILD ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy everything (simple & robust)
COPY . .

# Build (add -DskipTests to speed builds)
RUN mvn -B -U -DskipTests package

# Pick the built JAR (prefer *-shaded.jar if present), then normalize to /app/app.jar
RUN set -e; \
    ls -al target; \
    JAR="$(ls target/*-shaded.jar 2>/dev/null || ls target/*.jar | head -n1)"; \
    echo "Selected JAR: $JAR"; \
    cp "$JAR" /app/app.jar; \
    echo "=== MANIFEST ==="; \
    (jar xf /app/app.jar META-INF/MANIFEST.MF && cat META-INF/MANIFEST.MF) || true

# ---- RUNTIME ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the normalized JAR from the builder
COPY --from=builder /app/app.jar /app/app.jar

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080

CMD sh -c "java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -jar /app/app.jar"

