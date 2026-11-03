package pl.codelobby.task;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.task.dto.CreateTaskRequest;
import pl.codelobby.task.dto.CreateTaskResponse;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<CreateTaskResponse> createTask(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        if (jwt == null) {
            throw invalidToken();
        }

        Long authorId;

        try {
            authorId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw invalidToken();
        }

        Long taskId = taskService.createTask(authorId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new CreateTaskResponse(taskId));
    }

    private ApiException invalidToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN",
                "Invalid access token"
        );
    }
}
