package fritids.norskgolf.service.clubs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class ClubListLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<ClubRecord> load(String classpathResource) {
        try (InputStream in = new ClassPathResource(classpathResource).getInputStream()) {
            List<ClubRecord> clubs = mapper.readValue(in, mapper.getTypeFactory()
                    .constructCollectionType(List.class, ClubRecord.class));
            for (ClubRecord club : clubs) {
                if (club.clubId() == null || club.clubId().isBlank()
                        || club.name() == null || club.name().isBlank()) {
                    throw new IllegalStateException("club entry missing clubId or name: " + club);
                }
            }
            return clubs;
        } catch (Exception e) {
            throw new IllegalStateException("could not read club list " + classpathResource, e);
        }
    }
}
