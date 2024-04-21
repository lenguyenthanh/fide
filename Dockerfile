ARG VERSION=eclipse-temurin-jammy-21.0.2_13_1.9.9_3.4.1
FROM sbtscala/scala-sbt:${VERSION}

COPY . /app

WORKDIR /app
ENV SHELL /bin/sh
ENTRYPOINT ["sbt", "backend/run"]
