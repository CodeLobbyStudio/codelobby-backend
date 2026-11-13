package pl.codelobby.lobby;

import jakarta.persistence.*;
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
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lobby_id", nullable = false)
    Lobby lobby;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    Task task;

    @Column(nullable = false)
    private int position;

    @OneToMany(mappedBy = "lobbyTask", cascade = CascadeType.PERSIST)
    private List<LobbyTaskFile> files = new ArrayList<>();


    protected LobbyTask() {}

    public LobbyTask(Lobby lobby, Task task, int position, Set<ProgrammingLanguage> allowedLanguages) {
        this.lobby = lobby;
        this.task = task;
        this.position = position;

        for (TaskLanguage taskLanguage : task.getLanguages()) {
            ProgrammingLanguage language = taskLanguage.getLanguage();

            if (!allowedLanguages.contains(language)) {
                continue;
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
