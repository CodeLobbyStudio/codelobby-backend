package pl.codelobby.lobby;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.task.*;
import pl.codelobby.task.validation.TaskFileValidator;

import java.util.*;

@Service
public class LobbyTaskSelectionService {

    private final TaskFileValidator taskFileValidator;

    private final TaskRepository taskRepository;

    public LobbyTaskSelectionService(TaskFileValidator taskFileValidator, TaskRepository taskRepository) {
        this.taskFileValidator = taskFileValidator;
        this.taskRepository = taskRepository;
    }

    public void validateLanguages(
            List<Task> tasks,
            Set<ProgrammingLanguage> allowedLanguages
    ) {
        for (Task task : tasks) {
            List<TaskLanguage> taskLanguages = task.getLanguages();

            for (ProgrammingLanguage language : allowedLanguages) {
                TaskLanguage taskLanguage = taskLanguages.stream()
                        .filter(configured -> configured.getLanguage() == language)
                        .findFirst()
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.BAD_REQUEST,
                                "TASK_LANGUAGE_UNAVAILABLE",
                                "Task " + task.getId() + " is not configured for " + language
                        ));

                try {
                    taskFileValidator.validateFiles(taskLanguage.getFiles());
                } catch (IllegalArgumentException exception) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_TASK_FILES",
                            "Task " + task.getId() + " has invalid files for " + language
                                    + ". " + exception.getMessage()
                    );
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Task> selectTasks(
            Long hostId,
            List<Long> taskIds,
            Set<ProgrammingLanguage> allowedLanguages
    ) {
        if (new HashSet<>(taskIds).size() != taskIds.size()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_TASK_ID",
                    "Each task can be selected only once"
            );
        }

        Map<Long, Task> tasksById = new HashMap<>();
        for (Task task : taskRepository.findAllById(taskIds)) {
            tasksById.put(task.getId(), task);
        }

        List<Task> selectedTasks = new ArrayList<>(taskIds.size());
        for (Long taskId : taskIds) {
            Task task = tasksById.get(taskId);

            if (task == null) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TASK_NOT_FOUND",
                        "Task " + taskId + " was not found"
                );
            }

            if (task.getType() == TaskType.CUSTOM
                    && !task.getAuthor().getId().equals(hostId)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "TASK_ACCESS_DENIED",
                        "Only the author can select custom task " + taskId
                );
            }

            selectedTasks.add(task);
        }

        validateLanguages(selectedTasks, allowedLanguages);
        return selectedTasks;
    }
}