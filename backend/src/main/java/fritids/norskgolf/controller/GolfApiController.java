package fritids.norskgolf.controller;

import fritids.norskgolf.dto.RoundRequest;
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.Round;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.entities.PlayedCourse;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.RoundRepository;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import fritids.norskgolf.repository.PlayedCourseRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import fritids.norskgolf.dto.DashboardStats;
import java.security.Principal;
import fritids.norskgolf.dto.RoundDto;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class GolfApiController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private PlayedCourseRepository playedCourseRepository;


    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> getAllCourses(@AuthenticationPrincipal OAuth2AuthenticationToken token) {
        // 1. Get all courses
        List<Course> allCourses = courseRepository.findAll();

        // 2. Determine which are played (if user is logged in)
        Set<Long> playedCourseIds = new HashSet<>();
        if (token != null) {
            String providerId = token.getPrincipal().getAttribute("sub");
            userRepository.findByProviderId(providerId).ifPresent(user -> {
                playedCourseRepository.findByUserId(user.getId())
                        .forEach(pc -> playedCourseIds.add(pc.getCourse().getId()));
            });
        }

        // 3. Map to DTO (Fixes argument mismatch error)
        List<CourseDto> dtos = allCourses.stream()
                .map(course -> new CourseDto(
                        course.getId(),
                        course.getName(),
                        course.getLatitude(),
                        course.getLongitude(),
                        course.getExternalId(),
                        playedCourseIds.contains(course.getId()) // <--- Now passes the 6th argument correctly
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // --- Get User ---
    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUser(@PathVariable Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            UserDto userDto = new UserDto(user.getId(), user.getUsername());
            return ResponseEntity.ok(userDto);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
        }
    }

    // --- Get User's Played Courses ---
    @GetMapping("/users/{userId}/played-courses")
    public ResponseEntity<List<CourseDto>> getPlayedCourses(@PathVariable Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        var played = playedCourseRepository.findByUserIdWithCourse(userId).stream()
                .map(pc -> new CourseDto(
                        pc.getCourse().getId(),
                        pc.getCourse().getName(),
                        pc.getCourse().getLatitude(),
                        pc.getCourse().getLongitude(),
                        pc.getCourse().getExternalId(),
                        true)) // <--- Fixed: Was 'false', changed to 'true'
                .toList();
        return ResponseEntity.ok(played);
    }

    @Autowired
    private RoundRepository roundRepository;

    @PostMapping("/rounds")
    public ResponseEntity<?> loground(@RequestBody RoundRequest request, Principal principal){
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        User user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        // 1. Create and Save the Entity
        Round round = new Round();
        round.setUser(user);
        round.setCourse(course);
        round.setScore(request.getScore());
        round.setDate(java.time.LocalDate.parse(request.getDate()));

        Round savedRound = roundRepository.save(round);

        // 2. Sync "Played" status
        boolean isAlreadyPlayed = playedCourseRepository.existsByUserIdAndCourseId(user.getId(), course.getId());
        if (!isAlreadyPlayed) {
            playedCourseRepository.save(new fritids.norskgolf.entities.PlayedCourse(user, course));
        }

        // 3. FIX: Create a safe DTO to return
        // This stops the infinite loop because RoundDto does NOT contain the 'User' object
        RoundDto responseDto = new RoundDto(
                savedRound.getId(),
                savedRound.getCourse().getId(),
                savedRound.getCourse().getName(),
                savedRound.getDate().toString(),
                savedRound.getScore()
        );

        // 4. Return the DTO
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/rounds")
    public ResponseEntity<List<RoundDto>> getUserRounds(Principal principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        User user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        List<Round> rounds = roundRepository.findByUserIdOrderByDateDescIdDesc(user.getId());

        // Convert to DTO
        List<RoundDto> dtos = rounds.stream()
                .map(r -> new RoundDto(
                        r.getId(),
                        r.getCourse().getId(),
                        r.getCourse().getName(),
                        r.getDate().toString(),
                        r.getScore()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }


    @GetMapping("/overview")
    public ResponseEntity<DashboardStats> getOverviewStats(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // --- 1. EXISTING LOGIC: Fetch Stats & Regions ---
        List<Course> allCourses = courseRepository.findAll();
        Set<Long> playedCourseIds = playedCourseRepository.findByUserId(user.getId())
                .stream()
                .map(pc -> pc.getCourse().getId())
                .collect(Collectors.toSet());

        Map<String, DashboardStats.RegionStat> regionalData = new HashMap<>();
        Map<String, List<Course>> coursesByCounty = allCourses.stream()
                .collect(Collectors.groupingBy(c -> c.getCounty() != null ? c.getCounty() : "Unknown"));

        for (Map.Entry<String, List<Course>> entry : coursesByCounty.entrySet()) {
            String countyName = entry.getKey();
            List<Course> coursesInCounty = entry.getValue();

            long totalInRegion = coursesInCounty.size();
            long playedInRegion = coursesInCounty.stream()
                    .filter(c -> playedCourseIds.contains(c.getId()))
                    .count();

            List<CourseDto> regionCourseDtos = coursesInCounty.stream()
                    .map(c -> new CourseDto(
                            c.getId(),
                            c.getName(),
                            c.getLatitude(),
                            c.getLongitude(),
                            c.getExternalId(),
                            playedCourseIds.contains(c.getId())
                    ))
                    .sorted(Comparator.comparing(CourseDto::played).reversed()
                            .thenComparing(CourseDto::name))
                    .collect(Collectors.toList());

            regionalData.put(countyName, new DashboardStats.RegionStat(playedInRegion, totalInRegion, regionCourseDtos));
        }

        // --- 2. NEW LOGIC: Fetch Recent Rounds ---
        // Fetch all rounds for this user, newest first
        List<Round> allRounds = roundRepository.findByUserIdOrderByDateDescIdDesc(user.getId());

        // Take the top 5 and convert them to your DTO
        List<DashboardStats.RoundSummary> recentRounds = allRounds.stream()
                .limit(5)
                .map(r -> new DashboardStats.RoundSummary(
                        r.getId(),
                        r.getCourse().getName(),
                        r.getDate().toString(),
                        r.getScore()
                ))
                .collect(Collectors.toList());

        // --- 3. Build & Return Response ---
        DashboardStats stats = new DashboardStats();
        stats.setDisplayName(user.getFullName()); // Ensure user.getFullName() exists!
        stats.setAvatar(user.getAvatar());
        stats.setEmail(user.getEmail());

        // Set Stats
        stats.setTotalPlayed(playedCourseIds.size());
        stats.setTotalCourses(allCourses.size());
        stats.setPercentageComplete(allCourses.isEmpty() ? 0 : (double) playedCourseIds.size() / allCourses.size() * 100);
        stats.setRegionStats(regionalData);
        stats.setRecentRounds(recentRounds);

        // Optional: Calculate Best Score (Lowest score ever)
        // int best = allRounds.stream().mapToInt(Round::getScore).min().orElse(0);
        // stats.setBestScore(best);

        return ResponseEntity.ok(stats);
    }

    private User resolveUser(Principal principal) {
        String principalName = principal.getName();

        // 1. Try finding by username (for "testuser")
        Optional<User> byUsername = userRepository.findByUsername(principalName);
        if (byUsername.isPresent()) {
            return byUsername.get();
        }

        // 2. Try finding by providerId (for OAuth/Google users)
        // (In some configs, principal.getName() returns the provider ID)
        return userRepository.findByProviderId(principalName).orElse(null);
    }

    // --- Mark Course as Played ---
    @PostMapping("/users/{userId}/mark-played")
    public ResponseEntity<?> markCourseAsPlayed(
            @PathVariable Long userId,
            @RequestBody PlayedCourseRequest body,
            OAuth2AuthenticationToken token
    ) {
        String providerId = token.getPrincipal().getAttribute("sub");
        var authUserOpt = userRepository.findByProviderId(providerId);

        if (authUserOpt.isEmpty() || !authUserOpt.get().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Forbidden"));
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        var course = courseRepository.findByExternalId(body.getCourseExternalId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        boolean exists = playedCourseRepository.existsByUserIdAndCourseId(userId, course.getId());
        if (!exists) {
            try {
                playedCourseRepository.save(new fritids.norskgolf.entities.PlayedCourse(user, course));
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Ignore duplicate
            }
        }

        var played = playedCourseRepository.findByUserIdWithCourse(userId).stream()
                .map(pc -> new CourseDto(
                        pc.getCourse().getId(),
                        pc.getCourse().getName(),
                        pc.getCourse().getLatitude(),
                        pc.getCourse().getLongitude(),
                        pc.getCourse().getExternalId(),
                        true)) // Already correct here
                .toList();

        return ResponseEntity.ok(played);
    }

    @DeleteMapping("/rounds/{roundId}")
    public ResponseEntity<?> deleteRound(@PathVariable Long roundId, Principal principal) {
        User user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // 1. Find the round and ensure it belongs to the user
        Round round = roundRepository.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!round.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Course course = round.getCourse();

        // 2. Delete the round
        roundRepository.delete(round);

        // 3. SMART LOGIC: Check if we should un-mark the course as played
        // Are there any other rounds left for this user at this course?
        boolean hasOtherRounds = roundRepository.existsByUserIdAndCourseId(user.getId(), course.getId());

        if (!hasOtherRounds) {
            // If no rounds left, delete the "PlayedCourse" entry (Turn marker RED)
            playedCourseRepository.findByUserIdAndCourseId(user.getId(), course.getId())
                    .ifPresent(playedCourseRepository::delete);
        }

        return ResponseEntity.ok().build();
    }


    // --- DTOs ---
    private record UserDto(Long id, String username) {}


    public record CourseDto(Long id, String name, Double latitude, Double longitude, String externalId, boolean played) {}

    private static class PlayedCourseRequest {
        private String courseExternalId;
        public String getCourseExternalId() { return courseExternalId; }
        public void setCourseExternalId(String courseExternalId) { this.courseExternalId = courseExternalId; }
    }

    private static class ErrorResponse {
        private String message;
        public ErrorResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}