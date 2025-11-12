
# ---------- BUILD STAGE: uses Maven + JDK to compile and package 
----------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# copy only pom first to cache dependencies between builds
COPY pom.xml .
RUN mvn -q -U dependency:go-offline

# now copy the rest of the source and build
COPY . .
# package the app (jpro plugin will produce a runnable jar if configured)
RUN mvn -q -U -DskipTests package

# ---------- RUNTIME STAGE: lightweight JDK image to run the jar 
----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# copy the fat/runnable jar from the builder
# if your shaded jar has a different name, Render logs will show it — 
adjust below.
COPY --from=builder /app/target/*-shaded.jar /app/app.jar
# Fallback: if you don’t produce a shaded jar, copy the normal jar:
# COPY --from=builder /app/target/*.jar /app/app.jar

# JPro listens on 8080 by default
EXPOSE 8080

# Make sure the server binds to 0.0.0.0 inside Render
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080

# Run it. If your jar already embeds JPro’s web server, this starts it.
CMD ["java", "-Djpro.host=${JPRO_HOST}", "-Djpro.port=${JPRO_PORT}", 
"-jar", "app.jar"]


