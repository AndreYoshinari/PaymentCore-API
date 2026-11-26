FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
	&& addgroup -S spring \
	&& adduser -S spring -G spring
WORKDIR /app
COPY --from=build --chown=spring:spring /src/target/payment-core-0.0.1-SNAPSHOT.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
