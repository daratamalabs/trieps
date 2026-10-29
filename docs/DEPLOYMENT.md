# Deployment

## 1. Database and API

Use PHP 8.4+ (check `composer check-platform-reqs`; the current lock uses PHP 8.4+ dependencies), Composer, HTTPS, and MySQL 8+/PostgreSQL or SQLite for local testing. Point webroot to `api/public`, never the repository root.

```bash
cd api
composer install --no-dev --optimize-autoloader
cp .env.example .env
php artisan key:generate
# Edit .env: APP_ENV=production, APP_DEBUG=false, APP_URL=https://api.your-domain
# Set DB_CONNECTION=mysql and DB_HOST/DB_DATABASE/DB_USERNAME/DB_PASSWORD.
php artisan migrate --force
php artisan storage:link
php artisan optimize
```

Make only `storage` and `bootstrap/cache` writable by the web process. Keep `.env` inaccessible. Back up database and uploaded photos. Configure upload size >=8 MB and storage capacity. Production default CORS is not required for the native app; restrict it if adding browser clients later.

Set `INVITE_BASE_URL=https://your-landing-domain` on API. Landing handles `/invite/tour/{token}` and `/invite/community/{token}` and opens `trieps://invite/...`.

### Google login

Create Google OAuth clients for the Android package `id.trieps.app` with its signing SHA fingerprints and a Web application client. Put the **Web client ID** in API `GOOGLE_CLIENT_ID` and Android `TRIEPS_GOOGLE_CLIENT_ID`. The backend verifies signature, issuer, audience, expiry, and verified email through Google's PHP SDK. Android uses Credential Manager. A matching existing email account is not automatically linked: use email login.

### Email

Configure `MAIL_MAILER`, SMTP host/port/user/password/encryption and sender. Without SMTP, default `log` mailer writes reset email links locally and will not deliver messages. Reset links use API `APP_URL` and `/reset-password/{token}`.

### LiveKit voice

Create a LiveKit Cloud project or self-host LiveKit with TLS, WebSocket and TURN configured. Set backend `LIVEKIT_URL=wss://...`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`. Only the API holds the secret; it issues one-hour, tour-member-only microphone publishing tokens. SDK handles media transport. Use real devices on mobile networks to validate TURN and audio routing. The current client has no explicit token renewal UI for calls longer than one hour; connection recovery should be tested.

## 2. Landing

Deploy `landing/public` as a separate webroot. Follow the API Composer/key/migration commands for the landing with **its own database and APP_KEY**. `ANDROID_DOWNLOAD_URL` remains empty to display waitlist; set a real Play Store/APK URL only when available. This landing uses plain CSS/Blade: npm/Vite is not needed. Replace the draft privacy page with operational contact and retention terms before launch.

## 3. Android

Open `mobile` in Android Studio; Gradle wrapper 8.13, JDK 21, compile/target SDK 36. Build arguments:

```
-PTRIEPS_API_URL=https://api.your-domain/api/v1/
-PTRIEPS_GOOGLE_CLIENT_ID=WEB-CLIENT-ID.apps.googleusercontent.com
```

Use a private Android signing key; do not commit it. Configure release signing in your CI/Android Studio before `bundleRelease`. Existing repository contains **no release signing key**. A debug APK is for testing only. Custom-scheme links are supported via landing handoff. Verified Android App Links require your own domain and `assetlinks.json` with release signing fingerprint; not configured by default.

For map tiles, this implementation uses OpenStreetMap standard tiles through osmdroid with attribution. For production traffic, configure a tile provider appropriate to your expected volume and its terms; no bulk/offline tile downloading is implemented.

## 4. Test before public release

- Two real Android phones: background GPS with screen off, GPS disabled/re-enabled, process recreation, lost connection and reconnect, battery saver, and long rides.
- Solo/group start/checkpoints/finish and duplicate sync after network timeout.
- Member privacy, old position timestamps, invitations and link rotation.
- Google email account behavior and SMTP reset links.
- Two-party and multi-party voice, Bluetooth headset, audio focus, mute and call leave.
- Photo upload size, feed access, image sharing, per-vehicle reminder permission.
- Privacy policy, deletion/export, abuse reporting/moderation and Google Play data-safety declarations.

CI runs PHP tests and Android debug compilation. It does not replace these physical device tests.
