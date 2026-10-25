package pl.codelobby.lobby;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LobbyRepository extends JpaRepository<Lobby, Long> {

    Optional<Lobby> findByJoinCode(String joinCode);

}
