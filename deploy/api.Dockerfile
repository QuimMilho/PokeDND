# The PokeDnD API image. Quarkus fixes the kind of database when it builds the application, so there is one image per database
# flavour (spec 16.3): postgres (the default), mysql, mariadb or mongo. Build from the repository root:
#   docker build -f deploy/api.Dockerfile --build-arg FLAVOUR=mysql -t pokednd-api:latest-mysql .
# The image is told which database to talk to by environment variables only (see deploy/.env.example).
ARG FLAVOUR=postgres

FROM eclipse-temurin:25-jdk AS build
ARG FLAVOUR
WORKDIR /src
COPY Core/ Core/
COPY API/ API/
# Checked-out files may have Windows line endings; the Gradle scripts need Unix ones.
RUN sed -i 's/\r$//' Core/gradlew API/gradlew && chmod +x Core/gradlew API/gradlew
# The API builds against Core in Maven local (spec 13.4), so publish Core first.
RUN cd Core && ./gradlew --no-daemon publishToMavenLocal
RUN cd API && ./gradlew --no-daemon build -x test -PdbFlavour=${FLAVOUR}

FROM eclipse-temurin:25-jre
RUN groupadd --system pokednd && useradd --system --gid pokednd --home-dir /app pokednd \
    && mkdir -p /data && chown pokednd:pokednd /data
WORKDIR /app
COPY --from=build --chown=pokednd:pokednd /src/API/build/quarkus-app/ /app/
USER pokednd
ARG FLAVOUR
LABEL org.opencontainers.image.title="pokednd-api" org.opencontainers.image.description="PokeDnD API, database flavour: ${FLAVOUR}"
ENV POKEDND_DATA_DIR=/data
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
