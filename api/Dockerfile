# ─── Stage 1: Build ───────────────────────────────────────────────
FROM gradle:8.10-jdk17 AS builder

WORKDIR /app
COPY . .

# Build fat JAR (shadow JAR)
RUN gradle shadowJar --no-daemon -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy JAR từ build stage
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Expose port (Render sẽ inject PORT env var)
EXPOSE 8080

# Healthcheck cho Docker (Render dùng HTTP healthcheck riêng)
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run với JVM options tối ưu cho container 512MB RAM (Render Free)
ENTRYPOINT ["java", \
    "-Xmx350m", \
    "-Xms128m", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=70", \
    "-XX:+UseG1GC", \
    "-jar", "app.jar"]
