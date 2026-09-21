# syntax=docker/dockerfile:1

################################################################################
# Stage 1: Build the application using Gradle
################################################################################
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /source

# Copy gradle wrapper and configurations
COPY gradle/ gradle/
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle.kts .
COPY build.gradle.kts .

# Copy project definition files and sources
COPY bbb-shared/ bbb-shared/
COPY bbb-api/ bbb-api/

# Build the distribution
ARG APP_VERSION=0.1.0
RUN ./gradlew :bbb-api:installDist -PappVersion=${APP_VERSION} --no-daemon

################################################################################
# Stage 2: Final minimal runtime image
################################################################################
FROM eclipse-temurin:21-jre-alpine AS final

RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    mkdir -p /opt/app/logging /opt/app/logs && \
    chown -R appuser:appgroup /opt/app

WORKDIR /opt/app

COPY --from=build /source/bbb-api/build/install/bbb-api /opt/app/
COPY --from=build /source/bbb-api/src/main/resources/logback.xml /opt/app/logging/logback.xml

RUN chmod +x /opt/app/bin/* && chown -R appuser:appgroup /opt/app

USER appuser

ENV API_PORT=8081
EXPOSE 8081

ENTRYPOINT ["/opt/app/bin/bbb-api"]
