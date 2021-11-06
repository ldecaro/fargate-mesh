FROM amazoncorretto:8
RUN mkdir -p /u01/deploy
WORKDIR /u01/deploy

COPY target/greeting-ui-1.0.jar greeting.jar

ENTRYPOINT [ "sh", "-c", "java -jar /u01/deploy/greeting.jar"]