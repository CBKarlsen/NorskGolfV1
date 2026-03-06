package fritids.norskgolf.dto;

public class FriendDto {
    private String id; // UID
    private String displayName;
    private String email;
    private String status;
    private Long friendshipId;
    private int totalCourses;
    private int totalRounds;
    private String avatar;


    public FriendDto(String id, String displayName, String status, Long friendshipId) {
        this(id, displayName, null, status, friendshipId, 0, 0, null);
    }


    public FriendDto(String id, String displayName, String email, String status, Long friendshipId, int totalCourses, int totalRounds, String avatar) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.status = status;
        this.friendshipId = friendshipId;
        this.totalCourses = totalCourses;
        this.totalRounds = totalRounds;
        this.avatar = avatar;
    }

    // --- Getters ---
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }

    public String getStatus() { return status; }
    public Long getFriendshipId() { return friendshipId; }
    public int getTotalCourses() { return totalCourses; }
    public int getTotalRounds() { return totalRounds; }
    public String getAvatar() { return avatar; }
}