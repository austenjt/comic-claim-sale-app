# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

Two independent sub-projects, each with their own git history:

- `comic-book-db/` — Angular 16 frontend (SPA, deployed to Azure Static Web Apps)
- `fn-comic-db/` — Java 17 Azure Functions backend (deployed to Azure Function App)

**Do not modify `fn-comic-db/` unless explicitly asked.** The Java model is the source of truth for all field names, enum values, and serialization formats.

## Commands

### Frontend (`comic-book-db/`)

```bash
npm install          # install dependencies
npm start            # dev server at http://localhost:4200
npm run build        # production build
npm test             # Karma/Jasmine unit tests (runs all specs)
```

To run a single test file, add `--include` to the test command or use the Karma UI to filter specs.

### Backend (`fn-comic-db/`)

```bash
mvn clean package                  # compile and package
mvn azure-functions:run            # run locally at http://localhost:7071
mvn test                           # run JUnit 5 tests
mvn test -Dtest=ClassName          # run a single test class
mvn azure-functions:deploy         # deploy to Azure (after mvn clean package)
```

After adding a new trigger class, always `mvn clean package` before deploying — the new class won't be picked up otherwise.

### Frontend Deploy to Azure

```bash
node_modules/.bin/ng build --base-href "https://lemon-pebble-00417c11e.6.azurestaticapps.net/"
npx @azure/static-web-apps-cli deploy dist/comic-book-db \
  --deployment-token $(az staticwebapp secrets list --name comic-book-db --resource-group comic-db-rg --query "properties.apiKey" -o tsv) \
  --env production
```

## Architecture

### Backend (fn-comic-db)

**Trigger classes** (`org.example.functions.*`) are the HTTP entry points — one class per resource domain:
- `ComicTriggers` — CRUD for comics (`/api/comics`)
- `CartTriggers` — shopping cart (`/api/cart`, `/api/cart/items`, `/api/cart/submit`)
- `UserTriggers` — auth/user management (`/api/users/*`)
- `AdminTriggers` — admin-only operations (`/api/orders`, `/api/orders/{id}/fulfill`)
- `ImageTriggers` — image upload/retrieval (`/api/images`)
- `SearchTriggers` — title search (`/api/search`)
- `EnumTriggers` — serves enum values to frontend (`/api/enums`)
- `DiscountTriggers` — discount management (`/api/discounts`)
- `ValidationTriggers` — field validation helpers

**Service classes** (`org.example.functions.service.*`) contain all business logic. Services are singletons accessed via `getServiceInstance()` — no dependency injection framework is used.

**Data layer**: `CosmosDbClient` is a manual singleton that holds references to all six Cosmos DB containers: `comics`, `images`, `users`, `sessions`, `carts`, `discounts`. The database is `comic-db` in Azure.

**Important Azure constraint**: The `admin/` route prefix is reserved by Azure Functions for its internal host management API. Any HTTP trigger routed under `admin/` will 404 in production. Use a different prefix (e.g. `orders/`).

**Auth**: Custom PIN-based auth. `SessionService` validates `X-Session-Token` request headers. Admin status is checked per-request via `isAdminRequest()` in trigger classes. The admin email is configured via `ADMIN_EMAIL` env var.

**Serialization**: Jackson with `@JsonIgnoreProperties(ignoreUnknown = true)` on all Cosmos models (required to handle `_rid`, `_self`, `_etag`, `_ts` metadata). Cosmos stores comic IDs as strings; `ComicService` converts between `String` and `int` on read/write. `pricePaid` is stripped from non-admin responses in `ComicTriggers`.

### Frontend (comic-book-db)

**Angular 16** SPA using standard `PathLocationStrategy` (HTML5 routing). Hash-based routing was removed because it conflicted with MSAL's redirect flow. Azure Static Web Apps serves the Angular app for all routes via rewrite rules. All routes are in `app-routing.module.ts`. Auth routes use `AuthGuard`; admin routes use `AdminGuard`. Auth state is held in `AuthService` via a `BehaviorSubject<User | null>` and persisted in `localStorage` as a session token.

**`ComicService`** is the central data service. It eagerly loads all comics into a `BehaviorSubject` on startup (`loadInitialData()`). Components consume `getCachedComics()` for in-memory data or call remote methods for fresh data. The base URL points to the deployed Azure Function App — to develop against local functions, uncomment the `localhost:7071` line in `ComicService`.

**AG Grid** (`ag-grid-angular` v31) is used in `StandaloneListComponent` for the inline-editable admin list view. Column definitions and cell renderers live in `standalone-list/`.

**Images**: Comics have up to four image IDs (`smallCachedImageId`, `largeCachedImageId`, `smallBackImageId`, `largeBackImageId`). Missing images fall back to `src/assets/comic-book-small.png` (dashboard) or `src/assets/comic-book-large.png` (detail page).

### Enum Contract

Enums are serialized via `@JsonValue` in Java and must match exactly in the Angular `Comic` model (`comic.ts`):

| Enum | Example values |
|---|---|
| `Era` | `"Golden Age"`, `"Silver Age"`, `"Bronze Age"`, `"Copper Age"`, `"Modern Age"` |
| `GradingCompany` | `"CGC"`, `"CBCS"`, `"PGX"`, `"NOT CERTIFIED"` |
| `ComicGrade` | Numeric double: `10.0`, `9.9`, `9.8` … `0.5` |
| `PageQuality` | `"WHITE"`, `"OFF-WHITE TO WHITE"`, `"CREAM"`, etc. |
| `CoverVariant` | `"Cover A"`–`"Cover M"`, `"Regular"`, `"Variant"`, `"Virgin"`, etc. |
| `NumberSentinel` | `"-1"`, `"NN"` |

When adding a new enum value, update both the Java enum and the Angular model/form.