package pl.codelobby.task;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.task.dto.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskCatalogService taskCatalogService;

    public TaskController(TaskService taskService, TaskCatalogService taskCatalogService) {
        this.taskService = taskService;
        this.taskCatalogService = taskCatalogService;
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

    @GetMapping
    public PagedModel<TaskSummaryResponse> searchTasks(
            @Valid @ModelAttribute TaskCatalogRequest request
    ) {
        var result = taskCatalogService.search(
                request.query(),
                request.difficulty(),
                request.tag(),
                toPageable(request.page(), request.size())
        );

        return new PagedModel<>(result);
    }

    @GetMapping("/mine")
    public PagedModel<TaskSummaryResponse> getMyTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute TaskPageRequest request
    ) {
        Long authorId = getUserId(jwt);

        var result = taskCatalogService.findMine(
                authorId,
                toPageable(request.page(), request.size())
        );

        return new PagedModel<>(result);
    }

    @GetMapping("/{id}")
    public TaskDetailsResponse getTaskDetails(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") String rawTaskId
    ) {
        Long userId = getUserId(jwt);

        return taskCatalogService.findDetails(rawTaskId, userId);


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

    private Pageable toPageable(Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;

        if ((long) pageNumber * pageSize > Integer.MAX_VALUE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Requested page is too large"
            );
        }

        return PageRequest.of(pageNumber, pageSize);
    }
}
