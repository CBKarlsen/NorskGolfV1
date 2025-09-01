package fritids.norskgolf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import fritids.norskgolf.repository.UserRepository;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.entities.Course;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class NorskGolfApplication {
    public static void main(String[] args) {
        SpringApplication.run(NorskGolfApplication.class, args);
    }

    @Bean
    public CommandLineRunner loadTestUser(UserRepository userRepository) {
        return args -> {
            if (userRepository.findByUsername("testuser").isEmpty()) {
                User user = new User();
                user.setUsername("testuser");
                user.setPassword("testpass"); // Plain text for testing only
                userRepository.save(user);
            }
        };
    }

    @Bean
    public CommandLineRunner setPlayedCourse(UserRepository userRepository, CourseRepository courseRepository) {
        return args -> {
            User user = userRepository.findByUsername("testuser").orElse(null);
            Course bergen = courseRepository.findByName("Bergen golfklubb");
            if (user != null && bergen != null) {
                if (!user.getPlayedCourses().contains(bergen)) {
                    user.getPlayedCourses().add(bergen);
                    userRepository.save(user);
                }
            }
        };
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:3000")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
    //Mock data for testing purposes
    @Bean
    public CommandLineRunner loadTestCourses(CourseRepository courseRepository) {
        return args -> {
            if (courseRepository.findByName("Bergen golfklubb") == null) {
                Course bergen = new Course();
                bergen.setName("Bergen golfklubb");
                bergen.setLatitude(60.3971);
                bergen.setLongitude(5.3245);
                courseRepository.save(bergen);
            }
            if (courseRepository.findByName("Fana golfklubb") == null) {
                Course fana = new Course();
                fana.setName("Fana golfklubb");
                fana.setLatitude(60.2822);
                fana.setLongitude(5.3221);
                courseRepository.save(fana);
            }
            if (courseRepository.findByName("Bjørnafjorden golfklubb") == null) {
                Course bjorn = new Course();
                bjorn.setName("Bjørnafjorden golfklubb");
                bjorn.setLatitude(60.1886);
                bjorn.setLongitude(5.4827);
                courseRepository.save(bjorn);
            }
        };
    }

}
