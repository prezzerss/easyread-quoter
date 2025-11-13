# syntax=docker/dockerfile:1

# ---- BUILD ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn -B -U -DskipTests package
RUN set -e; \
    echo "=== TARGET CONTENTS ==="; ls -al target; \
    JAR="$(ls target/*-shaded.jar 2>/dev/null || ls target/*.jar | head -n1)"; \
    echo "Selected JAR: $JAR"; \
    cp "$JAR" /app/app.jar; \
    echo "=== MANIFEST ==="; \
    (jar xf /app/app.jar META-INF/MANIFEST.MF && grep -i '^Main-Class' META-INF/MANIFEST.MF || true)

# ---- RUNTIME ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/app.jar /app/app.jar
EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080
CMD sh -c "java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -jar /app/app.jar"






