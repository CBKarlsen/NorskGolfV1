# Curated Club List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the OpenStreetMap-derived course list with a curated list of Norwegian golf clubs, without destroying any round a user has logged.

**Architecture:** A JSON file in the repo is the source of truth. On boot, a reconciler matches each entry to an existing `Course` row (by `clubId`, else by normalised name plus proximity), updates matched rows in place so foreign keys survive, inserts new clubs, and deactivates courses the list does not contain. Matching logic lives in its own class so it can be tested without a database.

**Tech Stack:** Spring Boot 3.4.4, Java 17, Spring Data JPA, Jackson, JUnit 5, Mockito, H2 (tests).

## Global Constraints

- One entry per **club**, not per course.
- Courses absent from the list are **deactivated, never deleted**.
- `PlayedCourse` and `Round` rows must survive the import unchanged.
- Every list entry carries: `clubId`, `name`, `lat`, `lon`, `municipality`, `county`, `holes`.
- Proximity threshold for first-run matching: **3 km**.
- `holes` records the club's **main course** (18 for a club with 18+9).
- No new dependencies. Jackson and `spring-boot-starter-test` are already present.
- Tests run with `GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test` from `backend/`.
- Biome (frontend) is unaffected; this plan touches backend only.

## File Structure

| File | Responsibility |
|---|---|
| `backend/src/main/resources/golf_clubs.json` | The curated list (data, not code) |
| `entities/Course.java` | Gains `municipality`, `holes`, `active` |
| `repository/CourseRepository.java` | Gains `findByActiveTrue()` |
| `service/clubs/ClubRecord.java` | Immutable record of one list entry |
| `service/clubs/ClubListLoader.java` | Reads and validates the JSON |
| `service/clubs/ClubMatcher.java` | Name normalisation + proximity matching; no DB, no Spring |
| `service/CourseSyncService.java` | Rewritten: reconcile instead of seed-once |
| `service/GolfService.java` | Reads active courses only |

---

### Task 1: Course gains `active`, `municipality`, `holes`

**Files:**
- Modify: `backend/src/main/java/fritids/norskgolf/entities/Course.java`
- Modify: `backend/src/main/java/fritids/norskgolf/repository/CourseRepository.java`
- Modify: `backend/src/main/java/fritids/norskgolf/service/GolfService.java:36` and `:127`
- Test: `backend/src/test/java/fritids/norskgolf/service/GolfServiceActiveCoursesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Course.isActive()`, `Course.setActive(boolean)`, `Course.getMunicipality()`, `Course.setMunicipality(String)`, `Course.getHoles()`, `Course.setHoles(Integer)`, `CourseRepository.findByActiveTrue()` returning `List<Course>`.

- [ ] **Step 1: Write the failing test**

```java
package fritids.norskgolf.service;

import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.PlayedCourseRepository;
import fritids.norskgolf.repository.RoundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GolfServiceActiveCoursesTest {

    @Mock private CourseRepository courseRepository;
    @Mock private PlayedCourseRepository playedCourseRepository;
    @Mock private RoundRepository roundRepository;
    @InjectMocks private GolfService golfService;

    @Test
    void listsOnlyActiveCourses() {
        Course active = new Course();
        active.setName("Miklagard Golfklubb");
        active.setExternalId("miklagard-gk");
        active.setActive(true);
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(active));

        User user = new User();
        user.setId(1L);
        when(playedCourseRepository.findCourseIdsByUserId(1L)).thenReturn(List.of());

        assertEquals(1, golfService.getAllCourses(user).size());
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test -Dtest=GolfServiceActiveCoursesTest`
Expected: compilation failure — `findByActiveTrue()` and `setActive(boolean)` do not exist.

- [ ] **Step 3: Add the fields to `Course`**

Add below the existing `county` field, keeping the file's existing style (annotations on fields, getters and setters at the bottom):

```java
    private String municipality;

    private Integer holes;

    @Column(nullable = false)
    private boolean active = true;
```

And with the other accessors:

```java
    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }

    public Integer getHoles() { return holes; }
    public void setHoles(Integer holes) { this.holes = holes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
```

- [ ] **Step 4: Add the repository query**

In `CourseRepository`:

```java
    List<Course> findByActiveTrue();
```

