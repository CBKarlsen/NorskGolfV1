# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend (run from `backend/`)
```bash
./mvnw spring-boot:run                    # Start backend on :8080
./mvnw test                               # Run tests
./mvnw test -Dtest=ClassName#methodName   # Run a single test
./mvnw -B package                         # Build JAR
```

No Spring profile flag is needed — local overrides live in `secrets.properties` (see Database below). `application-local.properties` exists but is inert unless a `local` profile is activated.

### Frontend (run from `frontend/`)
```bash
npm start                     # Dev server on :3000 (proxies /api/* to :8080)
npm test -- -t "test name"    # Run a single test
npm run build
npm run deploy                # build + wipe and repopulate backend/src/main/resources/static/
npm run lint                  # Biome check + format (tabs, double quotes)
npm run lint:ci               # What CI runs — fails instead of fixing
```

Local dev requires both backend and frontend running. The CRA proxy (`frontend/package.json` → `"proxy"`) routes `/api` calls to :8080.

## Architecture

Single-JAR deployment: the React build is copied into `backend/src/main/resources/static/` and served by Spring Boot. `SpaRedirectController` forwards any extensionless unknown path to `index.html`; `WebConfig` forwards `/`.

### Backend (`backend/src/main/java/fritids/norskgolf/`)

Layered: **Controller → Service → Repository → Entity**

- `controller/` — REST under `/api`. Thin — delegate to services.
- `service/` — `GolfService`, `FriendService`, `UserService`, `LeaderboardService`, `CourseSyncService`
- `entities/` — `User`, `Course`, `Round`, `PlayedCourse` (user↔course join, unique on the pair), `Friendship`
- `SecurityConfig.java` — Google OAuth2, CORS, CSRF, and the public-path allowlist
- `NorskGolfApplication.java` — Entry point + a `CommandLineRunner` that backfills one test-user row (no-ops if `testuser` is absent)

### Auth and CSRF

1. Frontend calls `GET /api/auth/me` on load; 401 → show Google login
2. OAuth success → Spring session cookie → redirect to `/`

**The app gets exactly one cookie, and it must be called `__session`.** Firebase Hosting strips every other cookie before forwarding to Cloud Run (it is part of the CDN cache key), so `server.servlet.session.cookie.name=__session` is load-bearing, not cosmetic — rename it and every request arrives anonymous and login fails at the OAuth callback.

**`app.frontend.url` is the CORS allowlist: a comma-separated list of absolute origins**, first one canonical (post-logout redirect). Behind Firebase the app sees the `run.app` host while the browser says `golfjakten.no`, so Spring treats the SPA as cross-origin and enforces CORS on it. Browsers send `Origin` on writes but not on same-origin reads, so a bad value here breaks every POST/DELETE while GETs look fine — and **curl cannot reproduce it, because curl sends no `Origin` header**. Reach for a preflight (`OPTIONS` with `Origin` + `Access-Control-Request-Method`) or server-side debug logging instead. `GET /api/csrf` exists so the CSRF round-trip can be exercised without logging in.

That rules out a cookie-based CSRF token. `SecurityConfig` uses `HttpSessionCsrfTokenRepository`; `AuthController` returns the token as `csrfToken` on `/api/auth/me`, `App.js` hands it to `setCsrfToken` in `api.js`, and mutating fetches spread `csrfHeaders()` into their headers alongside `credentials: "include"`. Don't reintroduce `document.cookie` reads for this.

### SPA routes vs security

React routes are Norwegian (`/kart`, `/oversikt`, `/venner`). `SecurityConfig` permits SPA shell GETs as a class — `RegexRequestMatcher.regexMatcher(GET, "/[^/.]*(\\?.*)?")` — mirroring the extensionless single-segment paths `SpaRedirectController` forwards to `index.html`. A new React route therefore needs **no** security change. Every `/api/**` path contains a slash, so it can never match that rule; public API endpoints are listed explicitly (`GET /api/auth/me`, `GET /api/courses`).

A `DelegatingAuthenticationEntryPoint` in `SecurityConfig` splits the unauthenticated response by path: `/api/**` gets a bare **401**, everything else is redirected to `/login`. Built explicitly rather than via `defaultAuthenticationEntryPointFor`, because `oauth2Login` registers its own entry point into the same mapping table and the fallback would otherwise depend on configurer insertion order.

### Course data

`backend/src/main/resources/golf_clubs.json` is the curated list of Norwegian golf clubs and the source of truth for the collection game — one entry per club, carrying `clubId`, name, coordinates, municipality, county and hole count.

`CourseSyncService` reconciles it into the `course` table on every boot: matches by `clubId`, else by normalised name within 3 km; updates matched rows in place so `played_course` and `round` foreign keys survive; inserts new clubs; and **deactivates** courses the list no longer contains rather than deleting them, so a logged round is never lost. Ambiguous matches are logged and skipped for a human to resolve.

Set `app.clubs.dry-run=true` to log what would change without writing.

### Database

