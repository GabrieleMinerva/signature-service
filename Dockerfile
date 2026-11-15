FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY signature-messaging/pom.xml signature-messaging/pom.xml
COPY document-service/pom.xml document-service/pom.xml
COPY signature-service/pom.xml signature-service/pom.xml
COPY signature-messaging/src signature-messaging/src
COPY document-service/src document-service/src
COPY signature-service/src signature-service/src
RUN sh mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre AS document
WORKDIR /app
COPY --from=build /build/document-service/target/document-service-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre AS signature
WORKDIR /app
COPY --from=build /build/signature-service/target/signature-service-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
