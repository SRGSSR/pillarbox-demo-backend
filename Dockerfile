FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS node

FROM eclipse-temurin:26-jdk-alpine@sha256:7cc9b8649a735f80bb5216e135306c4b9ab428db2893d770c4516f469fa08623 AS build
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

FROM eclipse-temurin:26-jre-alpine@sha256:158186ab168dc35e5a8e799f6195eef61d57a86a73348fc3a1188317fc40f01c
RUN addgroup -S -g 10001 pillarbox \
 && adduser -S -D -H -u 10001 -G pillarbox -h /nonexistent -s /sbin/nologin pillarbox
VOLUME /tmp
COPY --from=build /app/build/libs/app.jar app.jar
USER 10001:10001
EXPOSE 8080
# The public Player API answers once the server and the database are up.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/v1/player/media?limit=1"]
ENTRYPOINT ["sh", "-c", "java --enable-native-access=ALL-UNNAMED -Dsun.net.inetaddr.ttl=5 -Dsun.net.inetaddr.negative.ttl=10 -jar /app.jar"]
