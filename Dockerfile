# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app

# Copy Maven wrapper and pom first — Docker caches this layer until pom changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build the fat jar
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
