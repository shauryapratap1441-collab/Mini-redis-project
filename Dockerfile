# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-24 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Run stage ----
FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Most PaaS platforms inject PORT at runtime and route public traffic to it;
# 8080 is just the local default (see Main.java).
ENV PORT=8080
EXPOSE 8080
EXPOSE 6379

ENTRYPOINT ["java", "-jar", "app.jar"]
