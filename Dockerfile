FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -q -U dependency:go-offline
COPY . .
RUN mvn -q -U -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/app.jar /app/app.jar

EXPOSE 8080
ENV JPRO_HOST=0.0.0.0
ENV JPRO_PORT=8080

CMD ["java","-Djpro.host=${JPRO_HOST}","-Djpro.port=${JPRO_PORT}","-jar","app.jar"]



