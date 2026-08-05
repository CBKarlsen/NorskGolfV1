package fritids.norskgolf.service.clubs;

import fritids.norskgolf.entities.Course;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class ClubMatcher {

    /** Two clubs closer than this are near enough to be the same place with a differently-placed pin. */
    private static final double MAX_KM = 3.0;

    public record Match(Course course, boolean ambiguous) {}

    public static String normalise(String name) {
        if (name == null) return "";
        String folded = name.toLowerCase(Locale.ROOT)
                .replace("ø", "o").replace("æ", "ae").replace("å", "a")
                .replace("aa", "a");
        folded = Normalizer.normalize(folded, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return folded.replaceAll("\\b(golfklubb|golfpark|golfbane|golf|klubb|gk)\\b", " ")
                .replaceAll("[^a-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public Match match(ClubRecord club, List<Course> candidates) {
        // Proximity alone decides: a second nearby course (regardless of name — clubs merge,
        // rename, or share a name) means we can't be sure which one is the real match, so it's
        // reported as ambiguous rather than resolved by an exact-but-possibly-coincidental name hit.
        List<Course> near = candidates.stream()
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .filter(c -> distanceKm(club.lat(), club.lon(), c.getLatitude(), c.getLongitude()) <= MAX_KM)
                .toList();

        if (near.size() == 1) return new Match(near.get(0), false);
        if (near.size() > 1) return new Match(null, true);

        return new Match(null, false);
    }
}
