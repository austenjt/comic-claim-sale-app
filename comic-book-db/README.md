# comic-book-db

Angular 16 frontend for a personal comic book collection database and claim sale platform.
The backend API is in the companion project [`fn-comic-db`](https://github.com/austenjt/fn-comic-db).

For build and deployment instructions, see [DEPLOY.md](./DEPLOY.md).

---

## Purpose

comic-book-db exists to support **Comic Book Claim Sales** — a selling format popular in online communities where a seller posts a list of comics at fixed prices and buyers claim items on a first-come, first-served basis.

The app gives a seller a single place to manage their collection, run claim sales, track who has claimed what, fulfill orders, and maintain a permanent order archive. It connects to an Azure Function App API backed by Azure Cosmos DB.

---

## Pages & Features

### Dashboard
Displays every comic currently for sale as a browsable grid of cover thumbnail cards. Each card shows the title (with issue number), series, grade, and price. Sold comics are not shown. The dashboard always fetches fresh data from the server to prevent stale claim status after fulfillments.

- **Claim button** — approved buyers see this on available comics. Clicking adds the comic to their cart.
- **In Your Cart** badge — shown on comics already in the buyer's own cart.
- **Claimed** badge — shown on comics in another buyer's active cart, with the claim date.
- **Award button** (admin, when Award Mode is enabled) — opens a modal to add any unclaimed comic to a selected user's cart at $0.00.

### Real-time Notifications (Toasts)
A toast notification system broadcasts claim/award/return events to all users across all pages of the app, not just the dashboard. Notifications are polled every 4 seconds from the backend.

- When a user **claims** a comic: they see "added to your cart"; all others see `"Title #N" added to UserName's cart — $XX.XX`.
- When admin **awards** a comic: all users see `"Title #N" awarded to UserName — FREE!`.
- When anyone **removes** a comic from their cart: all users (including the remover) see `"Title #N" Returned to sale`.
- Toasts stack in the upper-right corner, auto-dismiss after 30 seconds, and can be manually dismissed with an X button. They clear automatically on page navigation.

### Comic Detail
Full detail view for a single comic. The page heading combines title + issue number (e.g. "Amazing Spider-Man #121"). Includes:
- Front and back cover images with click-to-zoom
- Issue details, credits, condition (CGC/CBCS/raw), external references (GoCollect, Grand Comics Database)
- Sale info including date sold formatted in local time
- Claim/Release buttons for approved buyers

### My Cart (`/cart`)
Shows the buyer's active cart with items, prices, and subtotal. If a discount applies, a Discount line shows the amount and description.

Cart status progression:
```
OPEN → FINALIZING → FINALIZED → FULFILLED
```

- **Open** — freely add or remove items.
- **Submit Order** — starts a 24-hour review window (status: `FINALIZING`). Items can still be removed but not added.
- **Finalized** — after 24 hours the cart locks automatically. Seller arranges payment and shipping.
- **Fulfilled** — seller marks order complete; the comics are stamped as sold.

Removing an item from the cart (whether here or on a Comic Detail page) triggers a "Returned to sale" toast for all users.

### Orders (`/orders`)
Shows the buyer's complete order history — all fulfilled orders with their line items, prices, and discount totals. Order history is permanently archived and survives database resets.

### Comics Management (`/comics`)
Admin-only AG Grid of the full collection. The grid is hidden until **Refresh** is clicked, ensuring data is always current. Each row is inline-editable. Features:
- Toggle **For Sale** to show/hide a comic on the buyer dashboard without deleting it.
- Setting a **Sale Price** enables the Claim button for buyers.
- **Add** a new comic row directly in the grid.
- **Delete** a comic, its images, and release it from any active cart in one action.

### Manage Orders (`/admin/orders`)
Admin view of all active carts (submitted/finalized) and open carts. Each comic title is a clickable link showing "Title #Number". Features:
- **Release** — remove a comic from a buyer's cart. Triggers a "Returned to sale" toast for all users.
- **Mark as Fulfilled** — two-click confirmation. Archives the order permanently, stamps sold date/buyer on each comic, removes from active list.
- **Order Archive** — section at the bottom showing all previously fulfilled orders, permanently stored regardless of database resets.

### Sales & Discounts (`/admin/sales`)
Configure discount rules applied automatically when a buyer submits their order. Three discount types:
- **Flat Percentage Off** — e.g. 10% off everything.
- **Buy X Get One Free** — cheapest book in every group of X+1 is free.
- **Percentage Per X Books** — stacking percentage, e.g. 5% per 3 books.

Also includes a **Reset Database** section: type `RESET` to confirm, then delete all comics, images, carts, and discounts. Fulfilled orders are migrated to the archived-orders store first so order history is preserved.

### User Admin (`/admin/users`)
Manage pending account requests and existing users. Approve, reset PIN, suspend, and reactivate accounts.

---

## Image Handling

Cover images are uploaded through the API and stored in Cosmos DB. The backend generates a thumbnail (~104px tall) alongside the full-size image. The dashboard shows the thumbnail; the detail page shows the full image.

---

## Authentication

Custom PIN-based authentication — no third-party identity provider.

1. Buyer submits a registration form → account created as `PENDING`.
2. Admin approves → a 7-digit PIN is generated and shown once. Seller delivers it to the buyer.
3. Buyer logs in with email + PIN → receives a 7-day session token stored in `localStorage`.
4. On app load, `AuthService.loadSession()` restores session from the stored token via `GET /api/users/me`.

All requests include `X-Session-Token` via `AuthInterceptor`.

### Roles

| Role | Capabilities |
|---|---|
| **Admin** | Comics Management, User Admin, Manage Orders, Sales, Award mode, Reset Database |
| **Approved buyer** | Browse, Claim, My Cart, Orders, Profile |
| **Pending / Anonymous** | Read-only dashboard and detail pages |

---

## Shopping Cart & Claims

### Claim exclusivity
When a buyer claims a comic, the backend checks all active carts. If the comic is already in any cart, the request is rejected. The `GET /api/cart/claimed-ids` endpoint returns a live map of all currently claimed comic IDs visible to all visitors.

### Admin award
The admin can award any unclaimed comic to any approved user at $0.00 via `POST /api/awards`. If the user has no active cart, one is created.

### Archived Orders
When a cart is fulfilled, it is permanently snapshotted into the `archived-orders` Cosmos container as an `ArchivedOrder` with embedded `ArchivedOrderItem` records (title, price, claimed date — no comicId). This archive is never wiped by the Reset Database function, so order history persists across resets.

### Return events
When any user removes a comic from their cart, a short-lived `ReturnEvent` document (120-second TTL) is written to the `return-events` Cosmos container. The `/api/notifications` endpoint merges these with recent claim/award events and returns them to the frontend for polling-based broadcast.

## Color Scheme

https://coolors.co/palette/f0ead2-dde5b6-adc178-7b8f4b-a98467-6c584c-79675c

---

## Navigation

| Link | Visible to |
|---|---|
| Dashboard | Everyone |
| Start Here | Non-admin users |
| My Cart | Approved non-admin buyers |
| Orders | Approved non-admin buyers |
| Manage Orders | Admin only |
| Comics Management | Admin only |
| User Admin | Admin only |
| Sales | Admin only |
