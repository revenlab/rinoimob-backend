FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S rinoimob && adduser -S rinoimob -G rinoimob

COPY --from=build /workspace/target/*.jar /app/app.jar

ENV SERVER_PORT=39000
EXPOSE 39000

USER rinoimob

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
