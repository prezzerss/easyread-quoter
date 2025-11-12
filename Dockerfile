# syntax=docker/dockerfile:1

# ---- BUILD ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# copy everything (simple & robust)
COPY . .

# build and force final name to 'app'
RUN mvn -B -U -DskipTests -Dproject.build.finalName=app package

# show what's inside so Render logs reveal the actual classes + manifest
RUN echo "=== TARGET LIST ===" && ls -al target && \
    echo "=== MANIFEST ===" && (jar xf target/app-shaded.jar META-INF/MANIFEST.MF || true) && \
    (cat META-INF/MANIFEST.MF || true) && \
    echo "=== CLASSES PRESENT? ===" && \
    jar tf target/app-shaded.jar | grep -E "Main(App)?\.class|com/easyread" || true

# ---- RUNTIME ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/app-shaded.jar /app/app.jar

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080

CMD sh -c "java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -jar /app/app.jar"
