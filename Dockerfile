# syntax=docker/dockerfile:1

# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app
ENV MAVEN_USER_HOME=/opt/maven-wrapper

# Keep the Maven installation in the image layer; cache only downloaded dependencies.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2/repository \
    chmod +x mvnw && ./mvnw dependency:go-offline -B -ntp

# Copy source and build the fat jar
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    ./mvnw package -Dmaven.test.skip=true -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
