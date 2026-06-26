FROM eclipse-temurin:21-jre

WORKDIR /app

ARG UBUNTU_PORTS_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/

RUN sed -i "s|http://ports.ubuntu.com/ubuntu-ports/|${UBUNTU_PORTS_MIRROR}|g; s|https://ports.ubuntu.com/ubuntu-ports/|${UBUNTU_PORTS_MIRROR}|g" /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

ARG JAR_FILE=LinguaFrame/target/LinguaFrame-0.0.1-SNAPSHOT.jar

COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
