# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

Two independent sub-projects, each with their own git history:

- `comic-book-db/` — Angular 20 frontend (SPA, deployed to Azure Static Web Apps)
- `fn-comic-db/` — Java 17 Azure Functions backend (deployed to Azure Function App)

**Always ask before modifying `fn-comic-db/` unless explicitly asked. The Java model objects are the source of truth for all field 
names, enum values, and serialization formats.

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
npm run build:deploy
# or manually:
npm run build  # builds to dist/comic-book-db/browser with base-href https://lightningcomics.rocks/
npm run deploy # deploys via @azure/static-web-apps-cli to production
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
- `ConfigTriggers` — serves `AppConfig` (feature flags + enum values) to frontend (`/api/config`)
- `BidTriggers` — bidding lifecycle (`/api/bid/*`)
- `ValidationTriggers` — field validation helpers

**Timer triggers** (scheduled Azure Functions, no HTTP route):
- `CartExpiryTrigger` — expires abandoned open carts
- `CartFinalizationTrigger` — auto-finalizes submitted orders after a deadline
- `ReturnEventPrunerTrigger` — cleans up old return events

**Service classes** (`org.example.functions.service.*`) contain all business logic. Services are singletons accessed via `getServiceInstance()` — no dependency injection framework is used.

**Data layer**: `CosmosDbClient` is a manual singleton that holds references to all ten Cosmos DB containers: `comics`, `images`, `users`, `sessions`, `carts`, `discounts`, `archived-orders`, `return-events`, `audit-logs`, `activity-logs`. The database is `comic-db` in Azure.

**Important Azure constraint**: The `admin/` route prefix is reserved by Azure Functions for its internal host management API. Any HTTP trigger routed under `admin/` will 404 in production. Use a different prefix (e.g. `orders/`).

**Auth**: Every handler calls one of three `AuthHelper` methods at the top of the handler body — no middleware:
- `AuthHelper.requireSession(request)` — returns User or null (any valid token)
- `AuthHelper.requireApproved(request)` — returns User or null (must be APPROVED or admin)
- `AuthHelper.requireAdmin(request)` — returns User or null (must be admin)
- `AuthHelper.isAdminRequest(request)` — boolean shorthand

Tokens are Entra (Azure AD) JWTs validated by `EntraJwtValidator`. The admin email is configured via `ADMIN_EMAIL` env var.

**Serialization**: Jackson with `@JsonIgnoreProperties(ignoreUnknown = true)` on all Cosmos models. `pricePaid` is annotated `@JsonView(Views.Admin.class)` and stripped from non-admin responses using Jackson serialization views. Cosmos stores comic IDs as strings; `ComicService` converts between `String` and `int` on read/write.

**Bidding**: `BiddingState` is a nested object inside `ComicBook` in Java, but its fields are flattened into the top-level JSON response (appearing as `enableBid`, `highBid`, `bidOpenedAt`, `bidStartedAt`, `currentBidderName`, etc. on the `Comic` TypeScript interface). The lifecycle is:
1. Admin sets `enableBid=true` on a comic (marks it bid-eligible)
2. Admin calls `POST /api/bid/open` → sets `bidOpenedAt`; the Bid button appears for users
3. First user bid sets `bidStartedAt`, starting the inactivity countdown (`biddingCycleMins` from config)
4. Each new bid resets the clock; when time expires the winner's comic is added to their cart with `wonViaBid=true`
5. `wonViaBid=true` cart items cannot be released and are excluded from all discounts

### Frontend (comic-book-db)

**Angular 20** SPA using standard `PathLocationStrategy` (HTML5 routing). Hash-based routing was removed because it conflicted with MSAL's redirect flow. Azure Static Web Apps serves the Angular app for all routes via rewrite rules. All routes are in `app-routing.module.ts`. Auth routes use `AuthGuard`; admin routes use `AdminGuard`.

