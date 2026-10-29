# API v1

Base URL `https://YOUR-API/api/v1/`. Request/response JSON; send `Accept: application/json`. Protected endpoints use `Authorization: Bearer <token>`. Tokens expire after 30 days. Dates UTC on API; odometers in km, liters in L, costs in IDR. Standard Laravel 422 validation errors, 401 unauthenticated, 403 forbidden, 404 not found, 409 state conflict, 503 missing service configuration.

| Method | Path | Body / behavior |
|---|---|---|
| POST | auth/register | name,email,password (min 10 chars) → user,token |
| POST | auth/login | email,password → user,token |
| POST | auth/google | id_token → user,token; verified Google token |
| POST | auth/forgot-password | email; generic response |
| POST | auth/reset-password | email,token,password,password_confirmation |
| GET | me | current user |
| POST | auth/logout | revoke current token |
| GET/POST | vehicles | create: name,brand,model,year,plate?,odometer |
| PATCH | vehicles/{id} | name?,plate?,odometer? (nondecreasing) |
| GET/POST | vehicles/{id}/services | component,odometer,serviced_at YYYY-MM-DD,cost,notes? |
| PUT | vehicles/{id}/rules | component,interval_km,interval_months |
| GET | vehicles/{id}/reminders | per-component calculated due status |
| GET | reminders | all owned vehicles with reminders |
| GET/POST | tours | create: title,mode solo/group,start_name,finish_name,vehicle_id,start_odo |
| GET | tours/{id} | membership, checkpoints, visits, fuel,statistics,invite_url |
| POST | tours/join/{token} | vehicle_id,start_odo; groups only |
| POST | tours/{id}/rotate-invite | owner revokes old link |
| POST | tours/{id}/start | start current member |
| POST | tours/{id}/finish | finish_odo; update vehicle odometer |
| POST | tours/{id}/checkpoints | owner: name,position,latitude?,longitude? |
| POST | tours/{id}/checkpoints/{cp}/visit | odometer |
| POST | tours/{id}/points | points:[{client_id UUID,latitude,longitude,accuracy,recorded_at ISO8601}], <=500 |
| GET | tours/{id}/route | own route points, chronological |
| GET | tours/{id}/members | names + last location/timestamp |
| POST | tours/{id}/fuel | odometer,liters,cost,full_tank boolean |
| POST | tours/{id}/call-token | member-only LiveKit url,token |
| GET/POST | communities | create:name,description? |
| GET | communities/{id} | membership, members where permitted, owner-only invite_url |
| POST | communities/join/{token} | accept link |
| POST | communities/{id}/request | pending request |
| POST | communities/{id}/invite | owner: email of registered user |
| POST | communities/{id}/accept | accept own invitation |
| POST | communities/{id}/members/{user} | owner: action approve/reject |
| POST | communities/{id}/leave | member leave; owner transfer first |
| POST | communities/{id}/transfer | owner: user_id active member |
| POST | communities/{id}/rotate-invite | owner revoke old link |
| GET | posts?page=1 | paginated global feed (20/page) |
| POST | photos | multipart photo (jpg/png/webp <=8MB); returns path,url |
| POST | posts | caption,photos?[],tour_id?,route_card? |
| DELETE | posts/{id} | own post only |
| PUT/DELETE | posts/{id}/like | idempotent like/unlike |

Route card request: `{background:"forest"|"night"|"sand",show_distance:true,show_fuel:false}`. Requires a finished own tour. Server derives the silhouette; never trust client-provided summary figures. Feed returns normalized `shape:[[x,y],...]` with selected statistics, photos and author name. Raw GPS is not included in feed.

Example point sync:

```json
{"points":[{"client_id":"88082b26-dcc3-4e90-b0e2-e2240a51e975","latitude":-6.9175,"longitude":107.6191,"accuracy":8.5,"recorded_at":"2026-10-29T04:00:00Z"}]}
```

Server returns `{"accepted":["88082b26-dcc3-4e90-b0e2-e2240a51e975"]}`. Resending same UUID does not create duplicate points. Timestamps must fall within the member's trip window (with small tolerance); late upload after finishing is permitted for points captured during the ride.
