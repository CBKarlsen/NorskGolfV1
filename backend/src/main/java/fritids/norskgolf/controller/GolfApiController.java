package fritids.norskgolf.controller;

import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.entities.PlayedCourse;
import fritids.norskgolf.repository.CourseRepository;
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

    @GetMapping("/overview")
    public ResponseEntity<DashboardStats> getOverviewStats(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. Resolve the User (Handles both Test User & Google User)
        User user = resolveUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 1. Fetch all data
        List<Course> allCourses = courseRepository.findAll();
        Set<Long> playedCourseIds = playedCourseRepository.findByUserId(user.getId())
                .stream()
                .map(pc -> pc.getCourse().getId())
                .collect(Collectors.toSet());

        // 2. Aggregate by County
        Map<String, DashboardStats.RegionStat> regionalData = new HashMap<>();

        // Group courses by county (handle nulls with "Unknown")
        // NOTE: Make sure your Course entity has the getCounty() method!
        Map<String, List<Course>> coursesByCounty = allCourses.stream()
                .collect(Collectors.groupingBy(c -> c.getCounty() != null ? c.getCounty() : "Unknown"));

        // Calculate stats for each county
        for (Map.Entry<String, List<Course>> entry : coursesByCounty.entrySet()) {
            String countyName = entry.getKey();
            List<Course> coursesInCounty = entry.getValue();

            long totalInRegion = coursesInCounty.size();
            long playedInRegion = coursesInCounty.stream()
                    .filter(c -> playedCourseIds.contains(c.getId()))
                    .count();

            regionalData.put(countyName, new DashboardStats.RegionStat(playedInRegion, totalInRegion));
        }

        // 3. Build Response
        DashboardStats stats = new DashboardStats();
        stats.setTotalPlayed(playedCourseIds.size());
        stats.setTotalCourses(allCourses.size());
        stats.setPercentageComplete((double) playedCourseIds.size() / allCourses.size() * 100);
        stats.setRegionStats(regionalData);

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

    // --- DTOs ---
    private record UserDto(Long id, String username) {}

    // Make sure this definition stays here
    private record CourseDto(Long id, String name, Double latitude, Double longitude, String externalId, boolean played) {}

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