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
    private String displayName;
    private String avatar;
    private String email;
    private int bestScore;

    // --- Inner Class: Round Summary ---
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
        public long playedCount;
        public long totalCount;
        public double percentage;
        public List<CourseDto> courses;

        public RegionStat(long playedCount, long totalCount, List<CourseDto> courses) {
            this.playedCount = playedCount;
            this.totalCount = totalCount;
            this.courses = courses;
            // FIX 1: Changed 'total' to 'totalCount'
            this.percentage = totalCount > 0 ? (double) playedCount / totalCount * 100 : 0;
        }
    }

    // --- Getters & Setters ---
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getBestScore() { return bestScore; }
    public void setBestScore(int bestScore) { this.bestScore = bestScore; }

    // FIX 2: Changed 'totalCount' to 'totalPlayed' to match the field name
    public long getTotalPlayed() { return totalPlayed; }
    public void setTotalPlayed(long totalPlayed) { this.totalPlayed = totalPlayed; }

    public long getTotalCourses() { return totalCourses; }
    public void setTotalCourses(long totalCourses) { this.totalCourses = totalCourses; }

    public double getPercentageComplete() { return percentageComplete; }
    public void setPercentageComplete(double percentageComplete) { this.percentageComplete = percentageComplete; }

    public Map<String, RegionStat> getRegionStats() { return regionStats; }
    public void setRegionStats(Map<String, RegionStat> regionStats) { this.regionStats = regionStats; }

    public List<RoundSummary> getRecentRounds() { return recentRounds; }
    public void setRecentRounds(List<RoundSummary> recentRounds) { this.recentRounds = recentRounds; }
}