FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY backend ./backend
RUN mvn -f backend/pom.xml -pl finscope-web -am package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/backend/finscope-web/target/finscope-web-0.1.0-SNAPSHOT.jar /app/finscope.jar

ENV FINSCOPE_DATA_ROOT=/data \
    SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/finscope.jar"]
