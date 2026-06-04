# Serie Publish/Unpublish Feature

**Date:** 2026-06-04
**Status:** Approved

## Summary

Range-owned Serien (created in the admin view) are currently visible to all users immediately. This feature adds an explicit publish step: a range-owned Serie is hidden from regular users until an admin or range owner publishes it. Publishing can be toggled (publish and unpublish).

---

## Data Model

Add one field to the `Serie` JPA entity:

```java
@Column(nullable = false)
private boolean published = false;
```

- Hibernate `ddl-auto=update` adds the column on next restart; existing rows receive the DB default `false` — all currently visible range-owned Serien become unpublished automatically.
- `published` is only meaningful when `ownership = "range"`. For user-owned Serien it is ignored.
- `SerieResponse` (in `openapi.yaml`) gains a `published: boolean` field so the UI can reflect the state.

---

## Visibility Filtering

### `listSerien`

| Caller | range-owned Serien returned |
|---|---|
| Regular user | only where `published = true` |
| Admin / range owner (`isAdminOrOwner()`) | all, regardless of `published` |

### `getSerie` (by ID)

- Regular user fetching an unpublished range-owned Serie → **404** (not 403, to avoid leaking existence).
- Admin/range owner → always visible.

### Repository

New query method added to `SerieRepository`:

```java
List<Serie> findByOwnershipAndPublished(String ownership, boolean published);
```

The existing `findByOwnership("range")` is reserved for admin-only paths.

---

## API

### New endpoint

```
PATCH /api/serien/{id}/published
Authorization: Bearer <token>  (admin or range owner only)
Content-Type: application/json

Body:
{ "published": boolean }

Response 200: SerieResponse  (with updated published field)
Response 403: caller is not admin/range owner
Response 404: Serie not found
```

- Idempotent: setting `published = true` on an already-published Serie is a no-op (returns 200).
- Implemented in `SerieController` as `updateSeriePublished(UUID id, UpdateSeriePublishedRequest request)`.
- `openapi.yaml` is updated first; interface is regenerated via `./mvnw generate-sources`.

---

## Out of Scope

- No publish history or audit log.
- No per-user or per-range scoping of publish visibility — a published Serie is visible to all authenticated users on the platform.
- No notification to users when a Serie is published.
