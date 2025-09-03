package fritids.norskgolf.controller;

import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.entities.PlayedCourse;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import fritids.norskgolf.repository.PlayedCourseRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import java.util.List;
import java.util.Optional;
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


    // --- Get All Courses ---
    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> getAllCourses() {
        List<CourseDto> courses = courseRepository.findAll().stream()
                .map(course -> new CourseDto(
                        course.getId(),
                        course.getName(),
                        course.getLatitude(),
                        course.getLongitude(),
                        course.getExternalId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(courses);
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
                        pc.getCourse().getExternalId()))
                .toList();
        return ResponseEntity.ok(played);
    }

    // --- Mark Course as Played ---
    @PostMapping("/users/{userId}/mark-played")
    public ResponseEntity<?> markCourseAsPlayed(
            @PathVariable Long userId,
            @RequestBody PlayedCourseRequest body,
            OAuth2AuthenticationToken token
    ) {
        // Auth
        String providerId = token.getPrincipal().getAttribute("sub");
        var authUserOpt = userRepository.findByProviderId(providerId);
        if (authUserOpt.isEmpty() || !authUserOpt.get().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Forbidden"));
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        var course = courseRepository.findByExternalId(body.getCourseExternalId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        // **DB-level idempotency check**
        boolean exists = playedCourseRepository.existsByUserIdAndCourseId(userId, course.getId());
        if (!exists) {
            try {
                playedCourseRepository.save(new fritids.norskgolf.entities.PlayedCourse(user, course));
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Race condition safety: if another request created it milliseconds earlier,
                // treat as success (idempotent behavior).
            }
        }

        // Return updated played list
        var played = playedCourseRepository.findByUserIdWithCourse(userId).stream()
                .map(pc -> new CourseDto(
                        pc.getCourse().getId(),
                        pc.getCourse().getName(),
                        pc.getCourse().getLatitude(),
                        pc.getCourse().getLongitude(),
                        pc.getCourse().getExternalId()))
                .toList();

        return ResponseEntity.ok(played);
    }

    // --- DTOs ---
    private record UserDto(Long id, String username) {}

    private record CourseDto(Long id, String name, Double latitude, Double longitude, String externalId) {}

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