- [ ] **Step 5: Read only active courses in `GolfService`**

Replace `courseRepository.findAll()` at both call sites — in `getAllCourses` (line ~36) and `getDashboardStats` (line ~127) — with:

```java
        List<Course> allCourses = courseRepository.findByActiveTrue();
```

Leave `getPlayedCourses` alone: a user's history must still show a course that has since been deactivated.

- [ ] **Step 6: Run the test and the full suite**

Run: `cd backend && GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test`
Expected: PASS, including the 22 existing tests.

Note: `ddl-auto=update` adds the columns automatically. Existing rows get `active = true` because the field defaults to `true` in Java, but Hibernate does **not** backfill a default for rows that already exist — the column is added as null-then-false in some databases. Verify explicitly in Task 6's rollout step by checking the dry-run summary reports the expected number of active courses.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/fritids/norskgolf/entities/Course.java \
        backend/src/main/java/fritids/norskgolf/repository/CourseRepository.java \
        backend/src/main/java/fritids/norskgolf/service/GolfService.java \
        backend/src/test/java/fritids/norskgolf/service/GolfServiceActiveCoursesTest.java
git commit -m "Add active/municipality/holes to Course, read only active courses"
```

---

### Task 2: Club list format and loader

**Files:**
- Create: `backend/src/main/java/fritids/norskgolf/service/clubs/ClubRecord.java`
- Create: `backend/src/main/java/fritids/norskgolf/service/clubs/ClubListLoader.java`
- Create: `backend/src/test/resources/clubs-fixture.json`
- Test: `backend/src/test/java/fritids/norskgolf/service/clubs/ClubListLoaderTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `record ClubRecord(String clubId, String name, double lat, double lon, String municipality, String county, Integer holes)` and `ClubListLoader.load(String classpathResource)` returning `List<ClubRecord>`, throwing `IllegalStateException` on a missing file or an entry with a blank `clubId` or `name`.

- [ ] **Step 1: Write the fixture**

`backend/src/test/resources/clubs-fixture.json`:

```json
[
  {
    "clubId": "miklagard-gk",
    "name": "Miklagard Golfklubb",
    "lat": 60.0234,
    "lon": 11.1421,
    "municipality": "Ullensaker",
    "county": "Akershus",
    "holes": 18
  },
  {
    "clubId": "aalesund-gk",
    "name": "Ålesund Golfklubb",
    "lat": 62.5312,
    "lon": 6.1189,
    "municipality": "Ålesund",
    "county": "Møre og Romsdal",
    "holes": 18
  }
]
```

- [ ] **Step 2: Write the failing test**

```java
package fritids.norskgolf.service.clubs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClubListLoaderTest {

    private final ClubListLoader loader = new ClubListLoader();

    @Test
    void readsEveryFieldOfEveryEntry() {
        List<ClubRecord> clubs = loader.load("clubs-fixture.json");

        assertEquals(2, clubs.size());
        ClubRecord miklagard = clubs.get(0);
        assertEquals("miklagard-gk", miklagard.clubId());
        assertEquals("Miklagard Golfklubb", miklagard.name());
        assertEquals("Ullensaker", miklagard.municipality());
        assertEquals("Akershus", miklagard.county());
        assertEquals(18, miklagard.holes());
        assertEquals(60.0234, miklagard.lat(), 0.0001);
    }

    @Test
    void rejectsAMissingFile() {
        assertThrows(IllegalStateException.class, () -> loader.load("no-such-file.json"));
    }
}
```

- [ ] **Step 3: Run it and watch it fail**

Run: `cd backend && GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test -Dtest=ClubListLoaderTest`
Expected: compilation failure — `ClubListLoader` does not exist.

- [ ] **Step 4: Write the record and loader**

`ClubRecord.java`:

```java
package fritids.norskgolf.service.clubs;

public record ClubRecord(
        String clubId,
        String name,
        double lat,
        double lon,
        String municipality,
        String county,
        Integer holes
) {}
```

`ClubListLoader.java`:

