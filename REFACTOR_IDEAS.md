# fn-comic-db Refactor Ideas

Code-smell punch list from a read-only audit of the `fn-comic-db/` Java Azure Functions backend. Pick whichever appeal — each item flags frontend-coupling risk where relevant.

## 1. Duplication / boilerplate

- **CORS + error response repetition** — every trigger manually sets `Access-Control-Allow-Origin`, `Content-Type`, etc. Helpers like `cors()`, `unauthorized()`, `badRequest()` exist but aren't used consistently. A central `ResponseBuilder` would collapse a lot of code.
- **JSON field extraction repeats** without null checks — e.g. `AdminTriggers.java:157-159`, `CartTriggers.java:79-80`. A `HttpHelper.requireString(payload, "comicId")` helper would standardize this.
- **Cosmos query → parse → list pattern** is copy-pasted in `CartService.java:57-65, 369-378, 386-394` and `ComicService`. A generic `<T> List<T> queryAndMap(SqlQuerySpec, Class<T>)` would consolidate it.
- **try/catch shapes are nearly identical** across triggers (`CartTriggers` alone has ~10 copies). A functional wrapper like `safeHandle(() -> ...)` could centralize the catch ladder.

## 2. Long methods / god classes

- `CartService.java` is **838 lines** and mixes lifecycle, admin ops, email composition, and return-event logic. Split candidates: `CartLifecycleService`, `CartAdminService`, `CartEmailService`.
- `ComicService.java` (533 lines) mixes pagination, filtering, set enrichment, and image-orphan cleanup. `getTopLevelComicsPaged()` is ~85 lines.
- `ComicTriggers.java` (542) and `AdminTriggers.java` (511) act as dispatchers — splitting `AdminTriggers` into `AdminCartTrigger`, `AdminComicTrigger`, `AdminOrderTrigger` would map better to the routes.

## 3. Singleton / DI coupling

- `getServiceInstance()` is called in 50+ spots. A thin constructor-injection seam (no framework needed — just pass services into constructors) would make this testable.
- `static SERVICE_INSTANCE` lazy init is **not thread-safe** in any service (e.g. `CartService.java:38`). Two cold-start threads could race. Use enum singleton or eager init.
- Services pull in many other services (`CartService` → `Comic/Bid/Discount/Archive/Email/Audit/Image/ActivityLog/User`). The graph is invisible unless you read every method.

## 4. Error handling

- ~~**`HttpStatus.I_AM_A_TEAPOT` (418) used as a generic error code** in `ComicTriggers.java:88, 297, 351, 389`. Clients/proxies may treat 418 oddly; should be 400 or 500.~~ ✅ **DONE** — all 8 `I_AM_A_TEAPOT` call sites in `ComicTriggers`, `ImageTriggers`, and `SearchTriggers` removed.
- ~~`e.getMessage()` and `ExceptionUtils.getMessage(e)` returned to clients in several triggers — leaks internals. Log the full exception, return generic text.~~ ✅ **DONE** — replaced with `HttpHelper.errorResponse`/`getErrorResponse` which return generic text for 5xx and only include the message on 4xx.
- ~~Broad `catch (Exception e)` swallows distinctions between validation errors and true failures. Map `IllegalArgumentException → 400`, `IllegalStateException → 409`, everything else → 500 consistently.~~ ✅ **DONE** — `HttpHelper.errorResponse(request, e)` now does this mapping centrally.
- Unchecked `payload.get("x").asText()` calls will NPE if the field is missing — many triggers have this.

## 5. Concurrency / race conditions

- ~~**Bidding state transitions are not atomic.** `BidService.java` reads → checks → writes the comic without optimistic concurrency (eTag). Two near-simultaneous bids could lose data. Cosmos supports `If-Match` headers — worth using here.~~ ✅ **DONE** — `ComicService.getComicByIdWithETag` + `updateComic(..., ifMatchETag)` added; `BidService.placeBid` and `startBidding` now use `If-Match`. A 412 PreconditionFailed is mapped to `IllegalStateException` → HTTP 409.
- **Cart claim checks are not atomic** — `isComicClaimed()` then `add()` in `CartService.java:86-125` allows two users to claim the same comic between the check and write. ✅ **PARTIALLY DONE** — Cart writes now use `If-Match` via a transient `etag` field on `Cart` (excluded from JSON), so same-user double-clicks and bid-finalize/cart races are caught. Cross-user comic-claim race still requires either a `claimedBy` field on the comic or a separate claims container — out of scope for this round.
- Mutable collections returned from getters (`Cart.getItems()` returns the live list). Defensive copies or `Collections.unmodifiableList` would be safer.

## 6. Magic strings / constants

