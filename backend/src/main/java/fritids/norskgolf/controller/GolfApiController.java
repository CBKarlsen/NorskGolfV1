package fritids.norskgolf.controller;

import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

@RestController
@RequestMapping("/api")
public class GolfApiController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    // --- Get All Courses ---
    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> getAllCourses() {
        List<CourseDto> courses = courseRepository.findAll().stream()
                .map(course -> new CourseDto(course.getId(), course.getName(), course.getLatitude(), course.getLongitude(), course.getExternalId()))
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
        User user = userRepository.findByIdWithPlayedCourses(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        List<CourseDto> playedCourses = user.getPlayedCourses().stream()
                .map(course -> new CourseDto(course.getId(), course.getName(), course.getLatitude(), course.getLongitude(), course.getExternalId()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(playedCourses);
    }

    // --- Mark Course as Played ---
    @PostMapping("/users/{userId}/mark-played")
    public ResponseEntity<?> markCourseAsPlayed(
            @PathVariable Long userId,
            @RequestBody PlayedCourseRequest playedCourseRequest,
            OAuth2AuthenticationToken token
    ) {
        // Get current authenticated user's providerId
        String providerId = token.getPrincipal().getAttribute("sub");
        System.out.println("Requested userId: " + userId);
        System.out.println("Authenticated providerId: " + providerId);

        Optional<User> authUserOpt = userRepository.findByProviderId(providerId);
        if (authUserOpt.isPresent()) {
            System.out.println("Authenticated user DB id: " + authUserOpt.get().getId());
        } else {
            System.out.println("Authenticated user not found in DB");
        }

        if (authUserOpt.isEmpty() || !authUserOpt.get().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Forbidden"));
        }

        Optional<User> userOptional = userRepository.findByIdWithPlayedCourses(userId);
        Optional<Course> courseOptional = courseRepository.findByExternalId(playedCourseRequest.getCourseExternalId());

        if (userOptional.isEmpty() || courseOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User or course not found"));
        }

        User user = userOptional.get();
        Course course = courseOptional.get();

        user.addPlayedCourse(course);
        userRepository.save(user);

        // Fetch user with playedCourses initialized
        User updatedUser = userRepository.findByIdWithPlayedCourses(userId).orElse(user);
        List<CourseDto> playedCourses = updatedUser.getPlayedCourses().stream()
                .map(c -> new CourseDto(c.getId(), c.getName(), c.getLatitude(), c.getLongitude(), c.getExternalId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(playedCourses);
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