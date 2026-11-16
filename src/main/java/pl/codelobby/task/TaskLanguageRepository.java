package pl.codelobby.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskLanguageRepository
extends JpaRepository<TaskLanguage, Long> {

    Optional<TaskLanguage> findByTask_IdAndLanguage(
            Long taskId,
            ProgrammingLanguage language
    );

    List<TaskLanguage> findAllByTask_Id(Long taskId);
}
