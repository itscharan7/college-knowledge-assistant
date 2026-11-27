# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home appuser && mkdir -p /app/data/uploads && chown -R appuser /app
WORKDIR /app
COPY --from=build /build/target/college-assistant-*.jar app.jar
USER appuser
ENV STORAGE_DIR=/app/data/uploads
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
