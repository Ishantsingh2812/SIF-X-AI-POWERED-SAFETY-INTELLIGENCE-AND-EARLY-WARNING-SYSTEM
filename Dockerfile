FROM eclipse-temurin:21-jdk AS build
WORKDIR /app/backend
COPY backend/ .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
