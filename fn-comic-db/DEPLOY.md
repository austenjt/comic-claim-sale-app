# fn-comic-db — Backend Deployment Guide

Java 17 Azure Function App backed by Azure Cosmos DB.
For frontend deployment, see `../comic-book-db/DEPLOY.md`.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Azure Resource Overview](#azure-resource-overview)
3. [Local Development](#local-development)
4. [RBAC Roles](#rbac-roles)
5. [Gmail OAuth2 Email Setup](#gmail-oauth2-email-setup)
6. [Cosmos DB Setup](#cosmos-db-setup)
7. [CORS Configuration](#cors-configuration)
8. [Build & Deploy](#build--deploy)

---

## Prerequisites

| Tool | Purpose |
|---|---|
| Java 17 JDK | Compile the function code |
| Maven 3.x | Build and package the Function App |
| Azure CLI (`az`) | Deploy and manage Azure resources |
| Azure Functions Core Tools v4 | Run functions locally |

```bash
brew install azure-cli azure-functions-core-tools@4
az login
az account set --subscription "<your-subscription-id>"
```

---

## Azure Resource Overview

| Resource | Name | Resource Group | Region |
|---|---|---|---|
| Function App | `fn-comicBook-db-1703810588398` | `comic-db-rg` | `westus2` |
| App Service Plan | `java-functions-app-service-plan` | `comic-db-rg` | `westus2` |
| Cosmos DB Account | *(your account name)* | `comic-db-rg` | `westus2` |
| Cosmos DB Database | `comic-db` | — | — |
| Cosmos DB Container | `comics` | partition key: `/id` | — |
| Cosmos DB Container | `images` | partition key: `/id` | — |
| Cosmos DB Container | `users` | partition key: `/id` | — |
| Cosmos DB Container | `sessions` | partition key: `/id` | — |
| Cosmos DB Container | `carts` | partition key: `/id` | — |
| Static Web App (frontend) | `comic-book-db` | `comic-db-rg` | — |

---

## Local Development

Create `local.settings.json` in the project root. **Do not commit this file** — it is gitignored.

```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "COSMOS_ENDPOINT": "https://<your-cosmos-account>.documents.azure.com:443/",
    "ADMIN_EMAIL": "<your-admin-email>",
    "GMAIL_USERNAME": "<your-gmail-address>",
    "GMAIL_CLIENT_ID": "<your-client-id>.apps.googleusercontent.com",
    "GMAIL_CLIENT_SECRET": "<your-client-secret>",
    "GMAIL_REFRESH_TOKEN": "<your-refresh-token>"
  }
}
```

`DefaultAzureCredential` is used for Cosmos DB authentication — no key is needed. It picks up your active Azure CLI session automatically.

```bash
az login
mvn clean package -DskipTests
mvn azure-functions:run
```

The local API will be available at `http://localhost:7071/api/`.

---

## RBAC Roles

### Developer Deployment Roles

The deploying account needs the following on the `comic-db-rg` resource group:

| Role | Reason |
|---|---|
| `Contributor` | Deploy Function App and Static Web App |

```bash
az role assignment create \
  --assignee "<developer-email-or-object-id>" \
  --role "Contributor" \
  --scope "/subscriptions/<subscription-id>/resourceGroups/comic-db-rg"
```

### Function App Managed Identity (Cosmos DB Keyless Auth)

The Function App uses `DefaultAzureCredential` — no Cosmos DB key is stored anywhere. The Function App's System-Assigned Managed Identity must be granted the **Cosmos DB Built-in Data Contributor** role.

**Step 1: Enable System-Assigned Managed Identity**
```bash
az functionapp identity assign \
  --name fn-comicBook-db-1703810588398 \
  --resource-group comic-db-rg
```
Note the `principalId` in the output.

**Step 2: Grant Cosmos DB access to the identity**
```bash
az cosmosdb sql role assignment create \
  --account-name comic-cosmos-db \
  --resource-group comic-db-rg \
  --role-definition-name "Cosmos DB Built-in Data Contributor" \
  --principal-id <principal-id-from-step-1> \
  --scope "/subscriptions/<subscription-id>/resourceGroups/comic-db-rg/providers/Microsoft.DocumentDB/databaseAccounts/comic-cosmos-db"
```

**Step 3: Set the Cosmos DB endpoint app setting** (endpoint only — no key)
```bash
az functionapp config appsettings set \
  --name fn-comicBook-db-1703810588398 \
  --resource-group comic-db-rg \
  --settings "COSMOS_ENDPOINT=https://<your-cosmos-account>.documents.azure.com:443/"
```

**Step 4: Set ADMIN_EMAIL** (the email address that receives admin privileges on first registration; must match `GMAIL_USERNAME` if the email form is enabled)
```bash
az functionapp config appsettings set \
  --name fn-comicBook-db-1703810588398 \
  --resource-group comic-db-rg \
  --settings "ADMIN_EMAIL=<your-admin-email>"
```

---

## Gmail OAuth2 Email Setup

The contact form sends email via the **Gmail REST API** using OAuth2 user credentials (`google-api-services-gmail` + `google-auth-library-oauth2-http`). No Gmail password is stored — the app holds a refresh token and the Google auth library exchanges it for a short-lived access token automatically.

The contact form can be toggled on or off with the `GMAIL_ENABLED` app setting (`true`/`false`). When `false`, the Angular frontend hides the form entirely and the backend rejects submissions. Defaults to `true` if the setting is absent.

> **Note:** `GMAIL_USERNAME` and `ADMIN_EMAIL` must be the same Gmail address when the email form is enabled. `ADMIN_EMAIL` identifies the single admin user; `GMAIL_USERNAME` is the Google account the OAuth credentials were created under and is the address that sends and receives contact form emails.

### 1. Create a Google Cloud Project and Enable the Gmail API

1. Go to **console.cloud.google.com** and create a new project (or select an existing one).
2. Navigate to **APIs & Services → Library**, search for **Gmail API**, and enable it.

### 2. Create OAuth 2.0 Credentials

1. Go to **APIs & Services → Credentials → Create Credentials → OAuth 2.0 Client ID**.
2. Choose **Desktop app** as the application type.
3. Download the JSON — it contains your `client_id` and `client_secret`.

### 3. Obtain a Refresh Token (one-time)

Use the **OAuth 2.0 Playground** at `https://developers.google.com/oauthplayground`:

1. Click the gear icon → check **"Use your own OAuth credentials"** → enter your `client_id` and `client_secret`.
2. In the scope list, enter `https://mail.google.com/` and authorize it with your Gmail account.
3. Click **"Exchange authorization code for tokens"** and copy the **Refresh token**.

### 4. Add App Settings to the Function App

```bash
az functionapp config appsettings set \
  --name fn-comicBook-db-1703810588398 \
  --resource-group comic-db-rg \
  --settings \
    GMAIL_ENABLED="true" \
    GMAIL_USERNAME="<your-gmail-address>" \
    GMAIL_CLIENT_ID="<your-client-id>.apps.googleusercontent.com" \
    GMAIL_CLIENT_SECRET="<your-client-secret>" \
    GMAIL_REFRESH_TOKEN="<your-refresh-token>"
```

To disable the contact form without redeploying, set `GMAIL_ENABLED=false` in the Function App settings — the frontend will hide the form on next load.

### 5. Add to local.settings.json for local development

```json
{
  "Values": {
    "GMAIL_ENABLED": "true",
    "GMAIL_USERNAME": "<your-gmail-address>",
    "GMAIL_CLIENT_ID": "<your-client-id>.apps.googleusercontent.com",
    "GMAIL_CLIENT_SECRET": "<your-client-secret>",
    "GMAIL_REFRESH_TOKEN": "<your-refresh-token>"
  }
}
```

---

## Cosmos DB Setup

Run these only when creating the database from scratch.

```bash
# Create database
az cosmosdb sql database create \
  --account-name comic-cosmos-db \
  --resource-group comic-db-rg \
  --name comic-db

# Create comics container
az cosmosdb sql container create \
  --account-name comic-cosmos-db \
  --resource-group comic-db-rg \
  --database-name comic-db \
  --name comics \
  --partition-key-path "/id"

# Create images container
az cosmosdb sql container create \
  --account-name comic-cosmos-db \
  --resource-group comic-db-rg \
  --database-name comic-db \
  --name images \
  --partition-key-path "/id"

# Create users container (Phase 1 auth)
az cosmosdb sql container create \
  --account-name comic-cosmos-db \
  --resource-group comic-db-rg \
  --database-name comic-db \
  --name users \
  --partition-key-path "/id"

# Create sessions container (Phase 1 auth)
az cosmosdb sql container create \
  --account-name comic-cosmos-db \
  --resource-group comic-db-rg \
  --database-name comic-db \
  --name sessions \
  --partition-key-path "/id"

# Create carts container (Phase 2 claims)
az cosmosdb sql container create \
  --account-name comic-cosmos-db \
  --resource-group comic-db-rg \
  --database-name comic-db \
  --name carts \
  --partition-key-path "/id"
```

---

## CORS Configuration

The Angular frontend must be listed as an allowed origin on the Function App. Azure Functions handles OPTIONS preflight at the platform level using this list — the code-level `Access-Control-Allow-Origin` headers alone are not sufficient.

**Current allowed origins** (as of 2026-03-11):
- `https://lightningcomics.rocks` — production custom domain
- `https://lemon-pebble-00417c11e.6.azurestaticapps.net` — original Azure Static Web Apps URL (kept for backwards compatibility)
- `http://localhost:4200` — local development

```bash
# Allow the production custom domain
az functionapp cors add \
  --name fn-comicBook-db-1703810588398 \
  --resource-group comic-db-rg \
  --allowed-origins "https://lightningcomics.rocks"

# Allow the Azure Static Web Apps URL (original domain)
az functionapp cors add \
  --name fn-comicBook-db-1703810588398 \
  --resource-group comic-db-rg \
  --allowed-origins "https://lemon-pebble-00417c11e.6.azurestaticapps.net"

# Allow local development
az functionapp cors add \
  --name fn-comicBook-db-1703810588398 \
  --resource-group comic-db-rg \
  --allowed-origins "http://localhost:4200"

# Verify
az functionapp cors show \
  --name fn-comicBook-db-1703810588398 \
  --resource-group comic-db-rg
```

> **Note:** When adding a new frontend domain (e.g. a custom domain or new Static Web Apps URL), always run `az functionapp cors add` with the new origin — otherwise all API requests from that domain will fail with CORS errors.

---

## Build & Deploy

```bash
# Build and package
mvn clean package -DskipTests

# Deploy to Azure (uses active az login session)
mvn azure-functions:deploy
```

Deployment configuration is in `pom.xml` under `azure-functions-maven-plugin`. No secrets are required.
