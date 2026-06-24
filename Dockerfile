# =============================================================================
# Stage 1: Build Frontend (Vue 3 + Vite)
# Output: src/main/resources/static/ (served as Spring Boot static resources)
# =============================================================================
FROM node:24.16.0-trixie-slim AS frontend-builder

RUN corepack enable && corepack prepare pnpm@latest --activate

WORKDIR /app/frontend

COPY frontend/package.json frontend/pnpm-lock.yaml ./

ENV PNPM_CONFIG_DANGEROUSLY_ALLOW_ALL_BUILDS=true

RUN pnpm install --frozen-lockfile

COPY frontend/ ./

WORKDIR /app
COPY src/main/resources/ src/main/resources/

WORKDIR /app/frontend
RUN pnpm run build

# =============================================================================
# Stage 2: Build Backend (Maven)
# =============================================================================
FROM eclipse-temurin:25-jdk-ubi10-minimal AS backend-builder

WORKDIR /app

COPY . .

COPY --from=frontend-builder /app/src/main/resources/static/ src/main/resources/static/

RUN chmod +x ./mvnw

RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -Pprod -DskipTests -B -q

RUN mkdir -p libs logs

# =============================================================================
# Stage 3: Runtime Image
# =============================================================================
FROM eclipse-temurin:25-jre-ubi10-minimal AS runtime

LABEL maintainer="TKC"
LABEL description="Apache Camel Dashboard"

WORKDIR /app

COPY --from=backend-builder /app/libs /app/libs
COPY --from=backend-builder /app/logs /app/logs
COPY --from=backend-builder /app/target/*.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