**`ConfigService`** is loaded via `APP_INITIALIZER` before any component renders. It fetches `AppConfig` from `/api/config`, which bundles feature flags (`biddingMode`, `awardModeEnabled`, `emailEnabled`, `biddingCycleMins`) and all enum option lists. Falls back to default values if the call fails. Both `ConfigService` and `ComicService` have a commented-out `localhost:7071` base URL for local development — uncomment both when running the backend locally.

**Auth state** is held in `AuthService` via a `BehaviorSubject<User | null>` and persisted in `localStorage` as a session token. Two guards exist: `AuthGuard` (requires `isLoggedIn()`) and `AdminGuard` (requires `isAdmin()`). The `/sales` route has **no guard** — `AdminSalesComponent` is accessible to all visitors and renders differently based on `auth.isAdmin()`.

**`ComicService`** is the central data service. It eagerly loads all comics into a `BehaviorSubject` on startup (`loadInitialData()`). The backend returns set-member comics nested under their container in `items[]`; `ComicService.flattenComics()` unwraps these for the local cache. Set containers are identified by `number.sentinel === 'SET'`. Components consume `getCachedComics()` for in-memory data or call remote methods for fresh data.

**Admin editing**: All comic fields are edited inline on the Comic Detail page (`comic-detail/`) and Set Detail page (`set-detail/`). When an admin is logged in, value fields render as editable inputs/selects/checkboxes and a Save button appears in the page nav. Saves call `PUT /api/comics`. The Inventory page (`standalone-list/`) retains the Quick Add wizard and Set management (Add/Delete/Rename/View Sets) but no longer contains a bulk-edit grid.

**Cart grouping**: `CartComponent` and `AdminOrdersComponent` both use a `groupedRows(): CartRow[]` computed property that collapses flat `CartItem[]` lists into typed display rows (`type: 'single' | 'set'`). Set rows include all member comics under one header. `CartRow.wonViaBid` is true if any member comic has `wonViaBid=true`, which suppresses the Release button and discount display for that row.

**Sales modal**: `SalesModalService` (simple `show()`/`hide()` + `visible$`) drives `SalesModalComponent`, which lives in `AppComponent`'s template above the router outlet. On first load for unauthenticated users, `AppComponent` fetches discounts, shows the modal if any are active, and sets `sessionStorage` key `lcr-sales-modal-shown` to suppress it for the rest of the session.

**Images**: Comics have up to four image IDs (`smallCachedImageId`, `largeCachedImageId`, `smallBackImageId`, `largeBackImageId`). Missing images fall back to `src/assets/comic-book-small.png` (dashboard) or `src/assets/comic-book-large.png` (detail page).

**Global modal system**: `styles.css` defines `.modal-overlay` (backdrop, z-index, centering, animation) and `.modal` (dialog chrome, padding, border-radius). Size modifiers: `.modal--sm` (360px), `.modal--lg` (640px), `.modal--xl` (820px). New dialogs should compose these classes rather than re-implementing the overlay/shadow.

### Enum Contract

Enums are serialized via `@JsonValue` in Java and must match exactly in the Angular `Comic` model (`comic.ts`):

| Enum | Example values |
|---|---|
| `Era` | `"Golden Age"`, `"Silver Age"`, `"Bronze Age"`, `"Copper Age"`, `"Modern Age"` |
| `GradingCompany` | `"CGC"`, `"CBCS"`, `"PGX"`, `"NOT CERTIFIED"` |
| `ComicGrade` | Numeric double: `10.0`, `9.9`, `9.8` … `0.5` |
| `PageQuality` | `"WHITE"`, `"OFF-WHITE TO WHITE"`, `"CREAM"`, etc. |
| `CoverVariant` | `"Cover A"`–`"Cover M"`, `"Regular"`, `"Variant"`, `"Virgin"`, etc. |
| `NumberSentinel` | `"-1"`, `"NN"`, `"SET"` |

When adding a new enum value, update both the Java enum and the Angular model/form.
