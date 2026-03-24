ARG NODE_VERSION=24
FROM node:${NODE_VERSION}-alpine AS node

FROM eclipse-temurin:24-jdk-alpine AS build
WORKDIR /app

COPY --from=node /usr/local /usr/local

COPY gradlew .
COPY gradle/ gradle/
COPY gradle.properties .
COPY build.gradle.kts .
RUN ./gradlew dependencies --no-daemon

COPY package.json .
COPY package-lock.json .
RUN npm ci

COPY src ./src
COPY scripts ./scripts

RUN ./gradlew build -x test -x check

FROM eclipse-temurin:24-jre-alpine
VOLUME /tmp
COPY --from=build /app/build/libs/app.jar app.jar
ENTRYPOINT ["sh", "-c", "java -Dsun.net.inetaddr.ttl=5 -Dsun.net.inetaddr.negative.ttl=10 -jar /app.jar"]
