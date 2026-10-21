
package pl.codelobby.lobby;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LobbyMemberRepository
        extends JpaRepository<LobbyMember, Long> {

    Optional<LobbyMember> findByLobby_IdAndUser_Id(
            Long lobbyId,
            Long userId
    );

    boolean existsByLobby_IdAndUser_Id(
            Long lobbyId,
            Long userId
    );

    List<LobbyMember> findAllByLobby_Id(Long lobbyId);

    List<LobbyMember> findAllByUser_Id(Long userId);
}