```java
package fritids.norskgolf.service.clubs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class ClubListLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<ClubRecord> load(String classpathResource) {
        try (InputStream in = new ClassPathResource(classpathResource).getInputStream()) {
            List<ClubRecord> clubs = mapper.readValue(in, mapper.getTypeFactory()
                    .constructCollectionType(List.class, ClubRecord.class));
            for (ClubRecord club : clubs) {
                if (club.clubId() == null || club.clubId().isBlank()
                        || club.name() == null || club.name().isBlank()) {
                    throw new IllegalStateException("club entry missing clubId or name: " + club);
                }
            }
            return clubs;
        } catch (Exception e) {
            throw new IllegalStateException("could not read club list " + classpathResource, e);
        }
    }
}
```

- [ ] **Step 5: Run the test**

Run: `cd backend && GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test -Dtest=ClubListLoaderTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/fritids/norskgolf/service/clubs/ \
        backend/src/test/java/fritids/norskgolf/service/clubs/ClubListLoaderTest.java \
        backend/src/test/resources/clubs-fixture.json
git commit -m "Add club list record and loader"
```

---

### Task 3: The matcher

**Files:**
- Create: `backend/src/main/java/fritids/norskgolf/service/clubs/ClubMatcher.java`
- Test: `backend/src/test/java/fritids/norskgolf/service/clubs/ClubMatcherTest.java`

**Interfaces:**
- Consumes: `ClubRecord` from Task 2.
- Produces:
  - `static String ClubMatcher.normalise(String name)` — lowercased, diacritics folded (`ø→o`, `æ→ae`, `å→a`), the words `golfklubb`, `gk`, `golfpark`, `golfbane`, `golf`, `klubb` removed, remaining whitespace collapsed.
  - `static double ClubMatcher.distanceKm(double lat1, double lon1, double lat2, double lon2)`
  - `ClubMatcher.Match match(ClubRecord club, List<Course> candidates)` returning `record Match(Course course, boolean ambiguous)`; `course` is null when nothing matched.

- [ ] **Step 1: Write the failing tests**

```java
package fritids.norskgolf.service.clubs;

import fritids.norskgolf.entities.Course;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClubMatcherTest {

    private final ClubMatcher matcher = new ClubMatcher();

    private static Course course(String name, double lat, double lon) {
        Course c = new Course();
        c.setName(name);
        c.setLatitude(lat);
        c.setLongitude(lon);
        return c;
    }

    private static ClubRecord club(String name, double lat, double lon) {
        return new ClubRecord("slug", name, lat, lon, "Kommune", "Fylke", 18);
    }

    @Test
    void normalisationIgnoresClubWordsAndNorwegianLetters() {
        assertEquals(ClubMatcher.normalise("Ålesund Golfklubb"), ClubMatcher.normalise("Aalesund GK"));
        assertEquals(ClubMatcher.normalise("Bjørnafjorden Golfklubb"), ClubMatcher.normalise("Bjornafjorden golfbane"));
    }

    @Test
    void matchesTheSameClubWithADifferentSuffix() {
        Course existing = course("Miklagard GK", 60.0234, 11.1421);

        ClubMatcher.Match match = matcher.match(club("Miklagard Golfklubb", 60.0240, 11.1430), List.of(existing));

        assertSame(existing, match.course());
        assertFalse(match.ambiguous());
    }

    @Test
    void doesNotMatchASimilarNameFarAway() {
        Course existing = course("Moss Golfklubb", 59.4340, 10.6580);

        // same name, but 200 km away — a different club
        ClubMatcher.Match match = matcher.match(club("Moss Golfklubb", 61.0000, 10.6580), List.of(existing));

        assertNull(match.course());
    }

    @Test
    void reportsAmbiguityInsteadOfGuessing() {
        Course first = course("Moss Golfklubb", 59.4340, 10.6580);
        Course second = course("Moss & Rygge Golfklubb", 59.4350, 10.6590);

        ClubMatcher.Match match = matcher.match(club("Moss Golfklubb", 59.4345, 10.6585), List.of(first, second));

        assertTrue(match.ambiguous());
    }

    @Test
    void distanceIsRoughlyRight() {
        // Oslo to Bergen is about 300 km
        double km = ClubMatcher.distanceKm(59.9139, 10.7522, 60.3913, 5.3221);
        assertTrue(km > 280 && km < 330, "was " + km);
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `cd backend && GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test -Dtest=ClubMatcherTest`
Expected: compilation failure — `ClubMatcher` does not exist.

- [ ] **Step 3: Write the matcher**

```java
package fritids.norskgolf.service.clubs;

