FROM maven:3.9.11-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /app
COPY pom.xml .
COPY request-service ./request-service
COPY notification-service ./notification-service
RUN mvn -B -pl "$SERVICE" package && cp "$SERVICE/target/app.jar" /app/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar .
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
