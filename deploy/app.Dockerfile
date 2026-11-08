# The PokeDnD app image: the built Angular app served as static files. Build from the repository root:
#   docker build -f deploy/app.Dockerfile -t pokednd-app .
# Minimal for now; later phases refine it.

FROM node:24-alpine AS build
WORKDIR /src
COPY App/package.json App/package-lock.json ./
RUN npm ci
COPY App/ ./
# Production build, both languages: dist/pokednd-app/browser/en and /pt
RUN npx ng build

FROM caddy:2-alpine
COPY deploy/app.Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/dist/pokednd-app/browser/ /srv/
EXPOSE 80
