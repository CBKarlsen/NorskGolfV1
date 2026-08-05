package fritids.norskgolf.service.clubs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClubListLoaderTest {

    private final ClubListLoader loader = new ClubListLoader();

    @Test
    void readsEveryFieldOfEveryEntry() {
        List<ClubRecord> clubs = loader.load("clubs-fixture.json");

        assertEquals(2, clubs.size());
        ClubRecord miklagard = clubs.get(0);
        assertEquals("miklagard-gk", miklagard.clubId());
        assertEquals("Miklagard Golfklubb", miklagard.name());
        assertEquals("Ullensaker", miklagard.municipality());
        assertEquals("Akershus", miklagard.county());
        assertEquals(18, miklagard.holes());
        assertEquals(60.0234, miklagard.lat(), 0.0001);
    }

    @Test
    void rejectsAMissingFile() {
        assertThrows(IllegalStateException.class, () -> loader.load("no-such-file.json"));
    }
}