- ~~Cart status values (`"OPEN"`, `"FINALIZING"`, `"FINALIZED"`, `"FULFILLED"`, `"DELETED"`) and payment status (`"PAID"`, `"UNPAID"`) are bare strings scattered across services and triggers. **A `CartStatus` enum with `@JsonValue` would prevent typos** — but the values would need to match what the frontend reads, so flag this as frontend-coupled.~~ ✅ **DONE** — `CartStatus` and `PaymentStatus` enums added with `@JsonValue`/`@JsonCreator`; field types tightened in `Cart` and `ArchivedOrder`; all string literals replaced. JSON wire format verified byte-identical.
- Container names in `CosmosDbClient.java` aren't centralized constants.
- Email subjects/bodies hardcoded inline in `CartService.java:696-813` — pull out to a `MessageTemplates` class or properties file.
- `EnvHelper` defaults (20, 10, 500, 7) lack inline javadoc explaining the units/intent.

## 7. Validation

- No request-body validation pattern. A small `RequestValidator` utility per trigger would standardize 400 responses with field-level error messages.
- `Integer.parseInt` on path/query params sometimes wrapped in try/catch, sometimes not (e.g. `ComicTriggers.java:132, 249`). No min/max checks for page numbers.

## 8. Logging

- Log messages frequently miss IDs/context (e.g. `"Error parsing cart"` with no cart ID).
- **PII in logs** — user names and emails appear in `CartService.java:169, 281, 352, 711`. Logging only the user ID would be safer.
- No correlation/request ID threaded through logs; debugging a single user's request is hard.
- High-volume info logs in `BidService.java:140` should probably be debug.

## 9. Dead / vestigial code

- `CartService.checkAndFinalize()` (lines 434-436) is a documented no-op kept "for backward compatibility". Mark `@Deprecated` with a removal date or just delete.
- `ComicBook.items` is populated for response only and never persisted — worth a comment or splitting into a view model.

## 10. Test coverage

Only three tests exist (`ComicBookNumberTest`, `ComicConditionTest`, `CsvToJsonConverterTest`). Highest-value gaps to fill, in rough priority order:

- `CartService` — state transitions, discount application, item add/remove, race conditions
- `BidService` — bid increment validation, lifecycle (opened → started → finalized), expiry
- `DiscountService.applyDiscounts()` — the math is complex and easy to break
- `ComicService` — pagination, set enrichment, deletion cascades
- Trigger smoke tests using Functions' `HttpRequestMessage` mocks

## 11. Model design

- **`BiddingState` flattened into `ComicBook` JSON via `@JsonUnwrapped` (or equivalent)** — this is a deliberate frontend contract. Don't refactor without coordinated frontend changes; worth a comment in `ComicBook.java` explaining why.
- `ComicBook.equals()`/`hashCode()` (lines 123-149) intentionally exclude `id`. Fine for duplicate detection, but two distinct comics with the same metadata will be `.equals()` — surprising in `Set`/`Map`. Consider naming this method `isLikelyDuplicate(other)` instead and giving Lombok the default equals.
- Lazy `getBiddingState()` that creates on null is inconsistent with how other models behave — pick one convention.

## 12. API surface inconsistencies

- Mixed naming: `submitOrder`/`unsubmitMyOrder` vs `fulfillOrder` vs `createComic`. The frontend already depends on these routes, so any change is a breaking change — but worth standardizing for new endpoints.
- Response shapes vary: sometimes `null`, sometimes `204 No Content`, sometimes `{ "deleted": true, "id": ... }`. Pick a convention for delete/action responses and document it.

## 13. Cosmos / persistence

- **N+1 / full-scan patterns:**
  - `CartService.removeItemAdmin()` (line 539) loads all active + open carts to find one comic.
  - `CartService.getClaimedComicMap()` (line 398) scans the carts container even when checking one comic.
  - `CartService.isComicClaimed()` runs a cross-partition query on every add-to-cart.
- **Unbounded reads:** `getComicsList()` and `deleteAllCarts()` have no page limits; risky as data grows.
- Verify partition-key filters are present on hot-path queries (`getActiveCart(userId)` etc.) so Cosmos doesn't fan out.
- Caching `claimedComicIds` in-memory with a short TTL would dramatically cut RU usage.

## 14. Misc

- `CosmosDbClient` has no startup validation — missing env vars surface as cryptic NPEs at first request rather than a clear boot-time error.
- Inline HTML email composition mixes presentation with business logic; even moving to text blocks (Java 17 `"""`) in a dedicated class would help.

---

## Suggested starting points

Three that give the most leverage with the least frontend risk:

1. ~~**Add `@JsonValue` enums for cart/payment status** — but only if the string values stay byte-identical to what the Angular `Comic`/`Cart` models expect. Pure refactor, no API change.~~ ✅ **DONE** (Apr 2026)
2. ~~**Centralize JSON parsing + error response in `HttpHelper`** — kills duplication across every trigger and lets you fix the I_AM_A_TEAPOT issue in one place.~~ ✅ **DONE** (Apr 2026)
3. ~~**Add eTag-based optimistic concurrency to bid + cart-claim writes** — fixes real race conditions without changing any public field shapes.~~ ✅ **DONE** (Apr 2026) — bid path fully covered; cart-claim cross-user race noted as future work.

### Verification

Built with `mvn clean package` (Java 17), all 29 existing unit tests pass, and a JSON probe confirmed `Cart`/`ArchivedOrder` serialization is byte-identical to the previous string-based representation (no `etag` field leaks into responses).
