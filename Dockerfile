FROM openjdk:17-slim
WORKDIR /app
COPY target/httpclient-1.0-SNAPSHOT.jar /app/httpclient.jar
