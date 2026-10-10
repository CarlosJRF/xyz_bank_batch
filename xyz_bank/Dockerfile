# ---- Build stage: compile and package the Spring Boot jar with Maven + JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Dependencies first: this layer is reused while pom.xml does not change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests need a live PostgreSQL, so they are not run while building the image.
RUN mvn -B -q package -DskipTests \
    && cp target/*.jar app.jar

# ---- Runtime stage: Java 21 JRE only, non-root user ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER spring
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# A batch job, not a server: it runs masterJob once and the container exits with the job status.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
