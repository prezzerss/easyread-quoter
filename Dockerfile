# syntax=docker/dockerfile:1

# ---- BUILD ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .
RUN mvn -B -U -DskipTests package

# Pick a jar (prefer shaded) and normalize to /app/app.jar
RUN set -e; \
    echo "=== TARGET CONTENTS ==="; ls -al target; \
    JAR="$(ls target/*-shaded.jar 2>/dev/null || ls target/*.jar | head -n1)"; \
    echo "Selected JAR: $JAR"; \
    cp "$JAR" /app/app.jar; \
    echo "=== MANIFEST ==="; \
    (jar xf /app/app.jar META-INF/MANIFEST.MF && cat META-INF/MANIFEST.MF) || true; \
    echo "=== LOOK FOR MainApp.class ==="; \
    jar tf /app/app.jar | grep -i 'MainApp.class' || true; \
    echo "=== LIST ALL com/easyread classes ==="; \
    jar tf /app/app.jar | grep '^com/easyread/' | head -n 100 || true

# ---- RUNTIME ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/app.jar /app/app.jar

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080

# Use -jar (manifest) by default
# Replace CMD with:
CMD sh -c 'java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -cp "/app/app.jar:/app/lib/*" com.easyread.MainApp'



