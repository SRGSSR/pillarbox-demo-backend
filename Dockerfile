# First stage: Build JAR
FROM eclipse-temurin:24-jdk-alpine AS build

WORKDIR /app

COPY gradlew .
COPY gradle/ gradle/
COPY gradle.properties .
COPY build.gradle.kts .
COPY src ./src

RUN ./gradlew clean build -x test -x check

# Second stage: Run JAR
FROM eclipse-temurin:24-jre-alpine
VOLUME /tmp

COPY --from=build /app/build/libs/app.jar app.jar

ENTRYPOINT ["sh", "-c", "java -Dsun.net.inetaddr.ttl=5 -Dsun.net.inetaddr.negative.ttl=10 -jar /app.jar"]
