FROM maven:3.9-eclipse-temurin-17@sha256:32ce79e40d744b18c6ce9fe65d6b58189cbabc938cfeff657a534a359a5d3f92 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre@sha256:0d79988c68791ce864fe39d149ab1dc84f680539dca77ee7f6f3b041ad7f2f43
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/obs-api-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
