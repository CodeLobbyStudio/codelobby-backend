package pl.codelobby.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByType(
            TaskType type,
            Pageable pageable
    );

    Page<Task> findByTypeAndCatalogNumber(
            TaskType type,
            Integer catalogNumber,
            Pageable pageable
    );

    Page<Task> findByTypeAndTitleContainingIgnoreCase(
            TaskType type,
            String title,
            Pageable pageable
    );

    List<Task> findAllByAuthor_Id(Long authorId);
}

