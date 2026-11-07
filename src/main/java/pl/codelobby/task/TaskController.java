package pl.codelobby.task;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.task.dto.CreateTaskRequest;
import pl.codelobby.task.dto.CreateTaskResponse;
import pl.codelobby.task.dto.TaskEditResponse;

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
        Long authorId = getUserId(jwt);
        Long taskId = taskService.createTask(authorId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateTaskResponse(taskId));
    }

    @GetMapping("/{id}/edit")
    public TaskEditResponse  getTaskForEdit(
            @PathVariable("id") Long taskId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = getUserId(jwt);

        return taskService.getTaskForEdit(taskId, userId);
    }

    private ApiException invalidToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN",
                "Invalid access token"
        );
    }

    private Long getUserId(Jwt jwt) {
        if (jwt == null) {
            throw invalidToken();
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw invalidToken();
        }
    }
}
