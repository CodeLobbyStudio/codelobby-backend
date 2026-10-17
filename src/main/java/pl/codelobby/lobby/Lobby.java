package pl.codelobby.lobby;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import pl.codelobby.auth.User;

import java.time.Instant;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    protected Lobby() {
    }

    public Lobby(String name, String joinCode, User host) {
        this.name = name;
        this.joinCode = joinCode;
        this.host = host;
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

    public LobbyStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public User getHost() {
        return host;
    }
}
