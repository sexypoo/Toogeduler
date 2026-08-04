FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN chmod +x mvnw && ./mvnw -B -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S toogeduler && adduser -S toogeduler -G toogeduler
COPY --from=build /workspace/target/*.jar app.jar
USER toogeduler
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]
