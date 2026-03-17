---
name: Box boolean fields in Java model
description: Use Boolean (boxed) not boolean (primitive) for optional fields in ComicBook.java to avoid false defaults from Cosmos DB
type: feedback
---

Use `Boolean` (boxed) instead of `boolean` (primitive) for optional fields in `ComicBook.java`.

**Why:** Primitive `boolean` defaults to `false` when a field is absent in Cosmos DB. This caused `isForSale` to serialize as `false` for all comics without the field explicitly set, making the Angular `isForSale !== false` filter drop them all from the dashboard. The set container showed because it had `isForSale: true` explicitly stored.

**How to apply:** Any new boolean field added to `ComicBook.java` (or other Cosmos model classes) should be `Boolean` (boxed) so null propagates correctly from the DB to the frontend. When switching from primitive to boxed, update Lombok-generated getter/setter call sites — `isX()` becomes `getIsX()` and `setX()` becomes `setIsX()`.
