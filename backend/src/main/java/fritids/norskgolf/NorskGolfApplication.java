package fritids.norskgolf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import fritids.norskgolf.repository.UserRepository;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.entities.PlayedCourse;
import fritids.norskgolf.repository.PlayedCourseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import java.util.List;

@SpringBootApplication
@EnableWebSecurity
public class NorskGolfApplication {
    public static void main(String[] args) {
        SpringApplication.run(NorskGolfApplication.class, args);
    }


    @Bean
    public CommandLineRunner setPlayedCourse(UserRepository userRepository,
                                             CourseRepository courseRepository,
                                             PlayedCourseRepository playedCourseRepository) {
        return args -> {
            Long testUserId = userRepository.findByUsername("testuser").map(User::getId).orElse(null);
            if (testUserId == null) return;

            var user = userRepository.findById(testUserId).orElse(null);
            if (user == null) return;

            var bergenList = courseRepository.findByName("Bergen golfklubb");
            if (bergenList == null || bergenList.isEmpty()) return;

            var bergen = bergenList.get(0);
            boolean exists = playedCourseRepository.existsByUserIdAndCourseId(user.getId(), bergen.getId());
            if (!exists) {
                playedCourseRepository.save(new fritids.norskgolf.entities.PlayedCourse(user, bergen));
            }
        };
    }

/*
    @Bean
    public CommandLineRunner loadTestCourses(CourseRepository courseRepository) {
        return args -> {
            if (courseRepository.findByName("Bergen golfklubb").isEmpty()) {
                Course bergen = new Course();
                bergen.setExternalId("bergen-001");
                bergen.setName("Bergen golfklubb");
                bergen.setCounty("Vestland");
                bergen.setLatitude(60.3971);
                bergen.setLongitude(5.3245);
                courseRepository.save(bergen);
            }
            if (courseRepository.findByName("Fana golfklubb").isEmpty()){
                Course fana = new Course();
                fana.setExternalId("fana-001");
                fana.setName("Fana golfklubb");
                fana.setCounty("Vestland");
                fana.setLatitude(60.2822);
                fana.setLongitude(5.3221);
                courseRepository.save(fana);
            }
            if (courseRepository.findByName("Bjørnafjorden golfklubb").isEmpty()) {
                Course bjorn = new Course();
                bjorn.setExternalId("bjorn-001");
                bjorn.setName("Bjørnafjorden golfklubb");
                bjorn.setCounty("Vestland");
                bjorn.setLatitude(60.1886);
                bjorn.setLongitude(5.4827);
                courseRepository.save(bjorn);
            }
        };
    }*/
}