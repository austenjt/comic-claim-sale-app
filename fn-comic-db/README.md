# fn-comic-db

Java 17 Azure Function App providing the REST API for the comic book collection and claim sale platform.
The Angular frontend is in the companion project [`comic-book-db`](https://github.com/austenjt/comic-book-db).

For build and deployment instructions, see [DEPLOY.md](./DEPLOY.md).

---

## Purpose

fn-comic-db exists to support **Comic Book Claim Sales** — a selling format popular in online communities where a seller posts a list of comics at fixed prices and buyers claim items on a first-come, first-served basis.

The API handles comic management, cover image storage, buyer authentication, cart lifecycle, order archiving, real-time broadcast notifications, and database administration.

## Overview

Serverless HTTP API built on Azure Functions. All data is stored in Azure Cosmos DB. Authentication to Cosmos DB uses Azure Managed Identity — no connection strings or keys in code or configuration.

---

## API Endpoints

### Comics — `/api/comics`

| Method | Route | Description |
|---|---|---|
| `GET` | `/api/comics` | List all comics. Supports `?pageNumber=&pageSize=` for pagination. |
| `GET` | `/api/comics/{id}` | Get a single comic by Internal ID. |
| `POST` | `/api/comics` | Create a new comic record. Optionally accepts `?id=` to assign a specific ID. |
| `PUT` | `/api/comics` | Update an existing comic record (full replace). |
| `DELETE` | `/api/comics/{id}` | Delete a comic by Internal ID, remove its images, and release it from any active cart. |

### Images — `/api/images`

| Method | Route | Description |
|---|---|---|
| `GET` | `/api/images` | List all stored image names. |
| `GET` | `/api/images/{name}` | Retrieve an image by name. Returns raw image bytes with the correct `Content-Type`. |
| `PUT` | `/api/images/{name}` | Upload or replace a named image. Accepts `multipart/form-data` with a `file` field. Use `?force=true` to upsert. |
| `DELETE` | `/api/images/{name}` | Delete an image by name. |

### Comic Image Upload — `/api/comics/{id}/image`

| Method | Route | Description |
|---|---|---|
| `POST` | `/api/comics/{id}/image` | Upload a front cover image. Stores original as `{id}-large.png` and generates a ~104px thumbnail as `{id}-small.png`. Returns the updated comic. |
| `POST` | `/api/comics/{id}/backimage` | Upload a back cover image. Stores as `{id}-back-large.png` with a thumbnail. Returns the updated comic. |

### Data Operations — `/api/comics/data`

| Method | Route | Description |
|---|---|---|
| `POST` | `/api/comics/data` | Bulk-load comics from a GoCollect `.csv` export. |
| `GET` | `/api/comics/data/prune` | Delete stored images not referenced by any comic record. |

---

## Services (Comics)

| Class | Responsibility |
|---|---|
| `ComicService` | CRUD operations against the Cosmos DB `comics` container. Also handles `deleteAllComicsAndImages()` for the database reset. |
| `ImageService` | Store, retrieve, and delete images in the Cosmos DB `images` container. Images are Base64-encoded in Cosmos. |
| `ImageResizeService` | Resize images to a target height while preserving aspect ratio. Uses Apache Commons Imaging. |

---

## Authentication

Custom PIN-based system backed by the `users` and `sessions` Cosmos containers.

### User Endpoints — `/api/users`

| Method | Route | Auth | Description |
|---|---|---|---|
| `POST` | `/api/users/register` | None | Submit an account request. Creates a `PENDING` user. If email matches `ADMIN_EMAIL`, auto-approved as admin. |
| `POST` | `/api/users/login` | None | Submit email + PIN. Returns a session token and user info on success. |
| `POST` | `/api/users/logout` | Session token | Deletes the session. |
| `GET` | `/api/users/me` | Session token | Returns the current user's profile. |
| `GET` | `/api/users/pending` | Admin session | Lists all `PENDING` users. |
| `GET` | `/api/users/approved` | Admin session | Lists all approved non-admin users. |
| `POST` | `/api/users/{id}/approve` | Admin session | Approves a user, generates a 7-digit PIN (returned once), stores hash+salt. |
| `POST` | `/api/users/{id}/resetPin` | Admin session | Generates a new PIN for an existing user. Old PIN stops working immediately. |
| `POST` | `/api/users/{id}/suspend` | Admin session | Suspends a user (blocks login). |
| `POST` | `/api/users/{id}/reactivate` | Admin session | Reactivates a suspended user. |
| `PUT` | `/api/users/profile` | Session token | Update name, address, phone, and payment notes. |

### PIN Security

PINs are never stored in plain text. On approval: random salt (UUID) + 7-digit PIN → `SHA-256(pin + salt)` → only hash and salt are persisted. The plain PIN is returned once to the admin only.

### Session Management

Sessions are stored in the `sessions` Cosmos container. Each document has a UUID token, `userId`, and an expiry 7 days from creation. Token is passed in the `X-Session-Token` request header and validated on every protected endpoint.

### Admin Bootstrap

Register using the `ADMIN_EMAIL` env var email address — the account is auto-approved. Set the PIN manually in Cosmos Data Explorer:
1. Pick a 7-digit PIN and a salt string.
2. Compute: `echo -n "<pin><salt>" | shasum -a 256`
3. Edit the admin user document: set `pinSalt`, `pinHash`, and `status: "APPROVED"`.

### Services (Auth)

| Class | Responsibility |
|---|---|
| `UserService` | Register, look up, approve users; PIN generation and verification. |
| `SessionService` | Create/validate/delete sessions. `deleteAllExcept(token)` used during database reset to preserve admin's session. |

---

## Shopping Cart & Claims

### Cart Endpoints — `/api/cart`

| Method | Route | Auth | Description |
|---|---|---|---|
| `GET` | `/api/cart` | Approved session | Get the current user's active cart. Returns `null` if none. |
| `POST` | `/api/cart/items` | Approved session | Add a comic to the cart. Fails if already claimed or cart is not `OPEN`. Snapshots `comicTitle`, `comicNumber`, and `price` onto the cart item. |
| `DELETE` | `/api/cart/items/{comicId}` | Approved session | Remove an item. Writes a `ReturnEvent` for broadcast. Allowed while `OPEN` or `FINALIZING`. |
| `POST` | `/api/cart/submit` | Approved session | Submit the order. Sets `FINALIZING` and records `finalizeAfter = now + 24h`. |
| `GET` | `/api/cart/claimed-ids` | None | Returns `Map<comicId, claimedAt>` of all comics in any active cart. |
| `GET` | `/api/cart/history` | Approved session | Returns the user's archived (fulfilled) order history from `archived-orders`. |

### Order Management Endpoints — `/api/orders`

| Method | Route | Auth | Description |
|---|---|---|---|
| `GET` | `/api/orders` | Admin session | List all non-fulfilled carts across all buyers. |
| `GET` | `/api/orders/open` | Admin session | List all open (not yet submitted) carts with at least one item. |
| `POST` | `/api/orders/{cartId}/fulfill` | Admin session | Mark a cart `FULFILLED`. Stamps `dateSold` and `soldTo` on each comic, archives the cart to `archived-orders`. |
| `DELETE` | `/api/orders/claim/{comicId}` | Admin session | Admin-removes a comic from any buyer's cart. Writes a `ReturnEvent`. |
| `GET` | `/api/orders/archived` | Admin session | Returns all permanently archived orders from `archived-orders`. |
| `POST` | `/api/awards` | Admin session | Award a comic to a user at $0.00. Creates or uses their active cart. |
| `GET` | `/api/notifications` | None | Returns recent claim, award, and return events from the last 30 seconds. Used for polling-based broadcast toasts. |
| `POST` | `/api/reset` | Admin session | Database reset: migrates fulfilled carts to archive, then deletes all carts, discounts, comics, images, and all sessions except the admin's. |

> **Note:** Routes starting with `admin/` are reserved by Azure Functions for platform management. Admin routes use `/api/orders` instead.

### Cart Status Lifecycle

```
OPEN → FINALIZING → FINALIZED → FULFILLED
```

| Status | Description |
|---|---|
| `OPEN` | Buyer is adding/removing items. |
| `FINALIZING` | Order submitted. 24-hour window active. Items can be removed, not added. |
| `FINALIZED` | Deadline passed. Cart locked. Lazily transitioned on next read. |
| `FULFILLED` | Admin marked as shipped. Excluded from active order queries. |

### Claim Exclusivity

`CartService.isComicClaimed()` queries all non-fulfilled carts before adding any item. If the comic appears in any active cart, the request is rejected with `409 Conflict`.

### Comic Number Snapshot

When a comic is added or awarded to a cart, the `comicNumber` is formatted and snapshotted onto the `CartItem` (e.g. `#121`, `#NN`). This ensures the number displays correctly in cart views even if the comic record changes later.

### Services (Cart & Orders)

| Class | Responsibility |
|---|---|
| `CartService` | Full cart lifecycle: add/remove items, submit, fulfill, admin unclaim, claimed map. On remove: writes `ReturnEvent`. On fulfill: calls `ArchiveService`. `getRecentClaimEvents()` merges claim/award (from cart JOIN) and return events (from `return-events`) for the notifications endpoint. |
| `ArchiveService` | Snapshots fulfilled carts to `archived-orders`. `archiveCart()` is idempotent. `migrateAll()` archives all existing fulfilled carts (called on database reset). |

---

## Archived Orders

When a cart is fulfilled, `ArchiveService.archiveCart()` writes a permanent `ArchivedOrder` document to the `archived-orders` Cosmos container. The snapshot contains:
- `userId`, `userName`, `userEmail`, `createdAt`, `fulfilledAt`
- `items` — an array of `ArchivedOrderItem` (title, price, claimedAt — **no comicId**)
- `discountAmount`, `discountDescription`

This archive is never deleted by the database reset, so order history survives even after all comics are wiped.

---

## Real-time Broadcast Notifications

`GET /api/notifications` (public, no auth) returns a list of `ClaimNotification` objects for events in the last 30 seconds. The frontend polls this every 4 seconds.

**Event types:**
- `CLAIM` — comic added to a cart via a buyer claim
- `AWARD` — comic added to a cart by admin at $0.00
- `RETURN` — comic removed from a cart (user or admin)

Claim/Award events are sourced from a Cosmos JOIN query across active cart items. Return events come from the `return-events` container (120-second TTL documents written by `CartService.writeReturnEvent()`).

`ClaimNotification` fields: `eventType`, `comicId`, `comicTitle`, `comicNumber`, `userName`, `price`, `claimedAt`.

---

## Discounts

### Discount Endpoints — `/api/discounts`

| Method | Route | Auth | Description |
|---|---|---|---|
| `GET` | `/api/discounts` | Admin session | List all discount rules. |
| `POST` | `/api/discounts` | Admin session | Create a discount rule. |
| `PUT` | `/api/discounts/{id}` | Admin session | Update a discount rule. |
| `DELETE` | `/api/discounts/{id}` | Admin session | Delete a discount rule. |

Three discount types: `RAW_PERCENTAGE`, `BUY_X_GET_ONE_FREE`, `PERCENTAGE_PER_X_BOOKS`. Discounts are snapshotted onto the cart at submit time. `DiscountService.deleteAllDiscounts()` is used during database reset.

---

## Cosmos DB Containers

| Container | Partition key | TTL | Purpose |
|---|---|---|---|
| `comics` | `/id` | — | Comic book records |
| `images` | `/id` | — | Base64-encoded cover images |
| `users` | `/id` | — | User accounts |
| `sessions` | `/id` | — | Session tokens (7-day soft expiry) |
| `carts` | `/id` | — | Active and fulfilled carts |
| `discounts` | `/id` | — | Discount rule definitions |
| `archived-orders` | `/id` | — | Permanent fulfilled order snapshots |
| `return-events` | `/id` | 120s | Short-lived return events for broadcast notifications |

---

## Data Model

Comics are stored as JSON documents partitioned by their integer ID (as string). Images are stored separately as Base64-encoded documents. Users and sessions each have their own container, partitioned by `/id`. Carts are partitioned by `/id`; cross-cart queries use cross-partition queries.

`@JsonIgnoreProperties(ignoreUnknown = true)` is required on all Cosmos-mapped models to handle Cosmos metadata fields (`_rid`, `_self`, `_etag`, `_ts`).
