package fritids.norskgolf.service.clubs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        ClubRecord aalesund = clubs.get(1);
        assertEquals("aalesund-gk", aalesund.clubId());
        assertEquals("Ålesund Golfklubb", aalesund.name());
        assertEquals("Møre og Romsdal", aalesund.county());
        assertEquals(18, aalesund.holes());
    }

    @Test
    void rejectsAMissingFile() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> loader.load("no-such-file.json"));
        assertTrue(e.getMessage().contains("could not read club list"));
    }

    @Test
    void rejectsADuplicateClubIdAndNamesIt() {
        // A copy-pasted slug either collides on the unique externalId or silently rewrites
        // another club's row. Either way the message must name the offending slug — and must
        // not be rewrapped as an unreadable-file error.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> loader.load("clubs-duplicate-fixture.json"));
        assertTrue(e.getMessage().contains("duplicate clubId"), e.getMessage());
        assertTrue(e.getMessage().contains("miklagard-gk"), e.getMessage());
        assertFalse(e.getMessage().contains("could not read club list"), e.getMessage());
    }

    @Test
    void rejectsABlankClubId() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> loader.load("clubs-blank-id-fixture.json"));
        assertTrue(e.getMessage().contains("missing clubId or name"), e.getMessage());
        assertFalse(e.getMessage().contains("could not read club list"), e.getMessage());
    }
}
