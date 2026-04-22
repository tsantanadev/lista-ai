FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY src/ src/
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
