package fritids.norskgolf.dto;

import fritids.norskgolf.controller.GolfApiController.CourseDto;
import java.util.List;
import java.util.Map;

public class DashboardStats {
    private long totalPlayed;
    private long totalCourses;
    private double percentageComplete;

    // 1. NEW: List of recent rounds at the top level
    private List<RoundSummary> recentRounds;

    private Map<String, RegionStat> regionStats;

    // --- Inner Class: Round Summary (Moved UP) ---
    public static class RoundSummary {
        public Long id;
        public String courseName;
        public String date;
        public int score;

        public RoundSummary(Long id, String courseName, String date, int score) {
            this.id = id;
            this.courseName = courseName;
            this.date = date;
            this.score = score;
        }
    }

    // --- Inner Class: Region Stat ---
    public static class RegionStat {
        public long played;
        public long total;
        public double percentage;
        public List<CourseDto> courses;

        public RegionStat(long played, long total, List<CourseDto> courses) {
            this.played = played;
            this.total = total;
            this.courses = courses;
            this.percentage = total > 0 ? (double) played / total * 100 : 0;
        }
    }

    // --- Getters & Setters ---
    public long getTotalPlayed() { return totalPlayed; }
    public void setTotalPlayed(long totalPlayed) { this.totalPlayed = totalPlayed; }

    public long getTotalCourses() { return totalCourses; }
    public void setTotalCourses(long totalCourses) { this.totalCourses = totalCourses; }

    public double getPercentageComplete() { return percentageComplete; }
    public void setPercentageComplete(double percentageComplete) { this.percentageComplete = percentageComplete; }

    public Map<String, RegionStat> getRegionStats() { return regionStats; }
    public void setRegionStats(Map<String, RegionStat> regionStats) { this.regionStats = regionStats; }

    // New Getter/Setter
    public List<RoundSummary> getRecentRounds() { return recentRounds; }
    public void setRecentRounds(List<RoundSummary> recentRounds) { this.recentRounds = recentRounds; }
}