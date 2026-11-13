package pl.codelobby.lobby;

import jakarta.persistence.*;
import pl.codelobby.task.ProgrammingLanguage;
import pl.codelobby.task.TaskFileType;

@Entity
@Table(name = "lobby_task_files")
public class LobbyTaskFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lobby_task_id", nullable = false)
    private LobbyTask lobbyTask;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgrammingLanguage language;

    @Column(nullable = false, length = 500)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private TaskFileType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    protected LobbyTaskFile() {}

    public LobbyTaskFile(
            LobbyTask lobbyTask,
            ProgrammingLanguage language,
            String path,
            TaskFileType type,
            String content
    ) {
        this.lobbyTask = lobbyTask;
        this.language = language;
        this.path = path;
        this.type = type;
        this.content = content;
    }


}
