# Dockerfile

# Use Gradle image with JDK 17
FROM gradle:7.6.1-jdk17
LABEL authors="ZZQ"

# Set environment variables
ENV KOTLIN_VERSION=1.9.20
ENV REMOTE_KNIT_VERSION=0.1.5

# Set working directory inside container
WORKDIR /home/knit

# Copy all project files to the container
COPY . .

# Pre-download dependencies and build demo-jvm module
RUN gradle --no-daemon :demo-jvm:shadowJar

# Optional: set default command if you want to run the jar automatically
# CMD ["java", "-jar", "demo-jvm/build/libs/demo-jvm-all.jar"]
