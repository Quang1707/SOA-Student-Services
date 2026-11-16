FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY index.js ./
COPY migrations ./migrations
ENV NODE_ENV=production
EXPOSE 8083
USER node
CMD ["node", "index.js"]