| Environment | DB | Config |
|---|---|---|
| Local dev | H2 (file-based) | `jdbc:h2:file:./data/golfjakten`, set in `secrets.properties` |
| Production | PostgreSQL | `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` injected by the host — **nothing in this repo configures it** |

`ddl-auto=update`. Local datasource + OAuth credentials + `app.frontend.url` all live in the gitignored `backend/src/main/resources/secrets.properties`, imported unconditionally by `application.properties`. Put local-only settings there — **do not commit local changes to `application.properties`**. In prod, OAuth comes from `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.

Sessions are stored in the database (`spring-session-jdbc`, `initialize-schema=always` creates `SPRING_SESSION*` tables on first boot) rather than in memory, so a restart or a second app instance doesn't log everyone out.

### Deployment

Production is split across two services in the same Google project (`norskgolf`):

| Serves | Where | Public URL |
|---|---|---|
| React build (`frontend/build`) | Firebase Hosting CDN | `https://golfjakten.no` (canonical), `https://norskgolf.web.app` |
| `/api/**`, `/oauth2/**`, `/login/oauth2/**`, `/logout` | Cloud Run, `europe-north1` | rewritten to by `firebase.json` |
| Database | Neon (free tier), Frankfurt | outside Google |

The split exists so the page shell loads from an edge cache instead of waiting on a cold JVM. **`/login` is deliberately not rewritten** — React owns that route, Spring owns the callback below it at `/login/oauth2/code/google`.

A merge to `master` deploys **both halves** — `.github/workflows/ci.yml` has a `deploy-backend` and a `deploy-frontend` job, each authenticating via Workload Identity Federation and each gated on an approval in the `production` environment. By hand:

```bash
cd backend  && gcloud run deploy norskgolf --source . --region=europe-north1   # backend
cd frontend && npm run build && cd .. && npx firebase-tools deploy --only hosting   # frontend
```

The Firebase CLI needs Node ≥20; the machine's default `node` may be older (`PATH="/opt/homebrew/opt/node@20/bin:$PATH"`).

Buildpacks detect Maven and build the JAR — there is no Dockerfile. Env vars (`SPRING_DATASOURCE_*`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) live on the Cloud Run service; update them with `gcloud run services update norskgolf --region=europe-north1 --update-env-vars=...`, never in the repo.

Gotchas:
- `backend/.gcloudignore` is load-bearing: `secrets.properties` is imported unconditionally, so uploading it would point production at the local H2 file and bake the OAuth secret into the image.
- Behind Firebase, Spring sees the `run.app` host, so `{baseUrl}` would build an OAuth `redirect_uri` pointing off the public domain. It is pinned to `https://golfjakten.no/login/oauth2/code/google` by the `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI` env var on the Cloud Run service; the repo keeps `{baseUrl}` for local dev. Both public hosts therefore send users to `golfjakten.no` to log in. `server.tomcat.use-relative-redirects=true` stops Tomcat rewriting relative redirects to absolute `run.app` URLs for the same reason.
- Cloud Run also answers on two `run.app` hostnames directly. Those bypass Firebase (and so keep all cookies), which makes them useful for debugging but they are not the canonical entry point.
- `min-instances=0`, so an idle app cold-starts (~4s JVM + Neon wake). Sessions are in Postgres, so nobody gets logged out by it.
- First boot against an empty DB runs `CourseSyncService`, which inserts 160 courses one at a time — a few minutes over a remote DB.

### Frontend (`frontend/src/`)

React 19 + React Router 7 + Material UI 7 + Leaflet.

- `App.js` — Router, auth check, AppBar + BottomNavigation shell
- `MapView.js` — Leaflet map of courses (public)
- `Overview.js` — Authenticated dashboard: stats, round history
- `SocialView.js` — Friends and leaderboard

## Key constraints

- All JPA `@Entity` classes **must have a no-arg constructor** (may be `protected`). Hibernate needs it.
- `backend/src/main/resources/static/` is generated by `npm run deploy` — edit `frontend/src/`, never the static output.
- All fetches use same-origin relative paths (dev proxy in dev, same-origin serving in prod). There is no API base URL and no `.env` — don't reintroduce one without a reason.
- CI is `.github/workflows/ci.yml`: backend tests, frontend lint/test/build, then the Cloud Run deploy on push to `master`.
- Jest needs three workarounds because CRA pins jest 27, which predates the `exports` field (`frontend/package.json` → `jest.moduleNameMapper` for react-router, a `TextEncoder`/`TextDecoder` polyfill in `setupTests.js`, and `jest.mock("./MapView")` in any test that renders `App`, since react-leaflet is ESM-only). Don't delete them expecting the suite to still run.
- `oauth2Login` sets `.loginPage("/login")` purely so Spring's `DefaultLoginPageGeneratingFilter` stays disabled and `/login` reaches React's `Login.js` through the SPA forward. There is no Spring-rendered login page; `Login.js` navigates to `/oauth2/authorization/google` itself.
