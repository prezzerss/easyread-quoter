# syntax=docker/dockerfile:1

# ---- BUILD ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .

# Build and copy runtime deps into target/lib
RUN mvn -B -U -DskipTests package dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=target/lib \
 && echo "=== TARGET CONTENTS ===" \
 && ls -al target target/lib || true \
 && echo "=== CLASSES PREVIEW ===" \
 && find target/classes -maxdepth 3 -type f -name '*.class' | head -n 40 || true

# ---- RUNTIME ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy compiled classes + dependency jars
COPY --from=builder /app/target/classes /app/classes
COPY --from=builder /app/target/lib /app/lib

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080

# 👇 REPLACE com.easyread.WebMain with the fully-qualified class name you found in step A
CMD sh -c 'java -Djpro.host=$JPRO_HOST -Djpro.port=$JPRO_PORT -cp "/app/classes:/app/lib/*" com.easyread.WebMain'







