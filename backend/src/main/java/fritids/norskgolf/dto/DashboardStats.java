package fritids.norskgolf.dto;

import java.util.Map;

public class DashboardStats {
    private long totalPlayed;
    private long totalCourses;
    private double percentageComplete;

    // Key = County Name (e.g., "Vestland"), Value = Stats object
    private Map<String, RegionStat> regionStats;

    // Inner class for the specific region details
    public static class RegionStat {
        public long played;
        public long total;
        public double percentage;

        public RegionStat(long played, long total) {
            this.played = played;
            this.total = total;
            this.percentage = total > 0 ? (double) played / total * 100 : 0;
        }
    }

    public long getTotalPlayed() {
        return totalPlayed;
    }

    public void setTotalPlayed(long totalPlayed) {
        this.totalPlayed = totalPlayed;
    }

    public long getTotalCourses() {
        return totalCourses;
    }

    public void setTotalCourses(long totalCourses) {
        this.totalCourses = totalCourses;
    }

    public double getPercentageComplete() {
        return percentageComplete;
    }

    public void setPercentageComplete(double percentageComplete) {
        this.percentageComplete = percentageComplete;
    }

    public Map<String, RegionStat> getRegionStats() {
        return regionStats;
    }

    public void setRegionStats(Map<String, RegionStat> regionStats) {
        this.regionStats = regionStats;
    }
}