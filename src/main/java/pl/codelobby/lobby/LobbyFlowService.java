package pl.codelobby.lobby;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.codelobby.auth.User;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.lobby.dto.LobbyDetailsResponse;
import pl.codelobby.lobby.dto.LobbyRunRequest;
import pl.codelobby.lobby.dto.LobbyWorkspaceResponse;
import pl.codelobby.task.JavaPublicTests;
import pl.codelobby.task.ProgrammingLanguage;
import pl.codelobby.task.TaskFileType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class LobbyFlowService {

    private final LobbyRepository lobbyRepository;
    private final LobbyMemberRepository memberRepository;
    private final LobbyJoinCodeNormalizer joinCodeNormalizer;

    public LobbyFlowService(
            LobbyRepository lobbyRepository,
            LobbyMemberRepository memberRepository,
            LobbyJoinCodeNormalizer joinCodeNormalizer
    ) {
        this.lobbyRepository = lobbyRepository;
        this.memberRepository = memberRepository;
        this.joinCodeNormalizer = joinCodeNormalizer;
    }

    @Transactional
    public LobbyDetailsResponse join(User user, String rawCode) {
        String code = joinCodeNormalizer.normalize(rawCode);

        if (!code.matches("[A-Z0-9]{8}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_JOIN_CODE",
                    "Join code must contain eight letters or digits");
        }

        Lobby lobby = lobbyRepository.findForUpdateByJoinCode(code).orElseThrow(this::notFound);
        Instant now = Instant.now();
        var existingMember = memberRepository.findByLobby_IdAndUser_Id(lobby.getId(), user.getId());

        if (existingMember.isPresent()) {
            return toDetails(lobby, existingMember.get().getRole(), now);
        }

        requireStatus(lobby, LobbyStatus.WAITING, now);

        if (memberRepository.countByLobby_Id(lobby.getId()) >= lobby.getMaxParticipants()) {
            throw new ApiException(HttpStatus.CONFLICT, "LOBBY_FULL", "Lobby is full");
        }

        memberRepository.save(new LobbyMember(lobby, user, LobbyRole.PARTICIPANT));
        return toDetails(lobby, LobbyRole.PARTICIPANT, now);
    }

    @Transactional(readOnly = true)
    public LobbyDetailsResponse details(Long lobbyId, Long userId) {
        Lobby lobby = lobbyRepository.findById(lobbyId).orElseThrow(this::notFound);
        LobbyRole role = memberRole(lobby, userId);
        return toDetails(lobby, role, Instant.now());
    }

    @Transactional
    public LobbyDetailsResponse start(Long lobbyId, Long userId) {
        Lobby lobby = lobbyRepository.findForUpdateById(lobbyId).orElseThrow(this::notFound);
        requireHost(lobby, userId);
        Instant now = Instant.now();
        requireStatus(lobby, LobbyStatus.WAITING, now);

        if (lobby.getTasks().isEmpty() || lobby.getAllowedLanguages().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "LOBBY_NOT_CONFIGURED",
                    "Lobby must have tasks and allowed languages before it can start");
        }

        lobby.start(now);
        return toDetails(lobby, LobbyRole.HOST, now);
    }

    @Transactional
    public LobbyDetailsResponse finish(Long lobbyId, Long userId) {
        Lobby lobby = lobbyRepository.findForUpdateById(lobbyId).orElseThrow(this::notFound);
        requireHost(lobby, userId);
        Instant now = Instant.now();

        if (lobby.getStatus() != LobbyStatus.FINISHED) {
            if (lobby.getStatus() != LobbyStatus.ACTIVE) {
                throw new ApiException(HttpStatus.CONFLICT, "LOBBY_NOT_ACTIVE",
                        "Only a started lobby can be finished");
            }
            lobby.finish(now);
        }

        return toDetails(lobby, LobbyRole.HOST, now);
    }

    @Transactional(readOnly = true)
    public LobbyWorkspaceResponse workspace(Long lobbyId, Long taskId, Long userId,
                                             ProgrammingLanguage language) {
        LobbyTask task = activeTask(lobbyId, taskId, userId, language);

        List<LobbyWorkspaceResponse.File> files = languageFiles(task, language).stream()
                .filter(file -> file.getType() == TaskFileType.EDITABLE || file.getType() == TaskFileType.READ_ONLY)
                .map(file -> new LobbyWorkspaceResponse.File(file.getPath(), file.getType(), file.getContent()))
                .toList();

        JavaPublicTests tests = language == ProgrammingLanguage.JAVA ? task.getJavaPublicTests() : null;
        List<LobbyWorkspaceResponse.PublicTest> publicTests = tests == null ? List.of()
                : tests.testIds().stream().map(LobbyWorkspaceResponse.PublicTest::new).toList();

        return new LobbyWorkspaceResponse(taskId, task.versionId(language), task.getTitle(),
                task.getDescription(), language, files, publicTests);
    }

    @Transactional(readOnly = true)
    public RunnerClient.Run prepareRun(Long lobbyId, Long taskId, Long userId, LobbyRunRequest request) {
        LobbyTask task = activeTask(lobbyId, taskId, userId, request.language());

        if (request.language() != ProgrammingLanguage.JAVA) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RUN_LANGUAGE_UNSUPPORTED",
                    "Public Run is currently available for Java only");
        }

        String versionId = task.versionId(request.language());
        if (!versionId.equals(request.taskVersionId())) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_VERSION_MISMATCH",
                    "Reload the workspace before running tests");
        }

        JavaPublicTests tests = task.getJavaPublicTests();
        if (tests == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PUBLIC_TESTS_NOT_CONFIGURED",
                    "This task version does not have a public Java test configuration");
        }

        List<RunnerClient.SourceFile> officialFiles = languageFiles(task, request.language()).stream()
                .filter(file -> file.getType() != TaskFileType.HIDDEN_TEST)
                .map(file -> new RunnerClient.SourceFile(file.getPath(), file.getType(), file.getContent()))
                .toList();

        Set<String> editablePaths = new HashSet<>();
        for (var file : officialFiles) {
            if (file.type() == TaskFileType.EDITABLE) {
                editablePaths.add(file.path());
            }
        }

        long submittedBytes = 0;
        for (LobbyRunRequest.File file : request.files()) {
            int size = file.content().getBytes(StandardCharsets.UTF_8).length;
            submittedBytes += size;
            if (!editablePaths.remove(file.path()) || !StandardCharsets.UTF_8.newEncoder().canEncode(file.content())
                    || size > 131072 || submittedBytes > 1048576) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RUN_FILES",
                        "Each submitted file must be a unique editable path within the size limits");
            }
        }

        return new RunnerClient.Run(versionId, officialFiles, request.files(), tests);
    }

    private LobbyTask activeTask(Long lobbyId, Long taskId, Long userId, ProgrammingLanguage language) {
        Lobby lobby = lobbyRepository.findById(lobbyId).orElseThrow(this::notFound);
        memberRole(lobby, userId);
        requireStatus(lobby, LobbyStatus.ACTIVE, Instant.now());

        if (!lobby.getAllowedLanguages().contains(language)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LANGUAGE_NOT_ALLOWED", "Language is not allowed in this lobby");
        }

        return lobby.getTasks().stream().filter(task -> taskId.equals(task.getTaskId())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOBBY_TASK_NOT_FOUND",
                        "Task was not found in this lobby"));
    }

    private List<LobbyTaskFile> languageFiles(LobbyTask task, ProgrammingLanguage language) {
        return task.getFiles().stream()
                .filter(file -> file.getLanguage() == language)
                .sorted(Comparator.comparing(LobbyTaskFile::getPath))
                .toList();
    }

    private LobbyRole memberRole(Lobby lobby, Long userId) {
        return memberRepository.findByLobby_IdAndUser_Id(lobby.getId(), userId)
                .map(LobbyMember::getRole)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "LOBBY_ACCESS_DENIED",
                        "Only lobby members can access this lobby"));
    }

    private void requireHost(Lobby lobby, Long userId) {
        if (memberRole(lobby, userId) != LobbyRole.HOST) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LOBBY_HOST_REQUIRED",
                    "Only the lobby host can manage the session");
        }
    }

    private void requireStatus(Lobby lobby, LobbyStatus required, Instant now) {
        if (lobby.effectiveStatus(now) != required) {
            throw new ApiException(HttpStatus.CONFLICT, "LOBBY_NOT_" + required.name(),
                    "Lobby must be " + required.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private LobbyDetailsResponse toDetails(Lobby lobby, LobbyRole role, Instant now) {
        List<LobbyDetailsResponse.Member> members = memberRepository.findAllByLobby_Id(lobby.getId()).stream()
                .sorted(Comparator.comparing(LobbyMember::getId))
                .map(member -> new LobbyDetailsResponse.Member(member.getUser().getId(), member.getRole()))
                .toList();

        List<LobbyDetailsResponse.Task> tasks = lobby.getTasks().stream()
                .map(task -> new LobbyDetailsResponse.Task(task.getTaskId(), task.getTitle(), task.getPosition()))
                .toList();

        return new LobbyDetailsResponse(lobby.getId(), lobby.getName(), lobby.getJoinCode(), lobby.getJoinUrl(),
                lobby.effectiveStatus(now), role, lobby.getHost().getId(), lobby.getMaxParticipants(),
                lobby.getDurationMinutes(), lobby.getStartedAt(), lobby.getEndsAt(), lobby.getAllowedLanguages(), members, tasks);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "LOBBY_NOT_FOUND", "Lobby not found");
    }
}
