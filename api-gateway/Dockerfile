# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom first to cache dependencies
COPY pom.xml .

# Pre-fetch dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the jar
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jdk-jammy AS runner

WORKDIR /app

# Copy jar from builder stage (using wildcard to avoid hardcoding version)
COPY --from=builder /app/target/*.jar app.jar

# Expose port (matches application.properties)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
