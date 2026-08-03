package fritids.norskgolf.dto;

public class UserProfileDTO {
    private String name;
    private String email;
    private String photo;
    private String userId;
    private String username;
    private String csrfToken;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoto() {
        return photo;
    }
    public void setPhoto(String photo) {
        this.photo = photo;
    }


    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getCsrfToken() { return csrfToken; }
    public void setCsrfToken(String csrfToken) { this.csrfToken = csrfToken; }
}
