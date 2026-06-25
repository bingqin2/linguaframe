FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml pom.xml
COPY LinguaFrame/pom.xml LinguaFrame/pom.xml
COPY LinguaFrame/src LinguaFrame/src

RUN mvn -pl LinguaFrame -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/LinguaFrame/target/LinguaFrame-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
