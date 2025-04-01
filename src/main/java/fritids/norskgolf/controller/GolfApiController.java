package fritids.norskgolf.controller;

import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users") // Changed to /api/users for clarity
public class GolfApiController {

    @Autowired
    private UserRepository userRepository;

    // --- Get User ---
    @GetMapping("/{userId}")
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
    @GetMapping("/{userId}/played-courses")
    public ResponseEntity<?> getPlayedCourses(@PathVariable Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            List<String> playedCourseExternalIds = user.getPlayedCourses().stream()
                    .map(Course::getExternalId) // Assuming externalId is the key
                    .collect(Collectors.toList());
            return ResponseEntity.ok(playedCourseExternalIds);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
        }
    }



    @PostMapping("/{userId}/played-courses")
    public ResponseEntity<?> markCourseAsPlayed(@PathVariable Long userId, @RequestBody PlayedCourseRequest playedCourseRequest) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (!userOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
        }

        User user = userOptional.get();

        // Assuming you receive the externalId from the frontend
        String courseExternalId = playedCourseRequest.getCourseExternalId();

        // You don't need to fetch  the Course from your database anymore.
        // The frontend will use the externalId to get course details from the other database.

        // You still need to create a Course object to add to user's played courses.
        // But the Course object does not need to be saved to the database.
        Course course = new Course();
        course.setExternalId(courseExternalId);

        user.getPlayedCourses().add(course);
        userRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // --- Data Transfer Objects (DTOs) ---
        private record UserDto(Long id, String username) {
    }

    private static class PlayedCourseRequest {
        private String courseExternalId;

        public String getCourseExternalId() {
            return courseExternalId;
        }

        public void setCourseExternalId(String courseExternalId) {
            this.courseExternalId = courseExternalId;
        }
    }

    private static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;

        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}