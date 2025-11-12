# syntax=docker/dockerfile:1

# ---- BUILD ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .
RUN mvn -B -U -DskipTests package

# Grab a shaded jar if present; otherwise any jar. Rename to app.jar
RUN ls -al target && \
    (cp target/*-shaded.jar app.jar || cp target/*.jar app.jar)

# ---- RUNTIME ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/app.jar /app/app.jar

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080
CMD sh -c "java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -jar /app/app.jar"

