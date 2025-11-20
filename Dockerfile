# ---- BUILD STAGE ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom and pre-download dependencies for caching
COPY pom.xml .
RUN mvn -q -U dependency:go-offline

# Copy the rest of the project
COPY . .

# Build using the WEB profile (uses WebMain as Main-Class)
RUN mvn -q -U -DskipTests package -Pweb

# Sanity check: make sure WebMain.class is in the jar
RUN set -e; \
    echo "=== TARGET CONTENTS ==="; ls -al target; \
    jar tf target/easyread-quoter-1.0.0.jar | grep -q '^com/easyread/WebMain\.class$' && \
      echo 'OK: com/easyread/WebMain.class is in the jar';

# ---- RUNTIME STAGE ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /app/target/easyread-quoter-1.0.0.jar /app/app.jar

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080

CMD ["java","-Djpro.host=${JPRO_HOST}","-Djpro.port=${JPRO_PORT}","-jar","app.jar"]
