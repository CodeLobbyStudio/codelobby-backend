package pl.codelobby.lobby;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import pl.codelobby.auth.User;
import pl.codelobby.task.ProgrammingLanguage;
import pl.codelobby.task.Task;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "lobbies")
public class Lobby {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "join_code", nullable = false, unique = true, length = 8)
    private String joinCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LobbyStatus status = LobbyStatus.WAITING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @ElementCollection
    @CollectionTable(name = "lobby_languages", joinColumns = @JoinColumn(name = "lobby_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 20)
    private Set<ProgrammingLanguage> allowedLanguages = new HashSet<>();

    static final int DEFAULT_MAX_PARTICIPANTS = 10;
    static final int DEFAULT_DURATION_MINUTES = 30;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @OneToMany(mappedBy = "lobby", cascade = CascadeType.PERSIST)
    @OrderBy("position ASC")
    private List<LobbyTask> taskList = new ArrayList<>();

    protected Lobby() {
    }

    public Lobby(String name, String joinCode, User host) {
        this(name, joinCode, host, Set.of());
    }

    public Lobby(String name, String joinCode, User host,
                 Set<ProgrammingLanguage> allowedLanguages) {
        this(name, joinCode, host, allowedLanguages,
                DEFAULT_MAX_PARTICIPANTS, DEFAULT_DURATION_MINUTES);
    }

    public Lobby(String name, String joinCode, User host,
                 Set<ProgrammingLanguage> allowedLanguages,
                 int maxParticipants, int durationMinutes) {
        this.name = name;
        this.joinCode = joinCode;
        this.host = host;
        this.allowedLanguages.addAll(allowedLanguages);
        this.maxParticipants = maxParticipants;
        this.durationMinutes = durationMinutes;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getJoinCode() {
        return joinCode;
    }

    String getJoinUrl() {
        return "/lobbies/join?code=" + joinCode;
    }

    public LobbyStatus getStatus() {
        return status;
    }

    LobbyStatus effectiveStatus(Instant now) {
        if (status == LobbyStatus.ACTIVE && endsAt != null && !now.isBefore(endsAt)) {
            return LobbyStatus.FINISHED;
        }
        return status;
    }

    void start(Instant now) {
        status = LobbyStatus.ACTIVE;
        startedAt = now;
        endsAt = now.plus(durationMinutes, ChronoUnit.MINUTES);
    }

    void finish(Instant now) {
        status = LobbyStatus.FINISHED;
        if (now.isBefore(endsAt)) {
            endsAt = now;
        }
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getEndsAt() {
        return endsAt;
    }

    Set<ProgrammingLanguage> getAllowedLanguages() {
        return Set.copyOf(allowedLanguages);
    }

    List<LobbyTask> getTasks() {
        return List.copyOf(taskList);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public User getHost() {
        return host;
    }

    public void addTask(Task task, int position) {
        LobbyTask lobbyTask = new LobbyTask(this, task, position, allowedLanguages);
        taskList.add(lobbyTask);
    }
}
