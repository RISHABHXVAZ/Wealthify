# Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

# Run the application
FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system --gid 1001 wealthify && \
    useradd --system --uid 1001 --gid wealthify --no-create-home --shell /usr/sbin/nologin wealthify

WORKDIR /app

COPY --from=build /target/backend-0.0.1-SNAPSHOT.jar app.jar

USER wealthify:wealthify

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
