# ── Build stage ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper + pom first so dependency layers are cached
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Extract layered JAR for optimal Docker layer caching
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ── Runtime stage ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy layered JAR layers (most-stable first for cache efficiency)
COPY --from=builder /workspace/target/extracted/dependencies/           ./
COPY --from=builder /workspace/target/extracted/spring-boot-loader/    ./
COPY --from=builder /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/extracted/application/           ./

# Health check via Spring Boot Actuator liveness probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
