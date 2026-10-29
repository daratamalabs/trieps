# Architecture and data

A monorepo with independent deployments. Android is the consumer of `/api/v1`; Laravel landing serves Blade pages and a waitlist in a separate database. User data lives in API SQL database; photo files in the API public storage disk. Use Laravel disk configuration to switch storage if needed.

## Core tables

- `users`, `personal_access_tokens`: email/password or verified Google identity; hashed token storage.
- `vehicles` → `service_rules` and `service_records`: owned by one rider. Rules specify both distance and month intervals.
- `tours` → `tour_members`: each member has its own vehicle and start/finish odometer and times.
- `checkpoints` → `checkpoint_visits`: owner-defined stops, per-rider arrival odometer.
- `track_points`: per-rider coordinates, accuracy, capture timestamp, unique `(user_id, client_id)` for retry deduplication.
- `fuel_entries`: per-rider odometer/liters/cost/full-tank marker.
- `communities` → `community_members`: owner plus pending/invited/active membership.
- `posts` → `post_likes`: authenticated global feed; photo paths scoped to uploader.

The migrations are the source of truth and support Laravel's database abstraction. Generate SQL for your selected engine with `php artisan migrate --pretend` against a disposable empty database. No production data dump is committed.

## Offline path

1. Rider creates and starts a tour while connected.
2. A visible foreground location service receives precise locations every ~5 seconds / 5 meters, rejecting accuracy worse than 100m.
3. Every point is written first to SQLite with a random client UUID, rider ID, tour ID, coordinates and timestamp.
4. WorkManager sends batches of <=500 points when connected. Server returns acknowledged IDs; only those IDs are marked synced. Failure retries with exponential backoff.
5. A unique database key makes retransmission safe. Old unsynced points remain on device. Unsynced queues are partitioned by authenticated user to avoid cross-account uploads.
6. Last server location is shown to other tour members. Missing connectivity never becomes a fabricated live position.

The app is not guaranteed to record after force-stop, device shutdown, revoked permissions, or vendor task killers. On task/process recreation Android can restart its sticky service; validate device-specific behavior. A persisted tour ID allows explicit resume from tour detail. Uninstalling the app deletes local unsynced data.

## Fuel calculation

A first full tank establishes an anchor. Sum all fuel added after it through the next full tank, then divide odometer delta by summed liters. The anchor's initial liters are excluded. Trailing partial fills after the last full tank are excluded until another full tank closes the interval. Return null rather than guess when a complete interval is unavailable.

## Sharing and privacy

Raw route endpoints and member positions require tour membership. A public-to-authenticated-users route card stores an offset/scale normalized silhouette, not raw latitude/longitude. Selected distance and economy values are snapshotted only when requested. Shape geometry can still reveal recognizable places; users should choose carefully what to post. Android image share uses FileProvider temporary read grants.

## Maintenance

For each rule, select latest service record by date then odometer. Due at `last_odo + interval_km` OR `last_date + interval_months`, whichever first. Warn within 300km or 14 days. No records means unknown, not a fabricated safe state. Android periodically fetches and notifies while respecting notification permission.

## Current constraints

Polling rather than WebSockets for positions; no turn-by-turn routing/geocoding; no email invitation to unregistered users; no server push notifications/FCM; no server-side retention job yet. These constraints are explicit and do not change offline point recording.
