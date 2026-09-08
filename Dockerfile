FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S portal && adduser -S -G portal portal

COPY --chown=portal:portal backend/target/ztoken-portal-*.jar /app/app.jar

EXPOSE 8084

USER portal

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]
