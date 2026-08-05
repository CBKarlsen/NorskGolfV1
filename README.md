# GolfJakten ⛳

Track which Norwegian golf courses you have played, log your rounds, and compare
your progress with friends.

Live at [golfjakten.no](https://golfjakten.no).

## What it does

- **Map** — every golf club in Norway, from a curated list kept in the repo, with
  the ones you have played marked. Public: no login needed to browse.
- **Rounds** — log a score and date on any course. Playing a course marks it as
  played; deleting your last round there un-marks it.
- **Overview** — how much of the country you have covered, broken down by
  county, plus your recent rounds. Guests see the same view with empty stats.
- **Friends** — search by name or email, send and accept requests, and a
  leaderboard ranking you and your friends by courses played.

Sign-in is Google OAuth. The UI is in Norwegian.

## Stack

| | |
|---|---|
| Backend | Spring Boot 3.4.4, Java 17, Spring Data JPA, Spring Security |
| Frontend | React 19, React Router 7, Material UI 7, Leaflet |
| Database | PostgreSQL in production, file-based H2 locally |
| Hosting | Cloud Run, deployed by GitHub Actions on merge to `master` |

The React app is built into `backend/src/main/resources/static/` and served by
Spring Boot, so production is a single JAR with no separate frontend host.

## Running it locally

You need JDK 17 and Node 20.

The backend reads local configuration from
`backend/src/main/resources/secrets.properties`, which is gitignored and not in
this repo. Create it with your own Google OAuth credentials:

```properties
spring.security.oauth2.client.registration.google.client-id=<your client id>
spring.security.oauth2.client.registration.google.client-secret=<your client secret>

spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.url=jdbc:h2:file:./data/golfjakten
spring.datasource.username=sa
spring.datasource.password=

app.frontend.url=http://localhost:3000
server.servlet.session.cookie.secure=false
```

Add `http://localhost:8080/login/oauth2/code/google` to the authorised redirect
URIs of that OAuth client, then run both halves:

```bash
cd backend && ./mvnw spring-boot:run    # :8080
cd frontend && npm install && npm start # :3000, proxies /api to :8080
```

The course list is curated in `backend/src/main/resources/golf_clubs.json` — one
entry per club, with coordinates, municipality, county and hole count. On every
start the backend reconciles the course table against it: entries are matched to
existing rows by club id, or by name and proximity, updated in place, inserted
if new, and deactivated (never deleted) if no entry matches, so rounds logged at
a course that leaves the list stay in your history. Set `app.clubs.dry-run=true`
to log the diff without writing.

## Tests

```bash
cd backend  && ./mvnw test
cd frontend && npm test
```

Both run in CI on every pull request, along with `npm run lint:ci` (Biome).
Use `npm run lint` locally — same checks, but it fixes what it can.

## Layout

```
backend/src/main/java/fritids/norskgolf/
  controller/   REST endpoints under /api
  service/      business logic
  repository/   Spring Data JPA
  entities/     User, Course, Round, PlayedCourse, Friendship
  SecurityConfig.java
frontend/src/
  App.js        router + app shell
  MapView.js    Leaflet course map
  Overview.js   stats and round history
  SocialView.js friends and leaderboard
```

`CLAUDE.md` has the details a contributor needs — CSRF handling, the SPA routing
rules, deployment gotchas.
