# ==============================================================================
# HireFlow — Production Unified Single-Origin Dockerfile
# Architecture: Multi-stage build producing single Spring Boot + React 19 artifact
# Base: Node 22 (Vite Build) + Eclipse Temurin 21 (JDK & JRE)
# Security: Non-root user, minimal runtime footprint, no baked credentials
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build React 19 Frontend
# ------------------------------------------------------------------------------
FROM node:22-alpine AS frontend-builder
WORKDIR /app/frontend

COPY hireflow-app/package.json hireflow-app/package-lock.json ./
RUN npm ci

COPY hireflow-app/ ./
RUN npm run build

# ------------------------------------------------------------------------------
# Stage 2: Build Spring Boot Backend with Embedded Frontend
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS backend-builder
WORKDIR /app

# Cache Maven Wrapper and POM descriptors
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY hireflow-api/pom.xml hireflow-api/
RUN chmod +x mvnw

# Resolve dependencies
RUN ./mvnw -B dependency:resolve-plugins dependency:resolve -q || true

# Copy source trees
COPY src/ src/
COPY hireflow-api/src/ hireflow-api/src/

# Copy built frontend assets into Spring Boot static resources
COPY --from=frontend-builder /app/frontend/dist/ hireflow-api/src/main/resources/static/

# Compile root module classes and package Spring Boot executable JAR
RUN ./mvnw -B install -DskipTests -q && \
    ./mvnw -B -f hireflow-api/pom.xml clean package -DskipTests -q

# ------------------------------------------------------------------------------
# Stage 3: Production Runtime (Single Origin)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runner

# Install curl for healthchecks
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Non-root user for principle of least privilege
RUN groupadd -g 10001 hireflow && \
    useradd -u 10001 -g hireflow -m -s /bin/sh hireflow

WORKDIR /app

COPY --from=backend-builder --chown=hireflow:hireflow /app/hireflow-api/target/hireflow-api-1.0.0.jar /app/hireflow.jar

USER hireflow:hireflow

# Render, Cloud Run, and container platforms inject $PORT (default 8080)
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=40.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://127.0.0.1:${PORT:-8080}/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/hireflow.jar --server.port=${PORT:-8080} --server.address=0.0.0.0"]
