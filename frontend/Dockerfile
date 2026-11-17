FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.title="CampusClaw-Plus frontend" \
      org.opencontainers.image.source="https://github.com/UtopiWA/campusclaw-plus" \
      org.opencontainers.image.revision="${GIT_COMMIT}"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
