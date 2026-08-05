package fritids.norskgolf.service.clubs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ClubListLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<ClubRecord> load(String classpathResource) {
        List<ClubRecord> clubs;
        try (InputStream in = new ClassPathResource(classpathResource).getInputStream()) {
            clubs = mapper.readValue(in, mapper.getTypeFactory()
                    .constructCollectionType(List.class, ClubRecord.class));
        } catch (Exception e) {
            throw new IllegalStateException("could not read club list " + classpathResource, e);
        }

        // Validation lives outside the try on purpose: inside it, the blanket catch would rewrap
        // "duplicate clubId" as "could not read club list" and hide which of the two went wrong.
        Set<String> seen = new HashSet<>();
        for (ClubRecord club : clubs) {
            if (club.clubId() == null || club.clubId().isBlank()
                    || club.name() == null || club.name().isBlank()) {
                throw new IllegalStateException("club entry missing clubId or name: " + club);
            }
            if (!seen.add(club.clubId())) {
                // A duplicate slug either collides on the unique externalId or silently rewrites
                // another club's row, so it must fail loudly rather than reach the reconciler.
                throw new IllegalStateException("duplicate clubId in club list: " + club.clubId());
            }
        }
        return clubs;
    }
}
