# ---------- Build Stage ----------
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app

# Copy the multi-module project
COPY pom.xml .
COPY checkstyle.xml .
COPY checkstyle-suppressions.xml .
COPY backend ./backend
COPY test-report ./test-report

# Package the backend application (and parent)
RUN mvn clean install -pl backend -am -DskipTests

# ---------- Runtime Stage ----------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy the built jar from the backend module
COPY --from=build /app/backend/target/wealthlens.jar /app.jar

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=default
ENTRYPOINT ["java", "-jar", "/app.jar"]
