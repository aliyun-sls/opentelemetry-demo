# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

FROM gradle:8.11.1-jdk21 AS builder

WORKDIR /usr/src/app/

COPY ./src/inventory-service/build.gradle ./src/inventory-service/settings.gradle ./
COPY ./src/inventory-service/gradle ./gradle
COPY ./src/inventory-service/gradlew ./src/inventory-service/gradlew.bat ./

# Download dependencies
RUN gradle dependencies --no-daemon

COPY ./src/inventory-service/src ./src
RUN gradle bootJar --no-daemon

# -----------------------------------------------------------------------------

FROM openjdk:21-jre

ARG OTEL_JAVA_AGENT_VERSION=2.15.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar

WORKDIR /app

COPY --from=builder /usr/src/app/build/libs/inventory-service.jar ./

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "inventory-service.jar"] 