FROM eclipse-temurin:17.0.20_8-jre
WORKDIR /app
COPY ./build/libs/specmatic-kafka-json-schema-sample.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
