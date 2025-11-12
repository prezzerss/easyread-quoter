# syntax=docker/dockerfile:1

# ---- BUILD STAGE ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy everything (keeps it simple for now)
COPY . .

# Use cache for ~/.m2 if supported; print full errors (-e) and debug (-X)
# Remove -q so we can SEE the failure cause.
RUN --mount=type=cache,target=/root/.m2 mvn -B -U -e -X -DskipTests package

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



