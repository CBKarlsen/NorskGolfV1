package fritids.norskgolf.service.clubs;

import fritids.norskgolf.entities.Course;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClubMatcherTest {

    private final ClubMatcher matcher = new ClubMatcher();

    private static Course course(String name, double lat, double lon) {
        Course c = new Course();
        c.setName(name);
        c.setLatitude(lat);
        c.setLongitude(lon);
        return c;
    }

    private static ClubRecord club(String name, double lat, double lon) {
        return new ClubRecord("slug", name, lat, lon, "Kommune", "Fylke", 18);
    }

    @Test
    void normalisationIgnoresClubWordsAndNorwegianLetters() {
        assertEquals(ClubMatcher.normalise("Ålesund Golfklubb"), ClubMatcher.normalise("Aalesund GK"));
        assertEquals(ClubMatcher.normalise("Bjørnafjorden Golfklubb"), ClubMatcher.normalise("Bjornafjorden golfbane"));
    }

    @Test
    void matchesTheSameClubWithADifferentSuffix() {
        Course existing = course("Miklagard GK", 60.0234, 11.1421);

        ClubMatcher.Match match = matcher.match(club("Miklagard Golfklubb", 60.0240, 11.1430), List.of(existing));

        assertSame(existing, match.course());
        assertFalse(match.ambiguous());
    }

    @Test
    void doesNotMatchASimilarNameFarAway() {
        Course existing = course("Moss Golfklubb", 59.4340, 10.6580);

        // same name, but 200 km away — a different club
        ClubMatcher.Match match = matcher.match(club("Moss Golfklubb", 61.0000, 10.6580), List.of(existing));

        assertNull(match.course());
    }

    @Test
    void reportsAmbiguityInsteadOfGuessing() {
        Course first = course("Moss Golfklubb", 59.4340, 10.6580);
        Course second = course("Moss & Rygge Golfklubb", 59.4350, 10.6590);

        ClubMatcher.Match match = matcher.match(club("Moss Golfklubb", 59.4345, 10.6585), List.of(first, second));

        assertTrue(match.ambiguous());
    }

    @Test
    void distanceIsRoughlyRight() {
        // Oslo to Bergen is about 300 km
        double km = ClubMatcher.distanceKm(59.9139, 10.7522, 60.3913, 5.3221);
        assertTrue(km > 280 && km < 330, "was " + km);
    }
}
