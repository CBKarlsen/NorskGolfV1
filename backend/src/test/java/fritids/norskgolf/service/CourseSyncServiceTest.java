package fritids.norskgolf.service;

import fritids.norskgolf.dto.CourseDto;
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
    @Autowired private GolfService golfService;

    @Test
    void updatesMatchesInsertsNewAndDeactivatesTheRestWithoutLosingHistory() {
        // syncOnStartup already reconciled the placeholder golf_clubs.json into this database
        // before this test method ran, and the other test method in this class may have left
        // rounds/played-courses behind too (same H2 mem instance for the whole class); clear
        // everything so this test controls its own data. Children first for the FK constraints.
        roundRepository.deleteAll();
        playedCourseRepository.deleteAll();
        courseRepository.deleteAll();

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
        roundRepository.deleteAll();
        playedCourseRepository.deleteAll();
        courseRepository.deleteAll();

        long before = courseRepository.count();

        CourseSyncService.SyncSummary summary = courseSyncService.reconcile(
                List.of(new ClubRecord("nytt-gk", "Nytt Golfklubb", 58.0, 7.0, "Kommune", "Agder", 9)), true);

        assertEquals(1, summary.inserted());
        assertEquals(before, courseRepository.count(), "dry run must not write");
    }

    @Test
    void dryRunDoesNotMutateExistingRowsOrFlipActiveState() {
        // Regression test for the transactional-flush bug: reconcile() is @Transactional, and a
        // managed entity mutated by a setter gets auto-flushed at commit even if save() is never
        // called explicitly and dryRunMode is true. A dry run must leave every existing row byte
        // for byte as it found it.
        roundRepository.deleteAll();
        playedCourseRepository.deleteAll();
        courseRepository.deleteAll();

        Course existing = new Course();
        existing.setName("Miklagard GK");
        existing.setExternalId("osm-123");
        existing.setLatitude(60.0234);
        existing.setLongitude(11.1421);
        existing.setCounty("Old County");
        existing.setActive(true);
        existing = courseRepository.save(existing);

        Course unmatched = new Course();
        unmatched.setName("Nedlagt Driving Range");
        unmatched.setExternalId("osm-999");
        unmatched.setLatitude(59.0);
        unmatched.setLongitude(10.0);
        unmatched.setActive(true);
        unmatched = courseRepository.save(unmatched);

        List<ClubRecord> clubs = List.of(
                new ClubRecord("miklagard-gk", "Miklagard Golfklubb", 60.0240, 11.1430, "Ullensaker", "Akershus", 18));

        CourseSyncService.SyncSummary summary = courseSyncService.reconcile(clubs, true);

        assertEquals(1, summary.matched());
        assertEquals(0, summary.inserted());
        assertEquals(1, summary.deactivated(), "the count still previews what a real run would do");

        Course reReadExisting = courseRepository.findById(existing.getId()).orElseThrow();
        assertEquals("Miklagard GK", reReadExisting.getName(), "dry run must not rewrite the matched course");
        assertEquals("Old County", reReadExisting.getCounty());
        assertEquals("osm-123", reReadExisting.getExternalId());
        assertTrue(reReadExisting.isActive());

        Course reReadUnmatched = courseRepository.findById(unmatched.getId()).orElseThrow();
        assertTrue(reReadUnmatched.isActive(), "dry run must not deactivate the unmatched course");
    }

    @Test
    void matchesByExternalIdEvenWhenCoordinatesHaveDrifted() {
        // The exact-clubId branch bypasses ClubMatcher entirely, which is what every second
        // startup hits once externalIds are populated. Place the existing row far from the
        // club's real coordinates so a distance-based match could never find it — the only way
        // this test passes is via the byId lookup.
        roundRepository.deleteAll();
        playedCourseRepository.deleteAll();
        courseRepository.deleteAll();

        Course existing = new Course();
        existing.setName("Old Name For Miklagard");
        existing.setExternalId("miklagard-gk");
        existing.setLatitude(1.0);
        existing.setLongitude(1.0);
        existing.setActive(true);
        existing = courseRepository.save(existing);

        List<ClubRecord> clubs = List.of(
                new ClubRecord("miklagard-gk", "Miklagard Golfklubb", 60.0240, 11.1430, "Ullensaker", "Akershus", 18));

        CourseSyncService.SyncSummary summary = courseSyncService.reconcile(clubs, false);

        assertEquals(1, summary.matched());
        assertEquals(0, summary.inserted());
        assertEquals(0, summary.deactivated());
        assertTrue(summary.ambiguous().isEmpty());

        Course updated = courseRepository.findById(existing.getId()).orElseThrow();
        assertEquals("Miklagard Golfklubb", updated.getName());
        assertEquals(60.0240, updated.getLatitude());
    }

    @Test
    void ambiguousClubLeavesCandidatesUntouchedButIsReported() {
        roundRepository.deleteAll();
        playedCourseRepository.deleteAll();
        courseRepository.deleteAll();

        Course first = new Course();
        first.setName("Moss Golfklubb");
        first.setExternalId("osm-1");
        first.setLatitude(59.4340);
        first.setLongitude(10.6580);
        first.setActive(true);
        first = courseRepository.save(first);

        Course second = new Course();
        second.setName("Moss GK");
        second.setExternalId("osm-2");
        second.setLatitude(59.4350);
        second.setLongitude(10.6590);
        second.setActive(true);
        second = courseRepository.save(second);

        List<ClubRecord> clubs = List.of(
                new ClubRecord("moss-gk", "Moss Golfklubb", 59.4345, 10.6585, "Moss", "Østfold", 18));

        CourseSyncService.SyncSummary summary = courseSyncService.reconcile(clubs, false);

        assertEquals(0, summary.matched());
        assertEquals(0, summary.inserted());
        assertEquals(0, summary.deactivated(), "an ambiguous but present club must not deactivate its candidates");
        assertEquals(List.of("Moss Golfklubb"), summary.ambiguous());

        assertTrue(courseRepository.findById(first.getId()).orElseThrow().isActive());
        assertTrue(courseRepository.findById(second.getId()).orElseThrow().isActive());
    }

    @Test
    void refusesAListThatWouldDeactivateMostOfTheCourses() {
        // A truncated or wrong club list looks exactly like "almost every club closed". Rather
        // than gut the game, the reconciler must refuse the whole run and leave the table alone.
        roundRepository.deleteAll();
        playedCourseRepository.deleteAll();
        courseRepository.deleteAll();

        for (int i = 0; i < 12; i++) {
            Course c = new Course();
            c.setName("Klubb " + i);
            c.setExternalId("osm-" + i);
            c.setLatitude(58.0 + i * 0.5);   // >50 km apart, so nothing matches by proximity
            c.setLongitude(7.0);
            c.setActive(true);
            courseRepository.save(c);
        }

        List<ClubRecord> truncated = List.of(
                new ClubRecord("klubb-0", "Klubb 0", 58.0, 7.0, "Kommune", "Agder", 18));

        // 11 of 12 active courses = 92%, far past the 30% ceiling
        assertThrows(IllegalStateException.class, () -> courseSyncService.reconcile(truncated, false));

        assertEquals(12, courseRepository.findByActiveTrue().size(), "a refused run must write nothing");
        assertEquals("Klubb 0", courseRepository.findByExternalId("osm-0").orElseThrow().getName(),
                "not even the matched row may be rewritten");
    }

    @Test
    void aDeactivatedCourseStaysInTheUsersPlayedHistory() {
        // The core promise of deactivate-never-delete: the course leaves the map and the
        // denominator, but the round you logged there is still yours.
        roundRepository.deleteAll();
        playedCourseRepository.deleteAll();
        courseRepository.deleteAll();

        Course gone = new Course();
        gone.setName("Nedlagt Golfklubb");
        gone.setExternalId("osm-gone");
        gone.setLatitude(59.0);
        gone.setLongitude(10.0);
        gone.setActive(false);
        gone = courseRepository.save(gone);

        User user = new User();
        user.setUsername("history@test.local");
        user.setEmail("history@test.local");
        user.setProviderId("history-test");
        user = userRepository.save(user);
        playedCourseRepository.save(new PlayedCourse(user, gone));

        assertTrue(courseRepository.findByActiveTrue().isEmpty());
        assertEquals(List.of("Nedlagt Golfklubb"),
                golfService.getPlayedCourses(user.getId()).stream().map(CourseDto::name).toList());
    }

    @Test
    void findByActiveTrueExcludesInactiveRowsInTheDatabase() {
        roundRepository.deleteAll();
        playedCourseRepository.deleteAll();
        courseRepository.deleteAll();

        Course active = new Course();
        active.setName("Aktiv Golfklubb");
        active.setExternalId("aktiv");
        active.setActive(true);
        courseRepository.save(active);

        Course inactive = new Course();
        inactive.setName("Inaktiv Golfklubb");
        inactive.setExternalId("inaktiv");
        inactive.setActive(false);
        courseRepository.save(inactive);

        assertEquals(List.of("Aktiv Golfklubb"),
                courseRepository.findByActiveTrue().stream().map(Course::getName).toList());
    }
}
