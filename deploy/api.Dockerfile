# The PokeDnD API image (PostgreSQL flavour). Build from the repository root:
#   docker build -f deploy/api.Dockerfile -t pokednd-api .
# Minimal for now; later phases refine it (one image per database flavour, phase 34).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY Core/ Core/
COPY API/ API/
# Checked-out files may have Windows line endings; the Gradle scripts need Unix ones.
RUN sed -i 's/\r$//' Core/gradlew API/gradlew && chmod +x Core/gradlew API/gradlew
# The API builds against Core in Maven local (spec 13.4), so publish Core first.
RUN cd Core && ./gradlew --no-daemon publishToMavenLocal
RUN cd API && ./gradlew --no-daemon build -x test

FROM eclipse-temurin:25-jre
RUN groupadd --system pokednd && useradd --system --gid pokednd --home-dir /app pokednd \
    && mkdir -p /data && chown pokednd:pokednd /data
WORKDIR /app
COPY --from=build --chown=pokednd:pokednd /src/API/build/quarkus-app/ /app/
USER pokednd
ENV POKEDND_DATA_DIR=/data
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