import fritids.norskgolf.entities.Course;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class ClubMatcher {

    /** Two clubs closer than this are near enough to be the same place with a differently-placed pin. */
    private static final double MAX_KM = 3.0;

    public record Match(Course course, boolean ambiguous) {}

    public static String normalise(String name) {
        if (name == null) return "";
        String folded = name.toLowerCase(Locale.ROOT)
                .replace("ø", "o").replace("æ", "ae").replace("å", "a")
                .replace("aa", "a");
        folded = Normalizer.normalize(folded, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return folded.replaceAll("\\b(golfklubb|golfpark|golfbane|golf|klubb|gk)\\b", " ")
                .replaceAll("[^a-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public Match match(ClubRecord club, List<Course> candidates) {
        String target = normalise(club.name());

        List<Course> near = candidates.stream()
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .filter(c -> distanceKm(club.lat(), club.lon(), c.getLatitude(), c.getLongitude()) <= MAX_KM)
                .toList();

        List<Course> sameName = near.stream()
                .filter(c -> normalise(c.getName()).equals(target))
                .toList();

        if (sameName.size() == 1) return new Match(sameName.get(0), false);
        if (sameName.size() > 1) return new Match(null, true);

        // no exact name match nearby: one nearby candidate is a probable match, several is ambiguous
        if (near.size() == 1) return new Match(near.get(0), false);
        if (near.size() > 1) return new Match(null, true);

        return new Match(null, false);
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `cd backend && GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test -Dtest=ClubMatcherTest`
Expected: PASS, all five.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/fritids/norskgolf/service/clubs/ClubMatcher.java \
        backend/src/test/java/fritids/norskgolf/service/clubs/ClubMatcherTest.java
git commit -m "Add club matcher: name normalisation, proximity, ambiguity reporting"
```

---

### Task 4: The reconciler

**Files:**
- Modify: `backend/src/main/java/fritids/norskgolf/service/CourseSyncService.java` (full rewrite, 139 lines → roughly 80)
- Test: `backend/src/test/java/fritids/norskgolf/service/CourseSyncServiceTest.java`

**Interfaces:**
- Consumes: `ClubListLoader.load`, `ClubMatcher.match`, `CourseRepository`.
- Produces: `CourseSyncService.reconcile(List<ClubRecord> clubs, boolean dryRun)` returning `record SyncSummary(int matched, int inserted, int deactivated, List<String> ambiguous)`.

- [ ] **Step 1: Write the failing integration test**

```java
package fritids.norskgolf.service;

import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.PlayedCourse;
import fritids.norskgolf.entities.Round;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.PlayedCourseRepository;
import fritids.norskgolf.repository.RoundRepository;
import fritids.norskgolf.repository.UserRepository;
import fritids.norskgolf.service.clubs.ClubRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:clubsync;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class CourseSyncServiceTest {

    @Autowired private CourseSyncService courseSyncService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlayedCourseRepository playedCourseRepository;
    @Autowired private RoundRepository roundRepository;

    @Test
    void updatesMatchesInsertsNewAndDeactivatesTheRestWithoutLosingHistory() {
        Course existing = new Course();
        existing.setName("Miklagard GK");
        existing.setExternalId("osm-123");
        existing.setLatitude(60.0234);
        existing.setLongitude(11.1421);
        existing.setActive(true);
        existing = courseRepository.save(existing);

        Course orphan = new Course();
        orphan.setName("Nedlagt Driving Range");
        orphan.setExternalId("osm-999");
        orphan.setLatitude(59.0);
        orphan.setLongitude(10.0);
        orphan.setActive(true);
        orphan = courseRepository.save(orphan);

        User user = new User();
        user.setUsername("sync@test.local");
        user.setEmail("sync@test.local");
        user.setProviderId("sync-test");
        user = userRepository.save(user);

        playedCourseRepository.save(new PlayedCourse(user, orphan));
        Round round = new Round();
        round.setUser(user);
        round.setCourse(orphan);
        round.setDate(LocalDate.of(2024, 5, 1));
        round.setScore(85);
        roundRepository.save(round);

        List<ClubRecord> clubs = List.of(
                new ClubRecord("miklagard-gk", "Miklagard Golfklubb", 60.0240, 11.1430, "Ullensaker", "Akershus", 18),
                new ClubRecord("aalesund-gk", "Ålesund Golfklubb", 62.5312, 6.1189, "Ålesund", "Møre og Romsdal", 18));

        CourseSyncService.SyncSummary summary = courseSyncService.reconcile(clubs, false);

        assertEquals(1, summary.matched());
        assertEquals(1, summary.inserted());
        assertEquals(1, summary.deactivated());

        Course updated = courseRepository.findById(existing.getId()).orElseThrow();
        assertEquals("Miklagard Golfklubb", updated.getName());
        assertEquals("miklagard-gk", updated.getExternalId());
        assertEquals("Akershus", updated.getCounty());
        assertEquals("Ullensaker", updated.getMunicipality());
        assertEquals(18, updated.getHoles());
        assertTrue(updated.isActive());

        Course deactivated = courseRepository.findById(orphan.getId()).orElseThrow();
        assertFalse(deactivated.isActive(), "unmatched courses are deactivated, not deleted");

        assertEquals(1, roundRepository.findByUserIdOrderByDateDescIdDesc(user.getId()).size(),
                "the logged round must survive the import");
        assertTrue(playedCourseRepository.existsByUserIdAndCourseId(user.getId(), orphan.getId()));
    }

    @Test
    void dryRunReportsWithoutWriting() {
        long before = courseRepository.count();

        CourseSyncService.SyncSummary summary = courseSyncService.reconcile(
                List.of(new ClubRecord("nytt-gk", "Nytt Golfklubb", 58.0, 7.0, "Kommune", "Agder", 9)), true);

        assertEquals(1, summary.inserted());
        assertEquals(before, courseRepository.count(), "dry run must not write");
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd backend && GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test -Dtest=CourseSyncServiceTest`
Expected: compilation failure — `reconcile` and `SyncSummary` do not exist.

- [ ] **Step 3: Rewrite `CourseSyncService`**

Replace the whole file. The Overpass API fallback and `estimateCounty` go with it — the curated list is the source of truth, and county now comes from the data.

```java
package fritids.norskgolf.service;

import fritids.norskgolf.entities.Course;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.service.clubs.ClubListLoader;
import fritids.norskgolf.service.clubs.ClubMatcher;
import fritids.norskgolf.service.clubs.ClubRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CourseSyncService {

    private static final Logger log = LoggerFactory.getLogger(CourseSyncService.class);
    private static final String CLUB_LIST = "golf_clubs.json";

    private final CourseRepository courseRepository;
    private final ClubListLoader loader;
    private final ClubMatcher matcher;

    @Value("${app.clubs.dry-run:false}")
    private boolean dryRun;

    public CourseSyncService(CourseRepository courseRepository, ClubListLoader loader, ClubMatcher matcher) {
        this.courseRepository = courseRepository;
        this.loader = loader;
        this.matcher = matcher;
    }

    public record SyncSummary(int matched, int inserted, int deactivated, List<String> ambiguous) {}

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        SyncSummary summary = reconcile(loader.load(CLUB_LIST), dryRun);
        log.info("Club sync{}: {} matched, {} inserted, {} deactivated, {} ambiguous",
                dryRun ? " (DRY RUN)" : "", summary.matched(), summary.inserted(),
                summary.deactivated(), summary.ambiguous().size());
        summary.ambiguous().forEach(name -> log.warn("Ambiguous club, needs a human: {}", name));
    }

    @Transactional
    public SyncSummary reconcile(List<ClubRecord> clubs, boolean dryRunMode) {
        List<Course> existing = new ArrayList<>(courseRepository.findAll());
        List<Course> unclaimed = new ArrayList<>(existing);
        List<String> ambiguous = new ArrayList<>();
        int matched = 0;
        int inserted = 0;

        for (ClubRecord club : clubs) {
            Course byId = existing.stream()
                    .filter(c -> club.clubId().equals(c.getExternalId()))
                    .findFirst()
                    .orElse(null);

            ClubMatcher.Match match = byId != null
                    ? new ClubMatcher.Match(byId, false)
                    : matcher.match(club, unclaimed);

            if (match.ambiguous()) {
                ambiguous.add(club.name());
                continue;
            }

            Course course = match.course();
            if (course == null) {
                course = new Course();
                inserted++;
            } else {
                unclaimed.remove(course);
                matched++;
            }

            apply(club, course);
            if (!dryRunMode) courseRepository.save(course);
        }

        int deactivated = 0;
        for (Course leftover : unclaimed) {
            if (!leftover.isActive()) continue;
            deactivated++;
            leftover.setActive(false);
            if (!dryRunMode) courseRepository.save(leftover);
        }

        return new SyncSummary(matched, inserted, deactivated, ambiguous);
    }

    private void apply(ClubRecord club, Course course) {
        course.setExternalId(club.clubId());
        course.setName(club.name());
        course.setLatitude(club.lat());
        course.setLongitude(club.lon());
        course.setMunicipality(club.municipality());
        course.setCounty(club.county());
        course.setHoles(club.holes());
        course.setActive(true);
    }
}
```

- [ ] **Step 4: Add a minimal `golf_clubs.json` so the context can start**

Task 6 replaces this with the real list. For now, so `syncOnStartup` has something to read, create `backend/src/main/resources/golf_clubs.json` containing the two-entry fixture from Task 2 (same content as `clubs-fixture.json`).

- [ ] **Step 5: Run the test and the full suite**

Run: `cd backend && GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test`
Expected: PASS. `OpenInViewTest` and `GolfServiceTest` must still pass — if `OpenInViewTest` fails on course counts, it is because startup sync now writes the placeholder clubs; adjust that test to assert on its own seeded course by `externalId` rather than on totals.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/fritids/norskgolf/service/CourseSyncService.java \
        backend/src/main/resources/golf_clubs.json \
        backend/src/test/java/fritids/norskgolf/service/CourseSyncServiceTest.java
git commit -m "Reconcile courses against the curated club list instead of seeding once"
```

---

### Task 5: Retire the OSM seed data

**Files:**
- Delete: `backend/src/main/resources/golf_courses.json`
- Modify: `CLAUDE.md` (the "Course data" section)

- [ ] **Step 1: Confirm nothing reads the old file**

Run: `cd backend && grep -rn "golf_courses.json\|overpass\|estimateCounty" src/main/java src/main/resources --include="*.java" --include="*.properties"`
Expected: no matches. If any remain, remove them before continuing.

- [ ] **Step 2: Delete the file**

```bash
git rm backend/src/main/resources/golf_courses.json
```

- [ ] **Step 3: Update CLAUDE.md**

Replace the "Course data" section with:

```markdown
### Course data

`backend/src/main/resources/golf_clubs.json` is the curated list of Norwegian golf clubs and the source of truth for the collection game — one entry per club, carrying `clubId`, name, coordinates, municipality, county and hole count.

`CourseSyncService` reconciles it into the `course` table on every boot: matches by `clubId`, else by normalised name within 3 km; updates matched rows in place so `played_course` and `round` foreign keys survive; inserts new clubs; and **deactivates** courses the list no longer contains rather than deleting them, so a logged round is never lost. Ambiguous matches are logged and skipped for a human to resolve.

Set `app.clubs.dry-run=true` to log what would change without writing.
```

- [ ] **Step 4: Run the full suite and commit**

```bash
cd backend && GOOGLE_CLIENT_ID=ci-dummy GOOGLE_CLIENT_SECRET=ci-dummy ./mvnw -B test
git add -A backend CLAUDE.md
git commit -m "Retire the OSM course seed in favour of the curated club list"
```

---

### Task 6: Assemble the real club list

This is data work, not code. It is the bulk of the effort and needs human verification — the steps below make that verification concrete rather than leaving it to judgement.

**Files:**
- Modify: `backend/src/main/resources/golf_clubs.json` (replace the placeholder with ~180 real entries)

- [ ] **Step 1: Capture what the current database holds**

```bash
curl -s https://golfjakten.no/api/courses > /tmp/current-courses.json
python3 -c "import json; cs=json.load(open('/tmp/current-courses.json')); print(len(cs)); [print(c['name']) for c in sorted(cs, key=lambda c: c['name'])]" > /tmp/current-names.txt
```

- [ ] **Step 2: Collect the club list from provgolf**

Open `https://www.provgolf.no/` with the browser devtools network tab open and find the request that populates the club map. Save its JSON response to `/tmp/provgolf.json`. If no such request exists, fall back to transcribing the club list from the map UI — roughly 180 entries.

- [ ] **Step 3: Build the file**

For each club, produce one entry with `clubId` (slugified name: lowercase, Norwegian letters folded, spaces to hyphens, e.g. `Ålesund Golfklubb` → `aalesund-gk`), `name`, `lat`, `lon`, `municipality`, `county`, `holes`. Where provgolf lacks coordinates, take them from the matching entry in `/tmp/current-courses.json`, and where neither has them, look the club up individually — an entry with no coordinates cannot appear on the map.

- [ ] **Step 4: Check the seven known gaps are closed**

```bash
python3 -c "
import json
names = [c['name'].lower() for c in json.load(open('backend/src/main/resources/golf_clubs.json'))]
for c in ['larvik','moss','hamar','gjøvik','ålesund','molde','sarpsborg']:
    print(('OK  ' if any(c in n for n in names) else 'MISSING '), c)"
```
Expected: all seven present.

- [ ] **Step 5: Check for duplicate slugs**

```bash
python3 -c "
import json, collections
ids=[c['clubId'] for c in json.load(open('backend/src/main/resources/golf_clubs.json'))]
dupes=[k for k,v in collections.Counter(ids).items() if v>1]
print('duplicates:', dupes or 'none'); print('total clubs:', len(ids))"
```
Expected: no duplicates, and a total in the 170–190 range. A number far outside that means clubs were double-counted or missed.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/golf_clubs.json
git commit -m "Add the curated list of Norwegian golf clubs"
```

---

### Task 7: Dry run, then rollout

- [ ] **Step 1: Back up production data**

```bash
pg_dump "postgresql://<user>:<password>@ep-calm-water-asm3jp2o-pooler.c-4.eu-central-1.aws.neon.tech/neondb?sslmode=require" > ~/norskgolf-backup-$(date +%Y%m%d).sql
```
Credentials are on the Cloud Run service (`gcloud run services describe norskgolf --region=europe-north1`). There is no other backup; do not skip this.

- [ ] **Step 2: Dry-run against production data locally**

Restore the dump into a local Postgres or H2 instance, point the app at it, and start with the dry-run flag:

```bash
cd backend && GOOGLE_CLIENT_ID=d GOOGLE_CLIENT_SECRET=d PORT=8081 \
  ./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.clubs.dry-run=true
```

Read the logged summary. Expected shape: roughly 150 matched, roughly 25 inserted, a handful deactivated, and few or no ambiguous entries. **Read every ambiguous line and every deactivation by hand** — a deactivated club that a user has played is the failure mode this whole design exists to prevent.

- [ ] **Step 3: Fix what the dry run reveals**

Ambiguities are resolved by editing `golf_clubs.json` — usually by correcting a name or coordinate so the match is unmistakable. Re-run the dry run until the summary is clean.

- [ ] **Step 4: Merge and deploy**

Open a PR, let CI run, merge, approve the deployment.

- [ ] **Step 5: Verify production**

```bash
curl -s https://golfjakten.no/api/courses | python3 -c "import sys,json; cs=json.load(sys.stdin); print('active courses:', len(cs))"
gcloud run services logs read norskgolf --region=europe-north1 --limit=50 | grep "Club sync"
```
Expected: the active count matches the club list size, and the logged summary matches the reviewed dry run.

- [ ] **Step 6: Verify in the browser**

Log in and check: the map shows the new clubs, previously played courses are still marked, the overview total reads out of the new denominator, and per-county progress uses real counties. Confirm a previously logged round still appears.

---

## Self-review notes

- **Spec coverage:** data format (Task 2), schema fields (Task 1), match/update/insert/deactivate (Task 4), dry run (Tasks 4 and 7), active filtering (Task 1), county from data (Task 4's `apply`), matcher tests (Task 3), integration test preserving history (Task 4), rollout with backup (Task 7), removal of `estimateCounty` and the OSM seed (Task 5).
- **Known risk carried deliberately:** Task 1's note about `ddl-auto=update` and existing rows' `active` value is verified in Task 7's dry run rather than assumed.
- **`GolfService.getPlayedCourses` intentionally keeps `findByUserIdWithCourse`**, so history shows deactivated courses.
