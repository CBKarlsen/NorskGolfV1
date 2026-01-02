package fritids.norskgolf.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.repository.CourseRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
        // Simple check to prevent re-fetching on every restart if data exists
        if (courseRepository.count() > 0) {
            return;
        }

        System.out.println("Fetching all golf courses in Norway from OSM...");

        // Query: Get Norway area -> Find Nodes/Ways/Relations with golf_course -> Output centers
        String overpassUrl = "https://overpass-api.de/api/interpreter?data=" +
                "[out:json];area[\"ISO3166-1\"=\"NO\"]->.searchArea;" +
                "nwr[\"leisure\"=\"golf_course\"](area.searchArea);" +
                "out center;";

        try {
            String response = restClient.get()
                    .uri(overpassUrl)
                    .retrieve()
                    .body(String.class);

            parseAndSaveCourses(response);
            System.out.println("Golf course sync completed!");

        } catch (Exception e) {
            System.err.println("Failed to sync courses: " + e.getMessage());
        }
    }

    private void parseAndSaveCourses(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode elements = root.path("elements");

            for (JsonNode node : elements) {
                if (node.has("tags") && node.path("tags").has("name")) {

                    String name = node.path("tags").get("name").asText();
                    String externalId = String.valueOf(node.get("id").asLong());

                    // Try to get county from OSM tag first
                    String county = "";
                    if (node.path("tags").has("addr:county")) {
                        county = node.path("tags").get("addr:county").asText();
                    } else if (node.path("tags").has("addr:province")) { // Sometimes used in OSM
                        county = node.path("tags").get("addr:province").asText();
                    }

                    // Handle Coordinates
                    double lat = 0;
                    double lon = 0;
                    if (node.has("center")) {
                        lat = node.get("center").get("lat").asDouble();
                        lon = node.get("center").get("lon").asDouble();
                    } else if (node.has("lat")) {
                        lat = node.get("lat").asDouble();
                        lon = node.get("lon").asDouble();
                    } else {
                        continue;
                    }

                    // Fallback: Estimate county if OSM didn't provide it
                    if (county.isEmpty()) {
                        county = estimateCounty(lat, lon);
                    }

                    // Save to DB
                    // Check if exists first to update county if you re-run
                    Course course = courseRepository.findByExternalId(externalId).orElse(new Course());
                    course.setExternalId(externalId);
                    course.setName(name);
                    course.setLatitude(lat);
                    course.setLongitude(lon);
                    course.setCounty(county); // <--- Set the new field

                    courseRepository.save(course);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // A simple helper to map coordinates to major Norwegian regions
// This is "rough" but works for 95% of cases without complex geometry libraries
    private String estimateCounty(double lat, double lon) {
        if (lon > 10.0 && lat > 68.0) return "Troms og Finnmark";
        if (lat > 65.0) return "Nordland";
        if (lat > 62.5 && lon > 9.0) return "Trøndelag";
        if (lat < 62.5 && lat > 59.5 && lon < 8.0) return "Vestland";
        if (lat < 59.5 && lon < 7.5) return "Rogaland";
        if (lat < 59.0 && lon > 7.5) return "Agder";
        if (lat > 59.0 && lat < 60.5 && lon > 10.0) return "Viken"; //
        if (lat > 60.5 && lat < 62.5 && lon > 8.0 && lon < 12.0) return "Innlandet";
        if (lat > 59.0 && lon > 9.0 && lon < 10.5) return "Vestfold og Telemark";


        return "Unknown"; // Default
    }
}