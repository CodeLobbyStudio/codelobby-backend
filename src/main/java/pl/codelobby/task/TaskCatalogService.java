package pl.codelobby.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.codelobby.common.error.ApiException;
import pl.codelobby.task.dto.TagResponse;
import pl.codelobby.task.dto.TaskDetailsResponse;
import pl.codelobby.task.dto.TaskSummaryResponse;

import java.util.Comparator;

@Service
@Transactional(readOnly = true)
public class TaskCatalogService {

    private final TaskRepository taskRepository;

    public TaskCatalogService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Page<TaskSummaryResponse> search(
            String query,
            Pageable pageable
    ) {
        return search(query, null, null, pageable);
    }

    public Page<TaskSummaryResponse> search(
            String query,
            TaskDifficulty difficulty,
            String tag,
            Pageable pageable
    ) {
        var specification = TaskSpecifications.officialCatalog(
                query,
                difficulty,
                tag
        );

        Pageable pagination;

        if (pageable.isPaged()) {
            pagination = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize()
            );
        } else {
            pagination = Pageable.unpaged();
        }

        return taskRepository.findAll(specification, pagination)
                .map(this::toSummary);
    }

    private TaskSummaryResponse toSummary(Task task) {
        var tags = task.getTags().stream()
                .sorted(Comparator.comparing(Tag::getSlug))
                .map(tag -> new TagResponse(
                        tag.getName(),
                        tag.getSlug()
                ))
                .toList();

        var languages = task.getLanguages().stream()
                .map(TaskLanguage::getLanguage)
                .sorted()
                .toList();

        return new TaskSummaryResponse(
                task.getId(),
                task.getCatalogNumber(),
                task.getTitle(),
                task.getDifficulty(),
                tags,
                languages
        );
    }

    public TaskDetailsResponse findDetails(
            String rawTaskId,
            Long userId
    ) {
        long taskId;

        try {
            taskId = Long.parseLong(rawTaskId);
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Invalid task id"
            );
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "TASK_NOT_FOUND",
                        "Task not found"
                ));

        if (task.getType() == TaskType.CUSTOM
                && !task.getAuthor().getId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TASK_ACCESS_DENIED",
                    "Only the task author can access this task"
            );
        }

        TaskSummaryResponse summary = toSummary(task);

        return new TaskDetailsResponse(
                summary.id(),
                summary.catalogNumber(),
                summary.title(),
                task.getDescription(),
                task.getDifficulty(),
                summary.tags(),
                summary.languages()
        );

    }

    public Page<TaskSummaryResponse> findMine(
            Long authorId,
            Pageable pageable
    ) {
        Sort sort = Sort.by(
                Sort.Direction.DESC,
                "createdAt",
                "id"
        );

        Pageable pagination = pageable.isPaged()
                ? PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        sort
                )
                : Pageable.unpaged(sort);

        return taskRepository.findByTypeAndAuthor_Id(
                        TaskType.CUSTOM,
                        authorId,
                        pagination
                )
                .map(this::toSummary);
    }
}