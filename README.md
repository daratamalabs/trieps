# Trieps ↗

Android touring app, Laravel API, and standalone Laravel landing page. Indonesian UI with forest/lime visual identity.

```
mobile/   Kotlin + Jetpack Compose, Android 8+
api/      Laravel 13, Sanctum, MySQL/PostgreSQL/SQLite migrations
landing/  Laravel 13 + Blade + responsive CSS
 docs/    Deployment, API contract, architecture, verification
```

## Run locally

Requirements: PHP 8.4+ (PHP 8.5 used for verification), Composer 2, Android Studio, JDK 21, Android SDK 36. Run `composer check-platform-reqs` on your host. No Node build is required for the landing.

```bash
cd api
composer install
cp .env.example .env
php artisan key:generate
# Configure DB_* in .env, or create database/database.sqlite for local SQLite.
php artisan migrate
php artisan storage:link
php artisan serve --host=127.0.0.1 --port=8000
```

Run the landing independently:

```bash
cd landing
composer install
cp .env.example .env
php artisan key:generate
# Configure its own DB. For SQLite: touch database/database.sqlite
php artisan migrate
php artisan serve --host=127.0.0.1 --port=8001
```

Open `mobile/` in Android Studio and configure `local.properties` with your SDK path (ignored). Use an HTTPS development tunnel to the API. Production cleartext traffic is disabled.

```bash
cd mobile
./gradlew :app:assembleDebug \
  -PTRIEPS_API_URL=https://YOUR-API/api/v1/ \
  -PTRIEPS_GOOGLE_CLIENT_ID=YOUR-WEB-OAUTH-CLIENT-ID
```

The URL must end in `/`. Never put LiveKit secrets or OAuth client secrets in Gradle properties/APK. See [deployment](docs/DEPLOYMENT.md).

## Implemented scope

- Email registration/login/logout/password reset, Sanctum access tokens, verified Google ID tokens.
- Solo/group tours; start/finish, checkpoints and each rider's checkpoint odometer; group invitation links, member positions with timestamps.
- Android foreground GPS service; SQLite offline point queue, UUID idempotency and WorkManager retries. Route recording continues while screen is off subject to OS/vendor restrictions.
- Refueling records and full-to-full km/L + L/100km. Initial fill is excluded; partial fills between full tanks are included.
- LiveKit microphone-only group rooms, mute/unmute, participant list and leave; foreground microphone service.
- Public-to-authenticated-users feed with photos, likes, own-post deletion; normalized route silhouettes with background/data choices and image sharing through Android Sharesheet.
- Community discovery, create, invitations, join requests, owner approval/rejection, accept invitation, leave and ownership transfer.
- Multiple vehicles, odometer updates, maintenance history, custom kilometer/month intervals, due/soon indicators, periodic Android reminders.
- Standalone landing with waitlist, invite deep-link handoff, and draft privacy information.

## Important operational boundaries

This is a first implementation, not a claim of production certification. Read [verification and remaining launch checks](docs/VERIFICATION.md).

- Creating/joining/starting/finishing tours currently requires internet. **GPS recording after starting works without internet.** Offline map tiles are not bundled, and map/navigation are not offline turn-by-turn navigation.
- Member tracking polls every 10 seconds while tour detail is open; offline riders show their last server position and timestamp. Voice requires internet and a configured LiveKit server.
- Google OAuth, SMTP, LiveKit and deployment domains must be configured by the operator. Email/Google accounts with the same email are deliberately not auto-linked.
- Service predictions use user-entered odometer/history and configured intervals, not vehicle sensors or AI diagnostics. No interval is assumed for a newly added vehicle.
- Maintenance notifications use periodic WorkManager, not exact alarms. They require notification permission and can be delayed by Android battery restrictions.
- Account deletion/export, content reporting/moderation, data retention, production privacy terms and app-store release signing must be completed before public launch.

No `.env`, local database, tokens, release keystores or third-party credentials belong in Git.
