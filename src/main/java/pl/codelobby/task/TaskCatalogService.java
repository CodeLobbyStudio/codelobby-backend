package pl.codelobby.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class TaskCatalogService {

    private final TaskRepository taskRepository;

    public TaskCatalogService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Page<Task> search(String query, Pageable pageable) {

        if (query == null || query.isBlank()) {
            return taskRepository.findByType(
                    TaskType.OFFICIAL,
                    pageable
            );
        }

        String normalized = query.trim();

        if (normalized.matches("[0-9]+")) {
            try {
                int number = Integer.parseInt(normalized);

                return taskRepository.findByTypeAndCatalogNumber(
                        TaskType.OFFICIAL,
                        number,
                        pageable
                );
            } catch (NumberFormatException exception) {
                return Page.empty(pageable);
            }
        }

        return taskRepository
                .findByTypeAndTitleContainingIgnoreCase(
                        TaskType.OFFICIAL,
                        normalized,
                        pageable
                );
    }
}
