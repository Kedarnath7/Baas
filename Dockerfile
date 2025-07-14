FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/BaaS-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar

COPY src/main/resources/config.properties /app/config.properties

RUN mkdir -p /app/data /app/snapshots

EXPOSE 6565

CMD ["java", "-jar", "app.jar"]
