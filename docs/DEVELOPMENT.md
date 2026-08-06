# Development & API Usage Guide

This document provides a comprehensive guide on how to interact with the Pillarbox Backend.
Once the application is running, you can interact with it via the web console or the REST API.

## Web Console

The Web Console provides a visual interface for managing media assets. It is built using **HTMX**
and **Pebble templates**, allowing for dynamic updates without full page reloads.

* **URL**: [http://localhost:8080/console](http://localhost:8080/console)

### Console Routes

The console exposes several endpoints that return either full HTML pages or partial HTML fragments.
Fragment and action endpoints are driven by HTMX. Mutating actions require the `Write` role, and
restoring from the bin requires the `Admin` role; folder and media mutations are additionally gated
by folder write access (see [Folder permissions](#folder-permissions)).

| Method     | Endpoint                                       | Type     | Description                                                                               |
|------------|------------------------------------------------|----------|-------------------------------------------------------------------------------------------|
| **GET**    | `/console`                                     | Page     | Renders the main dashboard/home page. Accepts `folderId` to open a folder.                |
| **GET**    | `/console/bin`                                 | Page     | Renders the bin (recently deleted media).                                                 |
| **GET**    | `/console/editor/{id?}`                        | Page     | Opens the media editor (empty for new, populated for an existing id). Accepts `folderId`. |
| **GET**    | `/console/editor/{id}/duplicate`               | Page     | Opens the editor pre-filled from an existing id (id cleared) for duplication.             |
| **GET**    | `/console/editor/import`                       | Page     | Opens the editor pre-filled with data imported from the Integration Layer. Requires `urn`, accepts `folderId`; answers `502` when the URN cannot be resolved. |
| **GET**    | `/console/fragments/media-grid`                | Fragment | Paginated media grid. Accepts `page`, `pageSize`, `folderId`, and `deleted`.              |
| **GET**    | `/console/fragments/folder-grid`               | Fragment | Grid of subfolders. Accepts `id` (parent folder, omitted for root).                       |
| **GET**    | `/console/fragments/folder-picker`             | Fragment | Folder picker dialog for moving a media item. Accepts `mediaId` and `folderId`.           |
| **GET**    | `/console/fragments/folder-picker-child`       | Fragment | Lazily loads child folders in the picker. Accepts `id` and `currentFolderId`.             |
| **GET**    | `/console/fragments/editor/{fragment}`         | Fragment | Returns a set of input fields for a specific editor row type.                             |
| **POST**   | `/console/actions/folder`                      | Action   | Creates a folder.                                                                         |
| **PATCH**  | `/console/actions/folder/{id}`                 | Action   | Renames a folder.                                                                         |
| **DELETE** | `/console/actions/folder/{id}`                 | Action   | Deletes a folder.                                                                         |
| **POST**   | `/console/actions/folder/{id}/media`           | Action   | Assigns a media item to a folder.                                                         |
| **DELETE** | `/console/actions/folder/{id}/media/{mediaId}` | Action   | Removes a media item's assignment from a folder.                                          |
| **POST**   | `/console/actions/media`                       | Action   | Saves a media entity (create/update) and triggers a client-side redirect.                 |
| **DELETE** | `/console/actions/media/{id}`                  | Action   | Soft-deletes a media entity (moves it to the bin).                                        |
| **POST**   | `/console/actions/media/{id}/restore`          | Action   | Restores a deleted media entity from the bin. Requires the `Admin` role.                  |

## REST API

The REST API is divided into two primary functional areas:

1. [Management API (Protected)](#management-api-protected): Used for CRUD operations on media
   metadata, protected by OAuth2.
2. [Player API (Public)](#player-api-public): Playback requests with support for dynamic stream and
   DRM negotiation.

### Management API (Protected)

Protected endpoints require authentication via the Keycloak server (running on port `8081` by
default) and are gated by role-based access control. A request without a valid token receives
`401 Unauthorized`; a valid token that lacks the required role receives `403 Forbidden`.

#### Test Users

The development Keycloak realm is seeded with three users (all passwords are `password`):

| Username | Roles                    | Access                       |
|----------|--------------------------|------------------------------|
| `reader` | `Read`                   | Read-only                    |
| `editor` | `Read`, `Write`          | Read + create/modify content |
| `admin`  | `Read`, `Write`, `Admin` | Unrestricted                 |

#### Obtaining a Token

```bash
TOKEN=$(curl -s -X POST "http://localhost:8081/realms/pillarbox/protocol/openid-connect/token" \
  -d "username=editor" \
  -d "password=password" \
  -d "grant_type=password" \
  -d "client_id=pillarbox-api" | jq -r '.access_token')
```

Use the `$TOKEN` to authorize requests to the management API.

```bash
curl -v --request POST \
  --url http://localhost:8080/v1/media \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data '{...}'
```

All endpoints below require the `Authorization: Bearer $TOKEN` header. You can find all the
definitions in [MediaRoute.kt][media-route-kt].

| Method     | Endpoint                 | Description                                                |
|------------|--------------------------|------------------------------------------------------------|
| **GET**    | `/v1/media`              | List all media (supports `limit` and `offset` queries).    |
| **GET**    | `/v1/media/{id}`         | Retrieve a specific media entity by ID.                    |
| **POST**   | `/v1/media`              | Create or fully update a media entity.                     |
| **PATCH**  | `/v1/media/{id}/tags`    | Batch update tags for a specific media entity.             |
| **DELETE** | `/v1/media/{id}`         | Soft-delete a media entity (moves it to the bin).          |
| **POST**   | `/v1/media/{id}/restore` | Restore a deleted media entity. Requires the `Admin` role. |

Mutations (`POST`, `PATCH`, `DELETE`) require the `Write` role and are additionally gated by the
write access of the media's folder (see [Folder permissions](#folder-permissions)). Restoring from
the bin is reserved for the `Admin` role.

#### Folder API

Folders provide a hierarchical way to organise media items. They support nesting via a `parentId`
field. You can find all the definitions in [FolderRoute.kt][folder-route-kt].

| Method     | Endpoint                                    | Description                                                                                |
|------------|---------------------------------------------|--------------------------------------------------------------------------------------------|
| **GET**    | `/v1/folder`                                | List folders. Accepts `limit` and `offset` for pagination; `parentId` to filter by parent. |
| **GET**    | `/v1/folder/{id}`                           | Retrieve a specific folder by ID.                                                          |
| **GET**    | `/v1/folder/{id}/media`                     | List media items assigned to a folder. Accepts `limit` and `offset`.                       |
| **POST**   | `/v1/folder`                                | Create a new folder.                                                                       |
| **PATCH**  | `/v1/folder/{id}`                           | Update an existing folder.                                                                 |
| **DELETE** | `/v1/folder/{id}`                           | Delete a folder.                                                                           |
| **POST**   | `/v1/folder/{id}/media`                     | Assign a media item to a folder.                                                           |
| **DELETE** | `/v1/folder/{id}/media/{mediaId}`           | Remove a media item's assignment from a folder.                                            |
| **GET**    | `/v1/folder/{id}/permission`                | List the grants effective on a folder (its own grants plus inherited ancestor grants).     |
| **POST**   | `/v1/folder/{id}/permission`                | Add an access grant to a folder.                                                           |
| **DELETE** | `/v1/folder/{id}/permission/{permissionId}` | Remove a grant from a folder.                                                              |

#### Folder permissions

By default, any user with the `Write` role may modify any folder and its media. A folder becomes
**restricted** as soon as it (or one of its ancestors) carries at least one grant: from then on
only granted subjects (and `Admin` users) may write to it and its descendants. Grants are inherited
downwards. Managing a folder's grants requires write access to that folder, so editors with access
can delegate it without involving an administrator. Reading is always open to any authenticated
user.

A grant targets exactly one subject: a user, a team, or a role, so a `POST` body must set exactly
one of `oidcSub`, `teamId`, or `role`. `canWrite` defaults to `true`.

```bash
curl --request POST \
  --url http://localhost:8080/v1/folder/{id}/permission \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data '{"teamId": "team-123", "canWrite": true}'
```

| Field      | Type      | Description                                                                                  |
|------------|-----------|----------------------------------------------------------------------------------------------|
| `oidcSub`  | `string`  | Grant for a single user (their OIDC subject). Mutually exclusive.                            |
| `teamId`   | `string`  | Grant for all members of a team. Mutually exclusive.                                         |
| `role`     | `string`  | Grant for all holders of a role (e.g. re-open a subtree to all editors). Mutually exclusive. |
| `canWrite` | `boolean` | Whether the grant confers write access. Defaults to `true`.                                  |

#### Team API

Teams group users so a single folder grant can cover many people. Listing teams and their members
requires the `Write` role (e.g. to pick grant subjects); creating or deleting teams and managing
membership requires the `Admin` role. You can find all the definitions
in [TeamRoute.kt][team-route-kt].

| Method     | Endpoint                         | Role    | Description                                            |
|------------|----------------------------------|---------|--------------------------------------------------------|
| **GET**    | `/v1/team`                       | `Write` | List teams (supports `limit` and `offset`).            |
| **GET**    | `/v1/team/{id}`                  | `Write` | Retrieve a specific team by ID.                        |
| **GET**    | `/v1/team/{id}/member`           | `Write` | List a team's members (supports `limit` and `offset`). |
| **POST**   | `/v1/team`                       | `Admin` | Create a team (body: `{ "name": "..." }`).             |
| **DELETE** | `/v1/team/{id}`                  | `Admin` | Delete a team.                                         |
| **POST**   | `/v1/team/{id}/member`           | `Admin` | Add a member (body: `{ "oidcSub": "..." }`).           |
| **DELETE** | `/v1/team/{id}/member/{oidcSub}` | `Admin` | Remove a member from a team.                           |

#### User API

Users are provisioned from the OIDC provider on login. Listing users requires the `Write` role
(e.g. to pick grant subjects); inspecting a user's active sessions requires the `Admin` role.
You can find all the definitions in [UserRoute.kt][user-route-kt].

| Method  | Endpoint                | Role    | Description                                                |
|---------|-------------------------|---------|------------------------------------------------------------|
| **GET** | `/v1/user`              | `Write` | List users (supports `limit` and `offset`).                |
| **GET** | `/v1/user/{id}`         | `Write` | Retrieve a specific user by OIDC subject.                  |
| **GET** | `/v1/user/{id}/session` | `Admin` | List a user's active sessions, most recently active first. |

### Player API (Public)

The playback endpoints do not require a token and are open to all clients, unlike the management
API. They support content negotiation via query parameters or custom headers.
You can find all the definitions in [PlayerMediaRoute.kt][player-media-route-kt].

| Method  | Endpoint                       | Description                                                          |
|---------|--------------------------------|----------------------------------------------------------------------|
| **GET** | `/v1/player/media`             | List all available playback entities. Accepts `limit` and `offset`.  |
| **GET** | `/v1/player/media/{id}`        | Retrieve a specific playback entity by ID.                           |
| **GET** | `/v1/player/folder/{id}/media` | List media items assigned to a folder. Accepts `limit` and `offset`. |

#### Content Negotiation

The player API filters the best source for a specific device using query parameters or headers.
Query parameters take precedence over headers when both are provided.
If neither is supplied, the API returns a media item without a source.

**Query Parameters**

| Parameter     | Example Value                      | Description                                                                                                                         |
|---------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `stream-type` | `application/dash+xml`             | Preferred MIME type (e.g., DASH, HLS). Comma-separated for multiple values.                                                         |
| `drm`         | `com.widevine.alpha;HW_SECURE_ALL` | Preferred DRM key system, optionally followed by `;` and the highest supported security level. Comma-separated for multiple values. |
| `platform`    | `android`                          | Target platform whose ready-made preferences are used as defaults. One of `android`, `apple`, `web`.                                |

Example:

```bash
curl --request GET \
  --url 'http://localhost:8080/v1/player/media/urn:pillarbox:video:12345?stream-type=application/dash+xml&drm=com.widevine.alpha;HW_SECURE_ALL'
```

**Headers (fallback)**

| Header                 | Example Value                      | Description                                                                                                                         |
|------------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `X-Accept-Stream-Type` | `application/dash+xml`             | Preferred MIME type (e.g., DASH, HLS). Comma-separated for multiple values.                                                         |
| `X-Accept-DRM`         | `com.widevine.alpha;HW_SECURE_ALL` | Preferred DRM key system, optionally followed by `;` and the highest supported security level. Comma-separated for multiple values. |
| `X-Target-Platform`    | `android`                          | Target platform whose ready-made preferences are used as defaults. One of `android`, `apple`, `web`.                                |

Example:

```bash
curl --request GET \
  --url http://localhost:8080/v1/player/media/urn:pillarbox:video:12345 \
  --header 'X-Accept-Stream-Type: application/dash+xml' \
  --header 'X-Accept-DRM: com.widevine.alpha;HW_SECURE_ALL'
```

**Platform Presets**

The `platform` parameter and `X-Target-Platform` header select a ready-made preference list for
the target platform: stream types and DRM systems a typical `android`, `apple`, or `web` client
supports, in a sensible priority order. Explicit `stream-type` and `drm` values take precedence:
each one replaces the corresponding part of the preset, while the preset fills whatever is
omitted.

Only one platform can be targeted at a time, unknown platforms are rejected with `400 Bad Request`.

```bash
curl --request GET \
  --url 'http://localhost:8080/v1/player/media/urn:pillarbox:video:12345?platform=android'
```

**Security Levels**

The `drm` parameter and `X-Accept-DRM` header accept either DRM-native security levels or EME
robustness levels. Robustness levels are automatically resolved to the corresponding DRM-native
level.

| EME Robustness Level | Widevine | PlayReady |
|----------------------|----------|-----------|
| `SW_SECURE_CRYPTO`   | L3       | SL2000    |
| `SW_SECURE_DECODE`   | L3       | SL2000    |
| `HW_SECURE_CRYPTO`   | L2       | SL2000    |
| `HW_SECURE_DECODE`   | L2       | SL2000    |
| `HW_SECURE_ALL`      | L1       | SL3000    |

Native levels (`L1`, `L2`, `L3`, `SL2000`, `SL3000`) are still accepted and passed through
unchanged.

When no security level is provided for a known key system, the weakest level of that system is
assumed (Widevine `L3`, PlayReady `SL2000`), so sources requiring a stronger level are excluded.
Unknown key systems remain unconstrained and match any security level.

[folder-route-kt]: ../src/main/kotlin/ch/srgssr/pillarbox/backend/entrypoint/web/api/FolderRoute.kt
[media-route-kt]: ../src/main/kotlin/ch/srgssr/pillarbox/backend/entrypoint/web/api/MediaRoute.kt
[player-media-route-kt]: ../src/main/kotlin/ch/srgssr/pillarbox/backend/entrypoint/web/api/PlayerMediaRoute.kt
[team-route-kt]: ../src/main/kotlin/ch/srgssr/pillarbox/backend/entrypoint/web/api/TeamRoute.kt
[user-route-kt]: ../src/main/kotlin/ch/srgssr/pillarbox/backend/entrypoint/web/api/UserRoute.kt
