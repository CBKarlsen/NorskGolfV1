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

CSRF uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`: Spring writes a readable `XSRF-TOKEN` cookie (a `CsrfCookieFilter` forces it onto every response). **Every mutating fetch must read that cookie and echo it as the `X-XSRF-TOKEN` header, plus `credentials: "include"`** — see the `getCookie` helpers in `Overview.js` / `SocialView.js`.

### SPA routes vs security

React routes are Norwegian (`/kart`, `/oversikt`, `/venner`). `SecurityConfig` permits SPA shell GETs as a class — `RegexRequestMatcher.regexMatcher(GET, "/[^/.]*(\\?.*)?")` — mirroring the extensionless single-segment paths `SpaRedirectController` forwards to `index.html`. A new React route therefore needs **no** security change. Every `/api/**` path contains a slash, so it can never match that rule; public API endpoints are listed explicitly (`GET /api/auth/me`, `GET /api/courses`).

Protected `/api` endpoints answer an unauthenticated request with a 302 to Google OAuth, not a 401. Frontend fetches must check `res.ok` rather than assume JSON.

### Course data

`CourseSyncService` runs on `ApplicationReadyEvent` and seeds courses **only when the `course` table is empty**: `resources/golf_courses.json` first, falling back to a live Overpass API query for Norway. To re-import, clear the table first.

### Database

| Environment | DB | Config |
|---|---|---|
| Local dev | H2 (file-based) | `jdbc:h2:file:./data/golfjakten`, set in `secrets.properties` |
| Production | PostgreSQL | `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` injected by the host — **nothing in this repo configures it** |

`ddl-auto=update`. Local datasource + OAuth credentials + `app.frontend.url` all live in the gitignored `backend/src/main/resources/secrets.properties`, imported unconditionally by `application.properties`. Put local-only settings there — **do not commit local changes to `application.properties`**. In prod, OAuth comes from `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.

Sessions are stored in the database (`spring-session-jdbc`, `initialize-schema=always` creates `SPRING_SESSION*` tables on first boot) rather than in memory, so a restart or a second app instance doesn't log everyone out.

### Deployment

Production is **Cloud Run** (`gcloud` project `norskgolf`, service `norskgolf`, region `europe-north1`) with a free-tier **Neon** Postgres in Frankfurt. A merge to `master` deploys automatically (`.github/workflows/ci.yml` → `deploy` job, authenticating via Workload Identity Federation). To redeploy by hand:

```bash
cd backend && gcloud run deploy norskgolf --source . --region=europe-north1
```

Buildpacks detect Maven and build the JAR — there is no Dockerfile. Env vars (`SPRING_DATASOURCE_*`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) live on the Cloud Run service; update them with `gcloud run services update norskgolf --region=europe-north1 --update-env-vars=...`, never in the repo.

Gotchas:
- `backend/.gcloudignore` is load-bearing: `secrets.properties` is imported unconditionally, so uploading it would point production at the local H2 file and bake the OAuth secret into the image.
- Cloud Run assigns **two** hostnames (`norskgolf-<project-number>.europe-north1.run.app` and a legacy `-lz.a.run.app`). Spring builds `redirect_uri` from whichever host the browser used, so both must be registered on the OAuth client.
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
- `GET /login` is served by Spring's auto-generated OAuth login page, not React's `Login.js` — `oauth2Login` has no `.loginPage()`, so its default filter intercepts `/login` before the SPA forward. Reaching `/login` by in-app navigation still renders the React page.
