package fritids.norskgolf.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;

@Entity
public class Round {
    @Id
    @GeneratedValue
    private Long id;

    private LocalDate date;
    private int score;     // Total strokes
    private int stableford; // Optional: Stableford points

    @ManyToOne
    private User user;

    @ManyToOne
    private Course course; // Where was it played?


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getStableford() {
        return stableford;
    }

    public void setStableford(int stableford) {
        this.stableford = stableford;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }
}