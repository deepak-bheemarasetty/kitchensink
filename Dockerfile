FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY config config
COPY src src
RUN mvn -B verify

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/kitchensink.jar app.jar
EXPOSE 9080
ENTRYPOINT ["java", "-jar", "app.jar"]
