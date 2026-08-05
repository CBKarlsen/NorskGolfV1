package fritids.norskgolf;

import fritids.norskgolf.dto.CourseDto;
import fritids.norskgolf.dto.DashboardStats;
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.PlayedCourse;
import fritids.norskgolf.entities.Round;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.PlayedCourseRepository;
import fritids.norskgolf.repository.RoundRepository;
import fritids.norskgolf.repository.UserRepository;
import fritids.norskgolf.service.GolfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With spring.jpa.open-in-view=false there is no session open while a response is rendered, so any
 * lazy relation a service leaves unfetched throws LazyInitializationException at runtime. These
 * tests deliberately run OUTSIDE a transaction — annotating them @Transactional would keep a
 * session open and hide exactly the failure they exist to catch.
 */
@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.datasource.url=jdbc:h2:mem:openinview;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class OpenInViewTest {

    @Autowired private GolfService golfService;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private PlayedCourseRepository playedCourseRepository;
    @Autowired private RoundRepository roundRepository;

    private User seed() {
        User user = new User();
        user.setUsername("open-in-view@test.local");
        user.setEmail("open-in-view@test.local");
        user.setProviderId("open-in-view-test");
        user.setFirstName("Ola");
        user.setLastName("Nordmann");
        user = userRepository.save(user);

        Course course = new Course();
        course.setName("Testbanen");
        course.setExternalId("open-in-view-course");
        course.setCounty("Vestland");
        course.setLatitude(60.39);
        course.setLongitude(5.32);
        course = courseRepository.save(course);

        playedCourseRepository.save(new PlayedCourse(user, course));

        Round round = new Round();
        round.setUser(user);
        round.setCourse(course);
        round.setDate(LocalDate.of(2024, 5, 1));
        round.setScore(85);
        roundRepository.save(round);

        return user;
    }

    @Test
    void readPathsSurviveWithoutAnOpenSession() {
        User user = seed();

        List<CourseDto> courses = golfService.getAllCourses(user);
        assertTrue(courses.stream().anyMatch(c -> "open-in-view-course".equals(c.externalId()) && c.played()),
                "the seeded course should come back marked as played");

        assertEquals(1, golfService.getRoundsForUser(user).size());
        assertTrue(golfService.getPlayedCourses(user.getId()).stream()
                .anyMatch(c -> "open-in-view-course".equals(c.externalId())));

        DashboardStats stats = golfService.getDashboardStats(user);
        assertEquals(1, stats.getTotalPlayed());
        assertTrue(stats.getRecentRounds().size() >= 1);
    }
}
