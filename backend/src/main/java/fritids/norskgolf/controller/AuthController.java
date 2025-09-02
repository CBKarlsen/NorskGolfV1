package fritids.norskgolf.controller;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        Optional<User> userOptional = userRepository.findByUsername(loginRequest.getUsername());

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            // TODO Change this such that it is secure
            if (user.getPassword().equals(loginRequest.getPassword())) {
                return ResponseEntity.ok(new AuthenticationResponse(user.getId(), user.getUsername()));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid username or password"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegistrationRequest registrationRequest) {
        if (userRepository.findByUsername(registrationRequest.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Username already exists"));
        }

        if (registrationRequest.getPassword() == null || registrationRequest.getPassword().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Password cannot be empty"));
        }

        User newUser = new User();
        newUser.setUsername(registrationRequest.getUsername());
        newUser.setPassword(registrationRequest.getPassword()); // Remember: plain text for now!
        User savedUser = userRepository.save(newUser);

        return new ResponseEntity<>(new RegistrationResponse(savedUser.getId(), savedUser.getUsername(), "User registered successfully"), HttpStatus.CREATED);
    }

    private static class LoginRequest {
        private String username;
        private String password;

        // Getters and setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    private static class RegistrationRequest {
        private String username;
        private String password;

        // Getters and setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    private static class AuthenticationResponse {
        private Long userId;
        private String username;

        public AuthenticationResponse(Long userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        // Getters
        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }
    }

    private static class RegistrationResponse {
        private Long userId;
        private String username;
        private String message;

        public RegistrationResponse(Long userId, String username, String message) {
            this.userId = userId;
            this.username = username;
            this.message = message;
        }

        // Getters
        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        // Getter
        public String getMessage() {
            return message;
        }
    }
}