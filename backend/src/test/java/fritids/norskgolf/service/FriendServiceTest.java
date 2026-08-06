package fritids.norskgolf.service;

import fritids.norskgolf.dto.FriendDto;
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.Friendship;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.FriendshipRepository;
import fritids.norskgolf.repository.PlayedCourseRepository;
import fritids.norskgolf.repository.RoundRepository;
import fritids.norskgolf.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Friendship state machine: who may create, accept and delete a relationship.
 */
@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private PlayedCourseRepository playedCourseRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private CourseRepository courseRepository;

    @InjectMocks private FriendService friendService;

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setPublicId("public-" + id);
        return u;
    }

    private static Course course(long id, String county) {
        Course c = new Course();
        c.setId(id);
        c.setCounty(county);
        c.setActive(true);
        return c;
    }

    private HttpStatus statusOf(Executable call) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, call);
        return HttpStatus.valueOf(ex.getStatusCode().value());
    }

    // --- SEND REQUEST ---

    @Test
    void cannotSendRequestToYourself() {
        User me = user(1L);
        when(userRepository.findByPublicId("public-1")).thenReturn(Optional.of(me));

        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> friendService.sendRequest(me, "public-1")));
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void cannotSendRequestWhenRelationshipAlreadyExists() {
        User me = user(1L);
        User other = user(2L);
        when(userRepository.findByPublicId("public-2")).thenReturn(Optional.of(other));
        when(friendshipRepository.findRelationship(me, other)).thenReturn(Optional.of(new Friendship(me, other)));

        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> friendService.sendRequest(me, "public-2")));
        verify(friendshipRepository, never()).save(any());
    }

    // --- ACCEPT ---

    @Test
    void onlyReceiverCanAcceptPendingRequest() {
        User requester = user(1L);
        User receiver = user(2L);
        Friendship pending = new Friendship(requester, receiver);
        when(friendshipRepository.findById(10L)).thenReturn(Optional.of(pending));

        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(() -> friendService.respondToRequest(10L, "ACCEPT", requester)));
        verify(friendshipRepository, never()).save(any());
    }

    // --- DELETE (unfriend / cancel) ---

    @Test
    void requesterCanDeleteFriendship() {
        User requester = user(1L);
        Friendship f = new Friendship(requester, user(2L));
        when(friendshipRepository.findById(10L)).thenReturn(Optional.of(f));

        friendService.removeFriendship(10L, requester);

        verify(friendshipRepository).delete(f);
    }

    @Test
    void receiverCanDeleteFriendship() {
        User receiver = user(2L);
        Friendship f = new Friendship(user(1L), receiver);
        when(friendshipRepository.findById(10L)).thenReturn(Optional.of(f));

        friendService.removeFriendship(10L, receiver);

        verify(friendshipRepository).delete(f);
    }

    @Test
    void thirdPartyCannotDeleteFriendship() {
        Friendship f = new Friendship(user(1L), user(2L));
        when(friendshipRepository.findById(10L)).thenReturn(Optional.of(f));

        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> friendService.removeFriendship(10L, user(3L))));
        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void deletingUnknownFriendshipIsNotFound() {
        when(friendshipRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> friendService.removeFriendship(99L, user(1L))));
    }

    // --- SEARCH ---

    @Test
    void searchResultCarriesFriendshipIdForOutgoingRequest() {
        User me = user(1L);
        User other = user(2L);
        Friendship pending = new Friendship(me, other);
        ReflectionTestUtils.setField(pending, "id", 10L);
        when(userRepository.searchUsers(eq("ola"), eq(1L), any())).thenReturn(List.of(other));
        when(friendshipRepository.findRelationship(me, other)).thenReturn(Optional.of(pending));

        FriendDto result = friendService.searchUsers("ola", me).get(0);

        assertEquals("SENT", result.getStatus());
        assertEquals(10L, result.getFriendshipId());
    }

    @Test
    void searchResultHasNoFriendshipIdWithoutRelationship() {
        User me = user(1L);
        User other = user(2L);
        when(userRepository.searchUsers(eq("ola"), eq(1L), any())).thenReturn(List.of(other));
        when(friendshipRepository.findRelationship(me, other)).thenReturn(Optional.empty());

        FriendDto result = friendService.searchUsers("ola", me).get(0);

        assertEquals("NONE", result.getStatus());
        assertNull(result.getFriendshipId());
    }

    // --- LEADERBOARD ---

    @Test
    void leaderboardCountsOnlyActiveCoursesSoAFriendsTotalMatchesTheirOwnDashboard() {
        // Course 99 is not in findByActiveTrue: the club reconciler deactivated it.
        // Counting it would inflate the friend's BANER figure above what /oversikt shows.
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course(1L, "Oslo")));

        User me = user(1);
        when(friendshipRepository.findAllFriends(1L)).thenReturn(List.of());
        when(playedCourseRepository.findCourseIdsByUserId(1L)).thenReturn(List.of(1L, 99L));
        when(roundRepository.countByUserId(1L)).thenReturn(7);

        FriendDto meRow = friendService.getLeaderboard(me).get(0);

        assertEquals(1, meRow.getTotalCourses());
        assertEquals(7, meRow.getTotalRounds());
    }

    @Test
    void leaderboardReportsHowManyFylkerEachRowHasFinished() {
        // Oslo is fully played, Vestfold is not. Two fylker exist in total.
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(
                course(1L, "Oslo"), course(2L, "Oslo"),
                course(3L, "Vestfold"), course(4L, "Vestfold")));

        User me = user(1);
        when(friendshipRepository.findAllFriends(1L)).thenReturn(List.of());
        when(playedCourseRepository.findCourseIdsByUserId(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(roundRepository.countByUserId(1L)).thenReturn(3);

        FriendDto meRow = friendService.getLeaderboard(me).get(0);

        assertEquals(1, meRow.getFylkerComplete());
        assertEquals(2, meRow.getFylkerTotal());
    }

    @Test
    void leaderboardLoadsTheCourseListOnceRatherThanPerRow() {
        // One query for the whole leaderboard, not one per friend.
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course(1L, "Oslo")));

        User me = user(1);
        User friend = user(2);
        Friendship f = new Friendship(me, friend);
        ReflectionTestUtils.setField(f, "id", 10L);
        when(friendshipRepository.findAllFriends(1L)).thenReturn(List.of(f));
        when(playedCourseRepository.findCourseIdsByUserId(anyLong())).thenReturn(List.of(1L));
        when(roundRepository.countByUserId(anyLong())).thenReturn(1);

        assertEquals(2, friendService.getLeaderboard(me).size());
        verify(courseRepository, times(1)).findByActiveTrue();
    }
}
