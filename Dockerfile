FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS node

FROM eclipse-temurin:27-jdk-alpine@sha256:3ce6707ba035480069ff65d12265a8806d74fe9fd761987d183dd86362def451 AS build
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

FROM eclipse-temurin:27-jre-alpine@sha256:51f686a5322c0b762dff27e8e94b4b6582d7e71321d3a23b1f3c53b4078aabc5
RUN addgroup -S -g 10001 pillarbox \
 && adduser -S -D -H -u 10001 -G pillarbox -h /nonexistent -s /sbin/nologin pillarbox
VOLUME /tmp
COPY --from=build /app/build/libs/app.jar app.jar
USER 10001:10001
EXPOSE 8080
# The public Player API answers once the server and the database are up.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/v1/player/media?limit=1"]
ENTRYPOINT ["sh", "-c", "java --enable-native-access=ALL-UNNAMED -XX:-UseBASE64Intrinsics -Dsun.net.inetaddr.ttl=5 -Dsun.net.inetaddr.negative.ttl=10 -jar /app.jar"]
