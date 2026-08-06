package fritids.norskgolf.service;

import fritids.norskgolf.dto.DashboardStats;
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.Round;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.PlayedCourseRepository;
import fritids.norskgolf.repository.RoundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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

    @Test
    void countsOnlyActiveCoursesTowardsProgressSoThePercentageCannotExceed100() {
        // The user has played three courses, two of which the club-list reconciler deactivated.
        // Counting all three against an active-only denominator gave "3 av 2" and 150%.
        Course active = course(1L, "Miklagard Golfklubb", "Akershus");
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(active));

        User user = new User();
        user.setId(1L);
        when(playedCourseRepository.findCourseIdsByUserId(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(roundRepository.findByUserIdOrderByDateDescIdDesc(1L)).thenReturn(List.of());

        DashboardStats stats = golfService.getDashboardStats(user);

        assertEquals(1, stats.getTotalPlayed());
        assertEquals(1, stats.getTotalCourses());
        assertEquals(100.0, stats.getPercentageComplete(), 0.001);
    }

    @Test
    void countsEveryRoundEvenThoughOnlyFiveAreListed() {
        // recentRounds is capped at 5 for display, so the frontend cannot derive a
        // lifetime total from it. roundCount carries the real number.
        Course active = course(1L, "Miklagard Golfklubb", "Akershus");
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(active));

        User user = new User();
        user.setId(1L);
        when(playedCourseRepository.findCourseIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roundRepository.findByUserIdOrderByDateDescIdDesc(1L)).thenReturn(List.of(
                round(active), round(active), round(active), round(active),
                round(active), round(active), round(active)));

        DashboardStats stats = golfService.getDashboardStats(user);

        assertEquals(7, stats.getRoundCount());
        assertEquals(5, stats.getRecentRounds().size());
    }

    private static Round round(Course course) {
        Round r = new Round();
        r.setId(1L);
        r.setCourse(course);
        r.setDate(LocalDate.of(2026, 6, 1));
        r.setScore(84);
        return r;
    }

    private static Course course(Long id, String name, String county) {
        Course c = new Course();
        c.setId(id);
        c.setName(name);
        c.setCounty(county);
        c.setActive(true);
        return c;
    }
}
