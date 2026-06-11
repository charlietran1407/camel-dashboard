# =============================================================================
# Stage 1: Build Frontend (Vue 3 + Vite)
# Output: src/main/resources/static/ (served as Spring Boot static resources)
# =============================================================================
FROM node:24.16.0-trixie-slim AS frontend-builder

# Install pnpm
RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /app/frontend

# Copy package files first for better layer caching
COPY frontend/package.json frontend/pnpm-lock.yaml ./

# Allow build scripts for packages like esbuild (required in pnpm v10+)
ENV PNPM_CONFIG_DANGEROUSLY_ALLOW_ALL_BUILDS=true

# Install dependencies (frozen lockfile for reproducible builds)
RUN pnpm install --frozen-lockfile

# Copy all frontend source
COPY frontend/ ./

# Build: output goes to ../src/main/resources/static (relative to frontend/)
# We need to copy sources to the right relative path first
WORKDIR /app
COPY src/main/resources/ src/main/resources/

WORKDIR /app/frontend
RUN pnpm run build
# After build, ../src/main/resources/static is populated


# =============================================================================
# Stage 2: Build Backend (Maven)
# =============================================================================
FROM eclipse-temurin:25-jdk-alpine AS backend-builder

# Install Maven
#RUN apk add --no-cache maven

WORKDIR /app

COPY . .

# Copy the frontend build output (static files) from stage 1
COPY --from=frontend-builder /app/src/main/resources/static/ src/main/resources/static/

RUN chmod +x ./mvnw

# Build with prod profile → thin JAR (no compile-scope extras)
# -Pprod activates prod profile (provided-scope libs loaded from libs/)
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -Pprod -DskipTests -B -q

# =============================================================================
# Stage 3: Runtime Image
# =============================================================================
FROM eclipse-temurin:25-jdk-alpine AS runtime

LABEL maintainer="TKC"
LABEL description="Apache Camel Dashboard - Spring Boot + Vue 3 SPA"

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Create required runtime directories and set ownership
RUN mkdir -p libs camel-routes-storage logs \
    && chown -R appuser:appgroup /app

# Copy the fat JAR from build stage with the correct owner directly
COPY --from=backend-builder --chown=appuser:appgroup /app/target/*.jar app.jar

# Unpack the jar to run in exploded mode (required for runtime compilation like jOOR to see classpath)
RUN mkdir -p /app/extracted \
    && cd /app/extracted \
    && jar -xf ../app.jar \
    && chown -R appuser:appgroup /app/extracted

# Switch to non-root user
USER appuser

# Expose Spring Boot default port
EXPOSE 8080

# JVM options:
#   -Dspring.profiles.active=prod → activates prod Spring profile
ENTRYPOINT ["java", \
    "-Dspring.profiles.active=prod", \
    "-cp", "/app/extracted/BOOT-INF/classes:/app/extracted:/app/extracted/BOOT-INF/lib/*:/app/libs/*", \
    "vn.cxn.apache_camel.ApacheCamelApplication"]
