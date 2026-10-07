# syntax=docker/dockerfile:1.7
# Etapa 1: compilar, precompilar las plantillas JTE y empaquetar. No hace falta Java en la máquina.
FROM maven:3-eclipse-temurin-25@sha256:dd1ad2333ee5b795f43610f18044f5fc5144fd472295c6f12ee4cf06241f1838 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# Etapa 2: solo el JRE, Graphviz y poppler (pdftotext) como procesos hijo, y curl para el healthcheck.
FROM eclipse-temurin:25-jre@sha256:bcc1a99b4bc676717bdc9841c363072ce10e3f34462c4017f7df08e730f485b0
RUN apt-get update \
 && apt-get install -y --no-install-recommends graphviz poppler-utils curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home app
USER app
WORKDIR /app
COPY --from=build /src/target/pensamiento-critico-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-Djava.io.tmpdir=/tmp", "-jar", "app.jar"]
