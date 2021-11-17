FROM openjdk:11-jre-slim

COPY target/heliumevents.jar heliumevents.jar

ENTRYPOINT [ "java", "-jar", "heliumevents.jar", "-Dspring.main.web-application-type=none" ]