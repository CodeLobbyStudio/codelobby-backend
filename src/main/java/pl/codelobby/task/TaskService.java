package pl.codelobby.task;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.task.dto.*;
import pl.codelobby.task.validation.TaskFileValidator;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskFileValidator taskFileValidator;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository, TaskFileValidator taskFileValidator) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.taskFileValidator = taskFileValidator;
    }

    @Transactional
    public Long createTask(Long authorId, CreateTaskRequest request) {

        User author = getAuthor(authorId);

        Task task = buildTask(request, author);

        try {
            taskFileValidator.validateTaskLanguages(task.getLanguages());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TASK_FILES",
                    exception.getMessage()
            );
        }

        Task savedTask = taskRepository.save(task);

        return savedTask.getId();
    }

    @Transactional(readOnly = true)
    public TaskEditResponse getTaskForEdit(Long taskId, Long userId) {
        User currentUser = getAuthor(userId);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TASK_NOT_FOUND",
                        "Task not found"
                ));

        if (!task.getAuthor().getId().equals(currentUser.getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TASK_ACCESS_DENIED",
                    "Only the task author can access this task for editing"
            );
        }

        return new TaskEditResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getDifficulty(),
                task.getLanguages().stream()
                        .map(this::toLanguageResponse)
                        .toList()
        );
    }

    public User getAuthor(Long authorId) {
        return userRepository.findById(authorId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_TOKEN",
                        "Invalid access token"
                ));
    }

    private Task buildTask(CreateTaskRequest request, User author) {

        Task task = new Task(
                null,
                request.title(),
                request.description(),
                TaskType.CUSTOM,
                request.difficulty(),
                author
        );

        for (TaskLanguageRequest languageRequest : request.languages()) {

            TaskLanguage taskLanguage = task.addLanguage(
                    languageRequest.language()
            );

            for (TaskFileRequest fileRequest : languageRequest.files()) {

                taskLanguage.addFile(
                        fileRequest.path(),
                        fileRequest.type(),
                        fileRequest.content()
                );
            }
        }

        return task;
    }

    private TaskLanguageResponse toLanguageResponse(TaskLanguage taskLanguage) {
        return new TaskLanguageResponse(
                taskLanguage.getLanguage(),
                taskLanguage.getFiles().stream()
                        .map(this::toFileResponse)
                        .toList()
        );
    }

    private TaskFileResponse toFileResponse(TaskFile taskFile) {
        return new TaskFileResponse(
                taskFile.getPath(),
                taskFile.getType(),
                taskFile.getContent()
        );
    }
}
