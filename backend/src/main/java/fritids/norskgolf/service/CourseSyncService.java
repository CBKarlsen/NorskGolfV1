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

    /** A club list that would deactivate more than this share of the active courses is refused. */
    private static final double MAX_DEACTIVATION_SHARE = 0.30;
    /** Below this many active courses a percentage is noise — a fresh or test database, not the real list. */
    private static final int GUARD_FLOOR = 10;

    /** One planned write. A null course means "insert this club". */
    private record Decision(Course course, ClubRecord club) {}

    // @Transactional belongs HERE, not only on reconcile(): syncOnStartup calls reconcile on
    // itself, and self-invocation never passes the Spring proxy. Spring's event multicaster does
    // invoke this listener through the proxy, so the whole import runs as one transaction.
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        SyncSummary summary;
        try {
            summary = reconcile(loader.load(CLUB_LIST), dryRun);
        } catch (RuntimeException e) {
            // A refused or unreadable club list must not stop the app from starting: the existing
            // course table is perfectly usable, it just isn't updated this boot.
            log.error("Club sync skipped, database left untouched: {}", e.getMessage());
            return;
        }
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
        List<Decision> decisions = new ArrayList<>();
        int matched = 0;
        int inserted = 0;

        // Phase 1: decide. Nothing is mutated here — existing rows are MANAGED entities, and a
        // setter alone would be flushed at commit, so no decision may touch one until the
        // runaway guard below has passed and dryRunMode has been checked.
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
                // The club IS present in the curated list; the matcher just couldn't pick a
                // winner among several nearby candidates. Pull those candidates out of
                // "unclaimed" so they are not deactivated as if the club were absent — they
                // keep their current state until a human resolves the collision.
                unclaimed.removeAll(matcher.nearby(club, unclaimed));
                continue;
            }

            Course course = match.course();
            if (course == null) {
                inserted++;
            } else {
                unclaimed.removeIf(c -> c == course);
                matched++;
            }
            decisions.add(new Decision(course, club));
        }

        List<Course> toDeactivate = unclaimed.stream().filter(Course::isActive).toList();

        // Phase 2: guard. A wrong or truncated list looks exactly like "almost every club closed".
        long activeBefore = existing.stream().filter(Course::isActive).count();
        if (!dryRunMode && activeBefore >= GUARD_FLOOR && toDeactivate.size() > activeBefore * MAX_DEACTIVATION_SHARE) {
            throw new IllegalStateException(String.format(
                    "club list would deactivate %d of %d active courses (limit %.0f%%) — refusing to write",
                    toDeactivate.size(), activeBefore, MAX_DEACTIVATION_SHARE * 100));
        }

        // Phase 3: log every decision, then write. A dry run logs all of it so the diff can be
        // read; a real run logs only what changed, so a stable list doesn't spam every boot.
        for (Decision d : decisions) {
            if (d.course() == null) {
                log.info("Club sync: INSERT {} ({})", d.club().name(), d.club().clubId());
                if (!dryRunMode) {
                    Course course = new Course();
                    apply(d.club(), course);
                    courseRepository.save(course);
                }
            } else {
                boolean renamed = !d.club().name().equals(d.course().getName());
                if (dryRunMode || renamed) {
                    log.info("Club sync: MATCH existing '{}' -> '{}' ({}, {})",
                            d.course().getName(), d.club().name(), d.club().clubId(), distance(d));
                }
                if (!dryRunMode) {
                    apply(d.club(), d.course());
                    courseRepository.save(d.course());
                }
            }
        }

        for (Course leftover : toDeactivate) {
            log.info("Club sync: DEACTIVATE '{}' ({}) — no club in the list matched it",
                    leftover.getName(), leftover.getExternalId());
            if (!dryRunMode) {
                leftover.setActive(false);
                courseRepository.save(leftover);
            }
        }

        return new SyncSummary(matched, inserted, toDeactivate.size(), ambiguous);
    }

    private static String distance(Decision d) {
        Course c = d.course();
        if (c.getLatitude() == null || c.getLongitude() == null) return "no coordinates";
        return String.format("%.2f km away", ClubMatcher.distanceKm(
                d.club().lat(), d.club().lon(), c.getLatitude(), c.getLongitude()));
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
