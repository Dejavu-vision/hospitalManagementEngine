# ============================================================
# CuraMatrix HSM - Multi-Stage Dockerfile
# Stage 1: Build with Gradle
# Stage 2: Run with minimal JRE
# ============================================================

# ---- Stage 1: Build ----
FROM gradle:8.5-jdk17-alpine AS builder

WORKDIR /app

# Git info build args (passed from GitHub Actions)
ARG GIT_COMMIT=unknown
ARG GIT_BRANCH=unknown

# Copy dependency files first (layer caching)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Download dependencies (cached unless build.gradle changes)
RUN gradle dependencies --no-daemon || true

# Copy source code and a minimal .git for git-properties plugin
COPY src ./src
COPY .git ./.git

# Build the JAR (skip tests in CI — tests run in a separate pipeline stage)
RUN gradle bootJar --no-daemon -x test

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S curamatrix && adduser -S curamatrix -G curamatrix

# Copy JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Set ownership
RUN chown curamatrix:curamatrix app.jar

USER curamatrix

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
