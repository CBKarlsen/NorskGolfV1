package fritids.norskgolf.service;

import fritids.norskgolf.dto.RoundRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GolfServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private PlayedCourseRepository playedCourseRepository;
    @Mock private RoundRepository roundRepository;

    @InjectMocks private GolfService golfService;

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static RoundRequest request(String date, int score) {
        RoundRequest r = new RoundRequest();
        r.setCourseId(1L);
        r.setDate(date);
        r.setScore(score);
        return r;
    }

    private void assertRejected(RoundRequest request) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> golfService.logRound(user(1L), request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(roundRepository, never()).save(any());
        verify(playedCourseRepository, never()).save(any());
    }

    @Test
    void rejectsUnparseableDate() {
        assertRejected(request("not-a-date", 85));
    }

    @Test
    void rejectsMissingDate() {
        assertRejected(request(null, 85));
    }

    @Test
    void rejectsFutureDate() {
        assertRejected(request(LocalDate.now().plusDays(1).toString(), 85));
    }

    @Test
    void rejectsImplausibleScores() {
        assertRejected(request("2024-05-01", 0));
        assertRejected(request("2024-05-01", -5));
        assertRejected(request("2024-05-01", 900));
    }

    @Test
    void acceptsPlausibleRound() {
        Course course = new Course();
        course.setId(1L);
        course.setName("Meland");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(roundRepository.save(any(Round.class))).thenAnswer(i -> i.getArgument(0));
        when(playedCourseRepository.existsByUserIdAndCourseId(1L, 1L)).thenReturn(true);

        golfService.logRound(user(1L), request("2024-05-01", 85));

        verify(roundRepository).save(any(Round.class));
    }

    @Test
    void deleteRoundRefusesRoundOwnedBySomeoneElse() {
        Round round = new Round();
        round.setUser(user(1L));
        when(roundRepository.findById(42L)).thenReturn(Optional.of(round));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> golfService.deleteRound(42L, user(2L)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(roundRepository, never()).delete(any());
        verify(playedCourseRepository, never()).delete(any());
        verify(roundRepository, never()).existsByUserIdAndCourseId(anyLong(), anyLong());
    }
}
