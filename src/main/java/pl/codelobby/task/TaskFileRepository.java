package pl.codelobby.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskFileRepository
        extends JpaRepository<TaskFile, Long> {

    List<TaskFile> findAllByTaskLanguage_Id(Long taskLanguageId);
}
