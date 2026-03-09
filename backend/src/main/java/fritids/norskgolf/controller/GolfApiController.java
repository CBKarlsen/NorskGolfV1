package fritids.norskgolf.controller;

import fritids.norskgolf.dto.*;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.service.GolfService;
import fritids.norskgolf.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class GolfApiController {

    @Autowired private GolfService golfService;
    @Autowired private UserService userService; // Used for resolving Principal

    @GetMapping("/courses")
    public ResponseEntity<List<CourseDto>> getAllCourses(Principal principal) {
        User user = (principal != null) ? userService.resolveUser(principal) : null;
        return ResponseEntity.ok(golfService.getAllCourses(user));
    }

    @GetMapping("/rounds")
    public ResponseEntity<List<RoundDto>> getRounds(Principal principal) {
        User user = userService.resolveUser(principal);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(golfService.getRoundsForUser(user));
    }

    @PostMapping("/rounds")
    public ResponseEntity<RoundDto> logRound(@RequestBody RoundRequest request, Principal principal) {
        User user = userService.resolveUser(principal);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(golfService.logRound(user, request));
    }

    @DeleteMapping("/rounds/{roundId}")
    public ResponseEntity<Void> deleteRound(@PathVariable Long roundId, Principal principal) {
        User user = userService.resolveUser(principal);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        golfService.deleteRound(roundId, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/overview")
    public ResponseEntity<DashboardStats> getOverviewStats(Principal principal) {
        User user = userService.resolveUser(principal);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(golfService.getDashboardStats(user));
    }

    @PostMapping("/courses/mark-played")
    public ResponseEntity<List<CourseDto>> markCourseAsPlayed(
            @RequestBody PlayedCourseRequest body,
            Principal principal
    ) {
        User user = userService.resolveUser(principal);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();


        return ResponseEntity.ok(golfService.markCoursePlayed(user.getId(), body.getCourseExternalId()));
    }

    // Flytt til egen fil
    private static class PlayedCourseRequest {
        private String courseExternalId;
        public String getCourseExternalId() { return courseExternalId; }
        public void setCourseExternalId(String courseExternalId) { this.courseExternalId = courseExternalId; }
    }
}