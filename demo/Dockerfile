# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache dumb-init
RUN addgroup -g 1000 java && adduser -u 1000 -G java -s /bin/sh -D java
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
USER java:java
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "java -XX:+UseSerialGC -XX:MaxRAMPercentage=65.0 -Xss512k -Dserver.port=${PORT} -jar app.jar"]
