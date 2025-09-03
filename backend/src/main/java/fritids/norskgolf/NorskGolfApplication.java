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
import java.util.List;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;


@SpringBootApplication
@EnableWebSecurity
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
            User user = userRepository.findByIdWithPlayedCourses(
                    userRepository.findByUsername("testuser").map(User::getId).orElse(null)
            ).orElse(null);
            List<Course> bergenList = courseRepository.findByName("Bergen golfklubb");
            if (user != null && bergenList != null && !bergenList.isEmpty()) {
                Course bergen = bergenList.get(0);
                if (!user.getPlayedCourses().contains(bergen)) {
                    user.getPlayedCourses().add(bergen);
                    userRepository.save(user);
                }
            }
        };
    }



    //Mock data for testing purposes
    @Bean
    public CommandLineRunner loadTestCourses(CourseRepository courseRepository) {
        return args -> {
            if (courseRepository.findByName("Bergen golfklubb").isEmpty()) {
                Course bergen = new Course();
                bergen.setExternalId("bergen-001");
                bergen.setName("Bergen golfklubb");
                bergen.setLatitude(60.3971);
                bergen.setLongitude(5.3245);
                courseRepository.save(bergen);
            }
            if (courseRepository.findByName("Fana golfklubb").isEmpty()){
                Course fana = new Course();
                fana.setExternalId("fana-001");
                fana.setName("Fana golfklubb");
                fana.setLatitude(60.2822);
                fana.setLongitude(5.3221);
                courseRepository.save(fana);
            }
            if (courseRepository.findByName("Bjørnafjorden golfklubb").isEmpty()) {
                Course bjorn = new Course();
                bjorn.setExternalId("bjorn-001");
                bjorn.setName("Bjørnafjorden golfklubb");
                bjorn.setLatitude(60.1886);
                bjorn.setLongitude(5.4827);
                courseRepository.save(bjorn);
            }
        };
    }

}
