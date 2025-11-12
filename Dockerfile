# syntax=docker/dockerfile:1

# ---- BUILD ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy everything in the build context (repo root)
COPY . .

# Build (use wrapper if you added it; otherwise mvn is fine here)
# If you have mvnw in the repo, use: RUN ./mvnw -B -U -DskipTests package
RUN mvn -B -U -DskipTests package

# ---- RUNTIME ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# If you make a shaded jar, keep the first line; otherwise use the second.
COPY --from=builder /app/target/*-shaded.jar /app/app.jar
# COPY --from=builder /app/target/*.jar /app/app.jar
EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080
CMD sh -c "java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -jar /app/app.jar"



