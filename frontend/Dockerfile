# syntax=docker/dockerfile:1

# ---- build stage: type-check and bundle (dependencies cached in their own layer) ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# `npm run build` type-checks first; tests run in CI / locally (`npm test`), the image build only packages.
RUN npm run build

# ---- runtime stage: static files behind unprivileged nginx (no root, no Node) ----
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY proxy-api.conf /etc/nginx/proxy-api.conf
COPY --from=build /app/dist /usr/share/nginx/html
# The base image already runs as the unprivileged `nginx` user; stating it keeps that visible (and checkable).
USER nginx
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/ || exit 1
