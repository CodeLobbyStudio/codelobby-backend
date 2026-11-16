package pl.codelobby.lobby;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface LobbyRepository extends JpaRepository<Lobby, Long> {

    Optional<Lobby> findByJoinCode(String joinCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Lobby> findForUpdateByJoinCode(String joinCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Lobby> findForUpdateById(Long id);

}
