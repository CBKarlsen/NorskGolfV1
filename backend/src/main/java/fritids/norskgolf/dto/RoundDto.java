package fritids.norskgolf.dto;

public class RoundDto {
    private Long id;
    private Long courseId;
    private String courseName;
    private String date;
    private int score;

    public RoundDto(Long id, Long courseId, String courseName, String date, int score) {
        this.id = id;
        this.courseId = courseId;
        this.courseName = courseName;
        this.date = date;
        this.score = score;
    }

    // Getters and Setters (needed for JSON serialization)
    public Long getId() { return id; }
    public Long getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getDate() { return date; }
    public int getScore() { return score; }
}