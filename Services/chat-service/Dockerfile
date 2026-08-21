FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy shared protocol and install it to local repo
COPY shared/protocol ./shared/protocol
RUN rm -rf shared/protocol/src/main/java && mvn -f shared/protocol/pom.xml clean install -DskipTests

# Copy chat-service source code and package application
COPY Services/chat-service ./Services/chat-service
RUN mvn -f Services/chat-service/pom.xml clean package -DskipTests

# Locate executable jar
RUN find Services/chat-service/target -name "*.jar" ! -name "*.original" -exec cp {} /build/app.jar \;

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/app.jar app.jar

EXPOSE 8083 9093

ENTRYPOINT ["java", "-jar", "app.jar"]
