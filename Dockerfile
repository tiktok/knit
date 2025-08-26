# Dockerfile

# Use Gradle image with JDK 21
FROM gradle:8.13-jdk21
LABEL authors="ZZQ"

# Set environment variables
ENV KOTLIN_VERSION=2.1.20
ENV REMOTE_KNIT_VERSION=0.1.5

# Set working directory inside container
WORKDIR /home/knit

# Copy all project files to the container
COPY . .

# Pre-download dependencies and build demo-jvm module
RUN gradle :demo-jvm:build --no-daemon

# Optional: set default command if you want to run the jar automatically
# CMD ["java", "-jar", "demo-jvm/build/libs/demo-jvm-all.jar"]
