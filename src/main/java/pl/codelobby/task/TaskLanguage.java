
package pl.codelobby.task;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "task_languages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_languages_task_language",
                columnNames = {"task_id", "language"}
        )
)
public class TaskLanguage {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "task_id", nullable = false)
        private Task task;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private ProgrammingLanguage language;

        @OneToMany(
                mappedBy = "taskLanguage",
                cascade = CascadeType.ALL,
                orphanRemoval = true
        )
        private List<TaskFile> files = new ArrayList<>();

        protected TaskLanguage() {
        }

        public TaskLanguage(Task task, ProgrammingLanguage language) {
                this.task = task;
                this.language = language;
        }

        public TaskFile addFile(
                String path,
                TaskFileType type,
                String content
        ) {
                TaskFile file = new TaskFile(
                        this,
                        path,
                        type,
                        content
                );

                files.add(file);

                return file;
        }

        public Long getId() {
                return id;
        }

        public Task getTask() {
                return task;
        }

        public ProgrammingLanguage getLanguage() {
                return language;
        }

        public List<TaskFile> getFiles() {
                return List.copyOf(files);
        }
}
