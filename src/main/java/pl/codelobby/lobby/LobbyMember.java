package pl.codelobby.lobby;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import pl.codelobby.auth.User;

import java.time.Instant;

@Entity
@Table(
        name = "lobby_members",
        uniqueConstraints = @UniqueConstraint(
        name = "uk_lobby_members_lobby_user",
        columnNames = {"lobby_id", "user_id"}
        )
)
public class LobbyMember {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "lobby_id", nullable = false)
        private Lobby lobby;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private LobbyRole role;

        @CreationTimestamp
        @Column(name = "joined_at", nullable = false, updatable = false)
        private Instant joinedAt;

        protected LobbyMember() {
        }

        public LobbyMember(Lobby lobby, User user, LobbyRole role) {
                this.lobby = lobby;
                this.user = user;
                this.role = role;
        }

        public Long getId() {
                return id;
        }

        public Lobby getLobby() {
                return lobby;
        }

        public User getUser() {
                return user;
        }

        public LobbyRole getRole() {
                return role;
        }

        public Instant getJoinedAt() {
                return joinedAt;
        }
}
