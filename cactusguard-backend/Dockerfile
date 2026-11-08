# Build local: mvn -DskipTests package && docker build -t cactusguard .
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S cactus && adduser -S cactus -G cactus

COPY target/cactusguard-0.0.1-SNAPSHOT.jar app.jar

USER cactus

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
