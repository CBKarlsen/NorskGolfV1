package fritids.norskgolf.dto;

public class RoundRequest {
    // Keep fields private for safety
    private String courseExternalId;
    private Long courseId;
    private int score;
    private String date;



    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getCourseExternalId() { return courseExternalId; }
    public void setCourseExternalId(String courseExternalId) { this.courseExternalId = courseExternalId; }
}