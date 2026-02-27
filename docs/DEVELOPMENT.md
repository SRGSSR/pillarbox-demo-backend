# Development & API Usage Guide

This document provides a comprehensive guide on how to interact with the Pillarbox Backend.

Once the application is running, you can interact with it via the web console or the REST API.

## Web Console

For a visual management experience, use the built-in console.

* **URL**: [http://localhost:8080/console](http://localhost:8080/console)
* **Username / Password**: `dev/password`

## REST API

The REST API is divided into two primary functional areas:

1. [Management API (Protected)](#management-api-protected): Used for CRUD operations on media metadata, protected by OAuth2.
2. [Player API (Public)](#player-api-public): Playback requests with support for dynamic stream and DRM negotiation.

### Management API (Protected)

To access protected endpoints, you must first authenticate with the Keycloak server
(running on port `8081` by default).

```bash
TOKEN=$(curl -s -X POST "http://localhost:8081/realms/pillarbox/protocol/openid-connect/token" \
  -d "username=dev" \
  -d "password=password" \
  -d "grant_type=password" \
  -d "client_id=pillarbox-api" | jq -r '.access_token')
```

Use the `$TOKEN` to authorize POST requests to the management API.

```bash
curl -v --request POST \
  --url http://localhost:8080/v1/media \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  --data '{...}'
```

All endpoints below require the `Authorization: Bearer $TOKEN` header. You can find all the
definitions in [MediaRoute.kt][media-route-kt].

| Method     | Endpoint              | Description                                             |
|------------|-----------------------|---------------------------------------------------------|
| **GET**    | `/v1/media`           | List all media (supports `limit` and `offset` queries). |
| **GET**    | `/v1/media/{id}`      | Retrieve a specific media entity by ID.                 |
| **POST**   | `/v1/media`           | Create or fully update a media entity.                  |
| **PATCH**  | `/v1/media/{id}/tags` | Batch update tags for a specific media entity.          |
| **DELETE** | `/v1/media/{id}`      | Remove a media entity from the repository.              |

### Player API (Public)

The playback endpoints do not require a token and are open to all clients.

```bash
curl --request GET \
  --url http://localhost:8080/v1/player/media/urn:pillarbox:video:12345 \
  --header 'X-Accept-Stream-Type: application/dash+xml' \
  --header 'X-Accept-DRM: widevine'
```

Unlike the management API, these endpoints are **publicly accessible** (no Bearer token required)
They support content negotiation via custom headers. You can find all the definitions
in [PlayerMediaRoute.kt][player-media-route-kt].

| Method  | Endpoint                | Description                                |
|---------|-------------------------|--------------------------------------------|
| **GET** | `/v1/player/media`      | List all available playback entities.      |
| **GET** | `/v1/player/media/{id}` | Retrieve a specific playback entity by ID. |

#### Content Negotiation Headers

The player API uses headers to filter the best source for a specific device. If these headers are
omitted, the API returns a media item without a source.

| Header                 | Example Value          | Description                                                         |
|------------------------|------------------------|---------------------------------------------------------------------|
| `X-Accept-Stream-Type` | `application/dash+xml` | Filters for a specific MIME type (e.g., DASH, HLS).                 |
| `X-Accept-DRM`         | `WIDEVINE`             | Filters for a specific DRM key system (e.g., WIDEVINE, PLAY_READY). |

[media-route-kt]: ../src/main/kotlin/ch/srgssr/pillarbox/backend/entrypoint/web/MediaRoute.kt
[player-media-route-kt]: ../src/main/kotlin/ch/srgssr/pillarbox/backend/entrypoint/web/PlayerMediaRoute.kt
