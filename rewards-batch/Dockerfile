# Build from repo root: docker build -f rewards-batch/Dockerfile .
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY rewards-common ./rewards-common
COPY boost ./boost
COPY rewards-batch ./rewards-batch

RUN mvn -B -pl rewards-common,rewards-batch -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/rewards-batch/target/*.jar app.jar

USER spring:spring

EXPOSE 8081

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
