# syntax=docker/dockerfile:1

# ---- BUILD ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy wrapper first so dependency cache survives source changes
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .

# Cache the local repo to speed up downloads
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -U -e -DskipTests dependency:go-offline

# Now copy sources and build
COPY src ./src
COPY templates.json ./templates.json

# Give Maven more heap (Render builder is memory-tight sometimes)
ENV MAVEN_OPTS="-Xmx1024m"

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -U -e -DskipTests package

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



