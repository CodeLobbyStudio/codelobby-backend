package pl.codelobby.task;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import pl.codelobby.auth.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_number", unique = true)
    private Integer catalogNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskDifficulty difficulty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "task_tags",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(
            mappedBy = "task",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<TaskLanguage> languages = new ArrayList<>();

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    protected Task() {
    }

    public Task(
            Integer catalogNumber,
            String title,
            String description,
            TaskType type,
            TaskDifficulty difficulty,
            User author
    ) {
        this.catalogNumber = catalogNumber;
        this.title = title;
        this.description = description;
        this.type = type;
        this.difficulty = difficulty;
        this.author = author;
    }

    public void addTag(Tag tag) {
        tags.add(tag);
    }

    public TaskLanguage addLanguage(
            ProgrammingLanguage language
    ) {
        TaskLanguage taskLanguage = new TaskLanguage(
                this,
                language
        );

        languages.add(taskLanguage);

        return taskLanguage;
    }

    public List<TaskLanguage> getLanguages() {
        return List.copyOf(languages);
    }

    public Long getId() {
        return id;
    }

    public Integer getCatalogNumber() {
        return catalogNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskType getType() {
        return type;
    }

    public TaskDifficulty getDifficulty() {
        return difficulty;
    }

    public User getAuthor() {
        return author;
    }

    public Set<Tag> getTags() {
        return Set.copyOf(tags);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
