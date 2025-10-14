# Multi-stage build for RouteGuide gRPC Server

# Stage 1: Build the application
FROM --platform=linux/amd64 maven:3.8-openjdk-11-slim AS build
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create runtime image
FROM --platform=linux/amd64 openjdk:11-jre-slim
WORKDIR /app

# Copy the fat JAR from build stage
COPY --from=build /app/target/route-guide-1.0-SNAPSHOT.jar /app/route-guide.jar

# Expose gRPC port
EXPOSE 8980

# Run the server
ENTRYPOINT ["java", "-jar", "/app/route-guide.jar"]
