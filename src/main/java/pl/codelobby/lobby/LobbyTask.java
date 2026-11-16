package pl.codelobby.lobby;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.codelobby.task.JavaPublicTests;
import pl.codelobby.task.ProgrammingLanguage;
import pl.codelobby.task.Task;
import pl.codelobby.task.TaskFile;
import pl.codelobby.task.TaskLanguage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "lobby_tasks")
public class LobbyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lobby_id", nullable = false)
    private Lobby lobby;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false)
    private int position;

    @OneToMany(mappedBy = "lobbyTask", cascade = CascadeType.PERSIST)
    private List<LobbyTaskFile> files = new ArrayList<>();

    @Column(name = "description_snapshot", nullable = false, columnDefinition = "TEXT")
    private String descriptionSnapshot;

    @Column(name = "title_snapshot", nullable = false, length = 200)
    private String titleSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "java_public_tests", columnDefinition = "jsonb")
    private JavaPublicTests javaPublicTests;

    protected LobbyTask() {
    }

    Long getTaskId() {
        return task.getId();
    }

    String getTitle() {
        return titleSnapshot;
    }

    String getDescription() {
        return descriptionSnapshot;
    }

    int getPosition() {
        return position;
    }

    String versionId(ProgrammingLanguage language) {
        return "lobby-task-" + id + "-" + language.name();
    }

    JavaPublicTests getJavaPublicTests() {
        return javaPublicTests;
    }

    List<LobbyTaskFile> getFiles() {
        return List.copyOf(files);
    }

    public LobbyTask(Lobby lobby, Task task, int position, Set<ProgrammingLanguage> allowedLanguages) {
        this.lobby = lobby;
        this.task = task;
        this.position = position;
        this.descriptionSnapshot = task.getDescription();
        this.titleSnapshot = task.getTitle();

        for (TaskLanguage taskLanguage : task.getLanguages()) {
            ProgrammingLanguage language = taskLanguage.getLanguage();

            if (!allowedLanguages.contains(language)) {
                continue;
            }

            if (language == ProgrammingLanguage.JAVA) {
                javaPublicTests = taskLanguage.getJavaPublicTests();
            }

            for (TaskFile file : taskLanguage.getFiles()) {
                files.add(new LobbyTaskFile(
                        this,
                        language,
                        file.getPath(),
                        file.getType(),
                        file.getContent()
                ));
            }
        }
    }
}
