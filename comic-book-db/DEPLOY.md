# comic-book-db — Frontend Deployment Guide

Angular 16 app hosted on Azure Static Web Apps.
For backend infrastructure, Cosmos DB setup, RBAC, and CORS configuration, see `../fn-comic-db/DEPLOY.md`.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development](#local-development)
3. [Build for Production](#build-for-production)
4. [Deploy to Azure Static Web Apps](#deploy-to-azure-static-web-apps)
5. [Updating the Frontend](#updating-the-frontend)

---

## Prerequisites

| Tool | Purpose |
|---|---|
| Node.js 18+ / npm | Build the Angular app |
| Azure CLI (`az`) | Retrieve the deployment token at deploy time |

```bash
brew install node azure-cli
az login
```

---

## Local Development

The service files contain a commented-out localhost URL. Toggle it before running locally:

In `src/app/comic.service.ts` and `src/app/image.service.ts`:
```typescript
// private baseServiceUrl = 'https://fn-comicBook-db-1703810588398.azurewebsites.net/api';
private baseServiceUrl = 'http://localhost:7071/api';   // ← uncomment for local
```

> Remember to revert this before building for production.

Start the local dev server:
```bash
npm install
npm start
```

The app will be available at `http://localhost:4200`. The backend must also be running locally — see `../fn-comic-db/DEPLOY.md`.

---

## Build for Production

Ensure the service URLs in `comic.service.ts` and `image.service.ts` point to the Azure Function App (not localhost), then:

```bash
node_modules/.bin/ng build --base-href "https://lemon-pebble-00417c11e.6.azurestaticapps.net/"
```

Output is written to `dist/comic-book-db/`.

---

## Deploy to Azure Static Web Apps

The deployment token is retrieved live from Azure at deploy time and is never stored in the repository.

```bash
npx @azure/static-web-apps-cli deploy dist/comic-book-db \
  --deployment-token $(az staticwebapp secrets list \
    --name comic-book-db \
    --resource-group comic-db-rg \
    --query "properties.apiKey" -o tsv) \
  --env production
```

```bash
npx @azure/static-web-apps-cli deploy dist/comic-book-db --deployment-token $(az staticwebapp secrets list --name comic-book-db --resource-group comic-db-rg --query "properties.apiKey" -o tsv) --env production
```


---

## Updating the Frontend

```bash
# 1. Build
node_modules/.bin/ng build --base-href "https://lemon-pebble-00417c11e.6.azurestaticapps.net/"

# 2. Deploy
npx @azure/static-web-apps-cli deploy dist/comic-book-db \
  --deployment-token $(az staticwebapp secrets list \
    --name comic-book-db \
    --resource-group comic-db-rg \
    --query "properties.apiKey" -o tsv) \
  --env production
```
