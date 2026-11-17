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

# Build the JAR (skip tests in CI — tests run in a separate pipeline stage)
RUN gradle bootJar --no-daemon -x test

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user and pre-create log directory with full permissions
RUN addgroup -S curamatrix && adduser -S curamatrix -G curamatrix \
    && mkdir -p /app/logs \
    && chown -R curamatrix:curamatrix /app

# Copy JAR from builder stage with proper ownership
COPY --from=builder --chown=curamatrix:curamatrix /app/build/libs/*.jar app.jar

USER curamatrix

# Expose default application port (Render dynamically overrides this with $PORT)
EXPOSE 8080

# Health check dynamically targeting $PORT (falls back to 8080)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT:-8080}/actuator/health || exit 1

# JVM tuning for containers (safe for Render 512MB free tier to prevent OOM kills)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -XX:InitialRAMPercentage=40.0 -XX:+ExitOnOutOfMemoryError"

# Explicitly bind to 0.0.0.0 and dynamically accept $PORT assigned by Render
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080} --server.address=0.0.0.0"]
