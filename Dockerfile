# Stage 1: Build the commafeed-client
FROM maven:3.8.4-openjdk-11-slim AS build-client
WORKDIR /app

# Copy the pom.xml and other necessary files
COPY pom.xml .
COPY commafeed-client/pom.xml commafeed-client/
COPY commafeed-server/pom.xml commafeed-server/
COPY commafeed-client/src/ commafeed-client/src/
COPY commafeed-server/src/ commafeed-server/src/

# Install Node.js
RUN apt-get update && apt-get install -y nodejs npm

# Install node modules
WORKDIR /app/commafeed-client
RUN npm install

# Skip running tests and build the client
WORKDIR /app
RUN mvn clean install -pl commafeed-client -am -DskipTests -e

# Stage 2: Build the commafeed-server
FROM maven:3.8.4-openjdk-11-slim AS build-server
WORKDIR /app

# Copy the pom.xml and other necessary files
COPY pom.xml .
COPY commafeed-client/pom.xml commafeed-client/
COPY commafeed-server/pom.xml commafeed-server/
COPY commafeed-client/src/ commafeed-client/src/
COPY commafeed-server/src/ commafeed-server/src/

# Copy the built client artifact from the previous stage
COPY --from=build-client /root/.m2/repository/com/commafeed/commafeed-client/4.4.1/commafeed-client-4.4.1.jar /root/.m2/repository/com/commafeed/commafeed-client/4.4.1/commafeed-client-4.4.1.jar

# Build the server
RUN mvn clean package -pl commafeed-server -am

# Stage 3: Create the final image
FROM eclipse-temurin:21.0.3_9-jre

EXPOSE 8082

RUN mkdir -p /commafeed/data
VOLUME /commafeed/data

# Copy the built jar file and config file from the build stage
COPY --from=build-server /app/commafeed-server/target/commafeed.jar .
COPY commafeed-server/config.yml.example config.yml

# Set Java options
ENV JAVA_TOOL_OPTIONS -Djava.net.preferIPv4Stack=true -Xms20m -XX:+UseG1GC -XX:-ShrinkHeapInSteps -XX:G1PeriodicGCInterval=10000 -XX:-G1PeriodicGCInvokesConcurrent -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=10

CMD ["java", "-jar", "commafeed.jar", "server", "config.yml"]