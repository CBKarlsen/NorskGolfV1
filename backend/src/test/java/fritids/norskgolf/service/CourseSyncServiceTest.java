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
}
