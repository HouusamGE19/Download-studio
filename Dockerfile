# syntax=docker/dockerfile:1.7

FROM node:24-bookworm-slim AS frontend-build
WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-17 AS backend-build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
COPY --from=frontend-build /workspace/frontend/dist ./frontend/dist
RUN mvn --batch-mode -DskipTests package

FROM node:24-bookworm-slim AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        ffmpeg \
        openjdk-17-jre-headless \
        python3 \
        tini \
    && curl -fsSL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod 0755 /usr/local/bin/yt-dlp \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=backend-build /workspace/target/download-studio-1.0.0.jar /app/app.jar
COPY --chown=node:node download_single.sh /app/download_single.sh

RUN chmod 0755 /app/download_single.sh \
    && mkdir -p /downloads /data \
    && chown node:node /downloads /data

ENV SERVER_ADDRESS=0.0.0.0 \
    PORT=8080 \
    DOWNLOAD_SCRIPT=/app/download_single.sh \
    DOWNLOAD_DIRECTORY=/downloads \
    XDG_CACHE_HOME=/data/cache \
    DOWNLOAD_HISTORY=/data/history.json

USER node

EXPOSE 8080
VOLUME ["/downloads", "/data"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/api/system/status >/dev/null || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/app/app.jar"]
