FROM amazoncorretto:8
RUN mkdir -p /u01/deploy
WORKDIR /u01/deploy

COPY target/greeting-afternoon-1.0.jar greeting.jar

ENTRYPOINT [ "sh", "-c", "java  -Xmx256m -Xms256m -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9011 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=127.0.0.1 -Duser.timezone=GMT -Djava.net.preferIPv4Stack=true -jar /u01/deploy/greeting.jar"]