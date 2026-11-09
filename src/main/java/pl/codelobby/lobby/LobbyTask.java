package pl.codelobby.lobby;

import jakarta.persistence.*;
import pl.codelobby.task.Task;

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

    protected LobbyTask() {}

    public LobbyTask(Lobby lobby, Task task, int position) {
        this.lobby = lobby;
        this.task = task;
        this.position = position;
    }
}
