# Stage 1: Build the React application
FROM node:20-alpine AS builder

WORKDIR /app

# Copy dependency definitions
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build configuration
COPY . .

# Build argument for WebSocket Gateway URL (defaults to ws://localhost:8080/ws)
ARG VITE_WS_URL=ws://localhost:8080/ws
ENV VITE_WS_URL=$VITE_WS_URL
# TV10 failover: optional comma-separated endpoint pool (defaults to both local gateways)
ARG VITE_WS_URLS=ws://localhost:8080/ws,ws://localhost:8090/ws
ENV VITE_WS_URLS=$VITE_WS_URLS
ARG VITE_WS_SAME_ORIGIN=false
ENV VITE_WS_SAME_ORIGIN=$VITE_WS_SAME_ORIGIN

# Build production bundle
RUN npm run build

# Stage 2: Serve with lightweight Nginx
FROM nginx:1.25-alpine

# Copy built assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy Nginx SPA configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
