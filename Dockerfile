FROM eclipse-temurin:21.0.13_9-jre-jammy@sha256:dff6eb46527bd293a3c06bbbe9e64e8c9f91f01905b5abbdad31761c03059d2c
WORKDIR /app
COPY --chown=10001:10001 target/temporal-tenant-routing-1.0.0.jar /app/application.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
