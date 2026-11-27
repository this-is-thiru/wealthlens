# Runtime only. CI's unit job builds and tests backend/target/wealthlens.jar (./mvnw … package) and its gate job
# wraps that exact jar once both tiers pass, so what is published is what was tested. To build it locally:
#   ./mvnw clean install -pl backend -am -DskipTests && docker build -t wealthlens .
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY backend/target/wealthlens.jar /app.jar

# Not root (W-131j): the jar stays root-owned and read-only to the app. The app makes itself non-dumpable at start, so the PDF
# reader it starts as this same user cannot read its secrets (ProcessProtection).
RUN addgroup -S wealthlens && adduser -S -G wealthlens wealthlens
USER wealthlens

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=default
# The Foreign Function API call in ProcessProtection is native access; allowing it keeps the start free of its warning.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app.jar"]
