# Runtime only. CI builds and tests backend/target/wealthlens.jar first (./mvnw … test verify), and
# this image wraps that exact jar, so what is published is what was tested. To build it locally:
#   ./mvnw clean install -pl backend -am -DskipTests && docker build -t wealthlens .
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY backend/target/wealthlens.jar /app.jar

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=default
ENTRYPOINT ["java", "-jar", "/app.jar"]
