package fritids.norskgolf.service;

import fritids.norskgolf.entities.Course;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.service.clubs.ClubListLoader;
import fritids.norskgolf.service.clubs.ClubMatcher;
import fritids.norskgolf.service.clubs.ClubRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CourseSyncService {

    private static final Logger log = LoggerFactory.getLogger(CourseSyncService.class);
    private static final String CLUB_LIST = "golf_clubs.json";

    private final CourseRepository courseRepository;
    private final ClubListLoader loader;
    private final ClubMatcher matcher;

    @Value("${app.clubs.dry-run:false}")
    private boolean dryRun;

    public CourseSyncService(CourseRepository courseRepository, ClubListLoader loader, ClubMatcher matcher) {
        this.courseRepository = courseRepository;
        this.loader = loader;
        this.matcher = matcher;
    }

    public record SyncSummary(int matched, int inserted, int deactivated, List<String> ambiguous) {}

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        SyncSummary summary = reconcile(loader.load(CLUB_LIST), dryRun);
        log.info("Club sync{}: {} matched, {} inserted, {} deactivated, {} ambiguous",
                dryRun ? " (DRY RUN)" : "", summary.matched(), summary.inserted(),
                summary.deactivated(), summary.ambiguous().size());
        summary.ambiguous().forEach(name -> log.warn("Ambiguous club, needs a human: {}", name));
    }

    @Transactional
    public SyncSummary reconcile(List<ClubRecord> clubs, boolean dryRunMode) {
        List<Course> existing = new ArrayList<>(courseRepository.findAll());
        List<Course> unclaimed = new ArrayList<>(existing);
        List<String> ambiguous = new ArrayList<>();
        int matched = 0;
        int inserted = 0;

        for (ClubRecord club : clubs) {
            Course byId = existing.stream()
                    .filter(c -> club.clubId().equals(c.getExternalId()))
                    .findFirst()
                    .orElse(null);

            ClubMatcher.Match match = byId != null
                    ? new ClubMatcher.Match(byId, false)
                    : matcher.match(club, unclaimed);

            if (match.ambiguous()) {
                ambiguous.add(club.name());
                continue;
            }

            Course course = match.course();
            if (course == null) {
                course = new Course();
                inserted++;
            } else {
                unclaimed.remove(course);
                matched++;
            }

            apply(club, course);
            if (!dryRunMode) courseRepository.save(course);
        }

        int deactivated = 0;
        for (Course leftover : unclaimed) {
            if (!leftover.isActive()) continue;
            deactivated++;
            leftover.setActive(false);
            if (!dryRunMode) courseRepository.save(leftover);
        }

        return new SyncSummary(matched, inserted, deactivated, ambiguous);
    }

    private void apply(ClubRecord club, Course course) {
        course.setExternalId(club.clubId());
        course.setName(club.name());
        course.setLatitude(club.lat());
        course.setLongitude(club.lon());
        course.setMunicipality(club.municipality());
        course.setCounty(club.county());
        course.setHoles(club.holes());
        course.setActive(true);
    }
}
