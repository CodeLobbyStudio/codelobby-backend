package pl.codelobby.task;

import jakarta.persistence.*;

@Entity
@Table(name = "task_files",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_files_language_path",
                columnNames = {"task_language_id", "path"}
        )
)
public class TaskFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_language_id", nullable = false)
    private TaskLanguage taskLanguage;

    @Column(nullable = false, length = 500)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private TaskFileType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    protected TaskFile() {
    }

    public TaskFile(
            TaskLanguage taskLanguage,
            String path,
            TaskFileType type,
            String content
    ) {
        this.taskLanguage = taskLanguage;
        this.path = path;
        this.type = type;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public TaskLanguage getTaskLanguage() {
        return taskLanguage;
    }

    public String getPath() {
        return path;
    }

    public TaskFileType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }
}
