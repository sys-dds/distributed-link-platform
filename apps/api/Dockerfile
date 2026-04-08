FROM maven:3.9.14-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./apps/api/mvnw && ./apps/api/mvnw -f ./apps/api/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/apps/api/target/link-platform-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
