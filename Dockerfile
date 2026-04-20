# Build stage
FROM node:24.11.1-alpine3.21 AS build

WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine-slim

# Copy built files from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf
RUN rm -r /etc/nginx/conf.d

EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
