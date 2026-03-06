package fritids.norskgolf.service;

import fritids.norskgolf.dto.CourseDto;
import fritids.norskgolf.dto.DashboardStats;
import fritids.norskgolf.dto.RoundDto;
import fritids.norskgolf.dto.RoundRequest;
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.PlayedCourse;
import fritids.norskgolf.entities.Round;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.PlayedCourseRepository;
import fritids.norskgolf.repository.RoundRepository;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GolfService {

    @Autowired private CourseRepository courseRepository;
    @Autowired private PlayedCourseRepository playedCourseRepository;
    @Autowired private RoundRepository roundRepository;
    @Autowired private UserRepository userRepository;

    // --- 1. GET ALL COURSES ---
    public List<CourseDto> getAllCourses(User currentUser) {
        List<Course> allCourses = courseRepository.findAll();
        Set<Long> playedIds = new HashSet<>();

        if (currentUser != null) {
            playedCourseRepository.findByUserId(currentUser.getId())
                    .forEach(pc -> playedIds.add(pc.getCourse().getId()));
        }

        return allCourses.stream()
                .map(c -> new CourseDto(c.getId(), c.getName(), c.getLatitude(), c.getLongitude(), c.getExternalId(), playedIds.contains(c.getId())))
                .collect(Collectors.toList());
    }

    // --- 2. GET PLAYED COURSES ---
    public List<CourseDto> getPlayedCourses(Long userId) {
        return playedCourseRepository.findByUserIdWithCourse(userId).stream()
                .map(pc -> new CourseDto(pc.getCourse().getId(), pc.getCourse().getName(), pc.getCourse().getLatitude(), pc.getCourse().getLongitude(), pc.getCourse().getExternalId(), true))
                .collect(Collectors.toList());
    }

    // --- 3. LOG ROUND ---
    public RoundDto logRound(User user, RoundRequest request) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        // A. Create Round
        Round round = new Round();
        round.setUser(user);
        round.setCourse(course);
        round.setScore(request.getScore());
        round.setDate(LocalDate.parse(request.getDate()));
        Round savedRound = roundRepository.save(round);

        // B. Auto-mark as Played
        if (!playedCourseRepository.existsByUserIdAndCourseId(user.getId(), course.getId())) {
            playedCourseRepository.save(new PlayedCourse(user, course));
        }

        return new RoundDto(savedRound.getId(), savedRound.getCourse().getId(), savedRound.getCourse().getName(), savedRound.getDate().toString(), savedRound.getScore());
    }

    // --- 4. DELETE ROUND ---
    public void deleteRound(Long roundId, User user) {
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!round.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Course course = round.getCourse();
        roundRepository.delete(round);

        // Smart Logic: Un-mark course if no rounds left
        if (!roundRepository.existsByUserIdAndCourseId(user.getId(), course.getId())) {
            playedCourseRepository.findByUserIdAndCourseId(user.getId(), course.getId())
                    .ifPresent(playedCourseRepository::delete);
        }
    }

    // --- 5. DASHBOARD STATS (The Big Logic) ---
    public DashboardStats getDashboardStats(User user) {
        List<Course> allCourses = courseRepository.findAll();
        Set<Long> playedIds = playedCourseRepository.findByUserId(user.getId())
                .stream().map(pc -> pc.getCourse().getId()).collect(Collectors.toSet());

        // A. Regional Data Calculation
        Map<String, DashboardStats.RegionStat> regionalData = new HashMap<>();
        Map<String, List<Course>> byCounty = allCourses.stream()
                .collect(Collectors.groupingBy(c -> c.getCounty() != null ? c.getCounty() : "Unknown"));

        for (var entry : byCounty.entrySet()) {
            List<CourseDto> regionDtos = entry.getValue().stream()
                    .map(c -> new CourseDto(c.getId(), c.getName(), c.getLatitude(), c.getLongitude(), c.getExternalId(), playedIds.contains(c.getId())))
                    .sorted(Comparator.comparing(CourseDto::played).reversed().thenComparing(CourseDto::name))
                    .collect(Collectors.toList());

            long playedCount = entry.getValue().stream().filter(c -> playedIds.contains(c.getId())).count();
            regionalData.put(entry.getKey(), new DashboardStats.RegionStat(playedCount, entry.getValue().size(), regionDtos));
        }

        // B. Recent Rounds
        List<DashboardStats.RoundSummary> recentRounds = roundRepository.findByUserIdOrderByDateDescIdDesc(user.getId()).stream()
                .limit(5)
                .map(r -> new DashboardStats.RoundSummary(r.getId(), r.getCourse().getName(), r.getDate().toString(), r.getScore()))
                .collect(Collectors.toList());

        // C. Build Response
        DashboardStats stats = new DashboardStats();
        stats.setDisplayName(user.getFullName());
        stats.setAvatar(user.getAvatar());
        stats.setEmail(user.getEmail());
        stats.setTotalPlayed(playedIds.size());
        stats.setTotalCourses(allCourses.size());
        stats.setPercentageComplete(allCourses.isEmpty() ? 0 : (double) playedIds.size() / allCourses.size() * 100);
        stats.setRegionStats(regionalData);
        stats.setRecentRounds(recentRounds);

        return stats;
    }

    // --- 6. Mark Played Manually ---
    public List<CourseDto> markCoursePlayed(Long userId, String externalId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Course course = courseRepository.findByExternalId(externalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!playedCourseRepository.existsByUserIdAndCourseId(userId, course.getId())) {
            playedCourseRepository.save(new PlayedCourse(user, course));
        }
        return getPlayedCourses(userId);
    }
}