FROM python:3.13-slim
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.title="CampusClaw-Plus MCP demo" \
      org.opencontainers.image.source="https://github.com/UtopiWA/campusclaw-plus" \
      org.opencontainers.image.revision="${GIT_COMMIT}"

WORKDIR /app
COPY server.py /app/server.py

EXPOSE 8090
CMD ["python", "/app/server.py"]
