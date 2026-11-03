package pl.codelobby.task;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.codelobby.auth.User;
import pl.codelobby.auth.UserRepository;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.task.dto.CreateTaskRequest;
import pl.codelobby.task.dto.TaskFileRequest;
import pl.codelobby.task.dto.TaskLanguageRequest;
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
}
