FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN chmod +x gradlew

COPY src src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ARG OPENSKY_BASE_URL
ARG OPENSKY_USERNAME
ARG OPENSKY_PASSWORD

ENV OPENSKY_BASE_URL=${OPENSKY_BASE_URL}
ENV OPENSKY_USERNAME=${OPENSKY_USERNAME}
ENV OPENSKY_PASSWORD=${OPENSKY_PASSWORD}

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", \
  "--opensky.base-url=${OPENSKY_BASE_URL}", \
  "--opensky.username=${OPENSKY_USERNAME}", \
  "--opensky.password=${OPENSKY_PASSWORD}"]
