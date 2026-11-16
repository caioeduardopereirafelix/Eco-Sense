FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/ecosense-backend-0.1.0.jar app.jar
COPY index.html login.html ./web/
COPY admin ./web/admin
COPY js ./web/js
COPY css ./web/css
ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/web/
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
