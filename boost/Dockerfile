# Build
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install k6
RUN apk add --no-cache ca-certificates && \
    wget https://github.com/grafana/k6/releases/download/v0.51.0/k6-v0.51.0-linux-amd64.tar.gz && \
    tar -xzf k6-v0.51.0-linux-amd64.tar.gz && \
    mv k6-v0.51.0-linux-amd64/k6 /usr/bin/k6 && \
    rm -rf k6-v0.51.0-linux-amd64.tar.gz k6-v0.51.0-linux-amd64

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/target/*.jar app.jar

# Tạo thư mục chứa scripts k6
RUN mkdir -p /app/k6/scripts && chown -R spring:spring /app/k6

USER spring:spring

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
