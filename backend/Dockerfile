FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:resolve
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-alpine
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.title="CampusClaw-Plus backend" \
      org.opencontainers.image.source="https://github.com/UtopiWA/campusclaw-plus" \
      org.opencontainers.image.revision="${GIT_COMMIT}"
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /app/target/campusclaw-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
