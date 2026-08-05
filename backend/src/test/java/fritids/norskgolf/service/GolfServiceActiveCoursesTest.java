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
