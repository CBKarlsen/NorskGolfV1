package fritids.norskgolf.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.repository.CourseRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Service
public class CourseSyncService {

    private final CourseRepository courseRepository;
    private final RestClient restClient;

    public CourseSyncService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
        this.restClient = RestClient.create();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncCourses() {
        if (courseRepository.count() > 0) {
            System.out.println("✅ Courses already in database. Skipping sync.");
            return;
        }

        System.out.println("🌍 Database empty. Starting sync...");

        // STRATEGY 1: Local File (Fast & Reliable)
        try {
            ClassPathResource resource = new ClassPathResource("golf_courses.json");
            if (resource.exists()) {
                System.out.println("📂 Found 'golf_courses.json' locally. Importing...");
                String json = FileCopyUtils.copyToString(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
                parseAndSaveCourses(json);
                System.out.println("✅ Imported from local file!");
                return; // Stop here, success!
            }
        } catch (Exception e) {
            System.err.println("⚠️ Could not read local file: " + e.getMessage());
        }

        // STRATEGY 2: Internet (Backup / Slow)
        System.out.println("☁️ Local file missing. Calling Overpass API (This might fail)...");
        String overpassUrl = "https://overpass-api.de/api/interpreter?data=" +
                "[out:json][timeout:90];" +
                "area[\"ISO3166-1\"=\"NO\"]->.searchArea;" +
                "nwr[\"leisure\"=\"golf_course\"](area.searchArea);" +
                "out center;";

        try {
            String response = restClient.get()
                    .uri(overpassUrl)
                    .retrieve()
                    .body(String.class);

            parseAndSaveCourses(response);
            System.out.println("✅ Imported from Internet!");

        } catch (Exception e) {
            System.err.println("❌ Internet sync failed: " + e.getMessage());
        }
    }

    private void parseAndSaveCourses(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode elements = root.path("elements");
            Set<String> processedNames = new HashSet<>();

            for (JsonNode node : elements) {
                if (node.has("tags") && node.path("tags").has("name")) {
                    String name = node.path("tags").get("name").asText().trim();

                    if (processedNames.contains(name)) continue; // Skip duplicates
                    processedNames.add(name);

                    String externalId = String.valueOf(node.get("id").asLong());

                    // Coordinates logic
                    double lat = 0, lon = 0;
                    if (node.has("center")) {
                        lat = node.get("center").get("lat").asDouble();
                        lon = node.get("center").get("lon").asDouble();
                    } else if (node.has("lat")) {
                        lat = node.get("lat").asDouble();
                        lon = node.get("lon").asDouble();
                    } else {
                        continue;
                    }

                    // County Logic
                    String county = "";
                    if (node.path("tags").has("addr:county")) {
                        county = node.path("tags").get("addr:county").asText();
                    }
                    if (county.isEmpty()) {
                        county = estimateCounty(lat, lon);
                    }

                    Course course = new Course();
                    course.setExternalId(externalId);
                    course.setName(name);
                    course.setLatitude(lat);
                    course.setLongitude(lon);
                    course.setCounty(county);

                    courseRepository.save(course);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String estimateCounty(double lat, double lon) {
        if (lon > 10.0 && lat > 68.0) return "Troms og Finnmark";
        if (lat > 65.0) return "Nordland";
        if (lat > 62.5 && lon > 9.0) return "Trøndelag";
        if (lat < 62.5 && lat > 59.5 && lon < 8.0) return "Vestland";
        if (lat < 59.5 && lon < 7.5) return "Rogaland";
        if (lat < 59.0 && lon > 7.5) return "Agder";
        if (lat > 59.0 && lat < 60.5 && lon > 10.0) return "Viken";
        if (lat > 60.5 && lat < 62.5 && lon > 8.0 && lon < 12.0) return "Innlandet";
        if (lat > 59.0 && lon > 9.0 && lon < 10.5) return "Vestfold og Telemark";
        return "Andre fylker";
    }
}