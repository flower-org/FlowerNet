# Use a smaller OpenJDK runtime image
FROM eclipse-temurin:17-jre-alpine

# Set the working directory in the container
WORKDIR /app

# Copy the application JAR into the container
# (which should be built previously, `gradle shadowJar`, say in GitHub Actions)
COPY Socks5sServer/build/libs/Socks5sServer-all.jar /app/app.jar

# Set the entry point for the container
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Expose the application port (if needed, adjust the port number)
#EXPOSE 8080