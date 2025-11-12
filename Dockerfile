# syntax=docker/dockerfile:1

# ---- BUILD STAGE ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Cache deps
COPY server/pom.xml .
RUN mvn -q -B -U dependency:go-offline

# Build
COPY server/. .
RUN mvn -q -B -U -DskipTests package

# ---- RUNTIME STAGE ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# If you create a shaded/fat jar, this will copy it.
# If not, change the next line to: COPY --from=builder /app/target/*.jar /app/app.jar
COPY --from=builder /app/target/*-shaded.jar /app/app.jar

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080

# Use shell so env vars expand
CMD sh -c "java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -jar /app/app.jar"



