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
COPY bb-shared/ bb-shared/
COPY bb-update-database/ bb-update-database/

# Build the distribution
ARG APP_VERSION=1.0
RUN ./gradlew :bb-update-database:installDist -PappVersion=${APP_VERSION} --no-daemon

################################################################################
# Stage 2: Final minimal runtime image
################################################################################
FROM eclipse-temurin:21-jre-alpine AS final

RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    mkdir -p /opt/app/logging /opt/app/logs /data && \
    chown -R appuser:appgroup /opt/app /data

WORKDIR /opt/app

COPY --from=build /source/bb-update-database/build/install/bb-update-database /opt/app/
COPY --from=build /source/bb-update-database/src/main/resources/logback.xml /opt/app/logging/logback.xml
COPY --from=build /source/bb-update-database/migrations /opt/app/migrations

RUN chown -R appuser:appgroup /opt/app

USER appuser

ENTRYPOINT ["/opt/app/bin/bb-update-database"]
CMD ["-h"]
