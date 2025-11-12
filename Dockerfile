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
# copy all jars out of /target
COPY --from=builder /app/target/*.jar /app/
# if your build also outputs a /target/lib folder with deps, copy that too:
# COPY --from=builder /app/target/lib /app/lib

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080
# CMD sh -c "java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -jar /app/app.jar"
CMD sh -c 'java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -cp "/app/*:/app/lib/*" com.easyread.MainApp'
