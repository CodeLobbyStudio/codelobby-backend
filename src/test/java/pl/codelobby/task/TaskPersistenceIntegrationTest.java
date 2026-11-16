package pl.codelobby.task;

import org.junit.jupiter.api.Test;
import pl.codelobby.auth.User;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPersistenceIntegrationTest extends TaskIntegrationTestSupport {
    @Test
    void shouldRoundTripEntireMultilingualTaskThroughDatabase() {
        User author = author();
        Tag first = tag();
        Tag second = tag();
        Task task = official(author, 42, "Wyszukiwanie binarne — żółć");
        task.addTag(first);
        task.addTag(second);
        Map<ProgrammingLanguage, Map<TaskFileType, Long>> fileIds = new EnumMap<>(ProgrammingLanguage.class);
        for (ProgrammingLanguage language : Set.of(ProgrammingLanguage.JAVA, ProgrammingLanguage.CPP)) {
            TaskLanguage variant = task.addLanguage(language);
            for (TaskFileType type : TaskFileType.values()) {
                variant.addFile(path(type), type, content(language, type));
            }
        }
        Instant beforeSave = Instant.now();
        tasks.saveAndFlush(task);
        Long id = task.getId();
        Instant createdAt = task.getCreatedAt();
        Map<ProgrammingLanguage, Long> languageIds = new EnumMap<>(ProgrammingLanguage.class);
        for (TaskLanguage variant : task.getLanguages()) {
            languageIds.put(variant.getLanguage(), variant.getId());
            fileIds.put(variant.getLanguage(), variant.getFiles().stream()
                    .collect(Collectors.toMap(TaskFile::getType, TaskFile::getId)));
        }
        entityManager.clear();

        Task found = tasks.findById(id).orElseThrow();
        assertThat(found).isNotSameAs(task);
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getTitle()).isEqualTo("Wyszukiwanie binarne — żółć");
        assertThat(found.getDescription()).isEqualTo("Catalog description");
        assertThat(found.getType()).isEqualTo(TaskType.OFFICIAL);
        assertThat(found.getCatalogNumber()).isEqualTo(42);
        assertThat(found.getDifficulty()).isEqualTo(TaskDifficulty.MEDIUM);
        assertThat(found.getCreatedAt()).isEqualTo(createdAt).isBetween(beforeSave, Instant.now());
        assertThat(found.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(found.getAuthor().getEmail()).isEqualTo(author.getEmail());
        assertThat(found.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(found.getTags()).extracting(Tag::getSlug).containsExactlyInAnyOrder(first.getSlug(), second.getSlug());
        assertThat(found.getTags()).extracting(Tag::getName).containsExactlyInAnyOrder(first.getName(), second.getName());
        assertThat(found.getLanguages()).extracting(TaskLanguage::getLanguage)
                .containsExactlyInAnyOrder(ProgrammingLanguage.JAVA, ProgrammingLanguage.CPP);
        for (TaskLanguage variant : found.getLanguages()) {
            assertThat(variant.getId()).isEqualTo(languageIds.get(variant.getLanguage()));
            assertThat(variant.getTask().getId()).isEqualTo(id);
            assertThat(variant.getFiles()).hasSize(4);
            assertThat(variant.getFiles()).extracting(TaskFile::getType).containsExactlyInAnyOrder(TaskFileType.values());
            for (TaskFile file : variant.getFiles()) {
                assertThat(file.getId()).isEqualTo(fileIds.get(variant.getLanguage()).get(file.getType()));
                assertThat(file.getTaskLanguage().getId()).isEqualTo(variant.getId());
                assertThat(file.getPath()).isEqualTo(path(file.getType()));
                assertThat(file.getContent()).isEqualTo(content(variant.getLanguage(), file.getType()));
            }
        }
    }

    // Identical paths are intentional: uniqueness is scoped to a language variant.
    private static String path(TaskFileType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT) + ".txt";
    }

    private static String content(ProgrammingLanguage language, TaskFileType type) {
        return language + " / " + type + "\n" + (type == TaskFileType.EDITABLE ? "// TODO: uzupełnij — żółć\n" : "test data\n");
    }

    @Test
    void shouldPersistCustomTasksWithNullCatalogNumberAndNullDifficulty() {
        User author = author();
        Task first = tasks.saveAndFlush(custom(author, "First private task"));
        Task second = tasks.saveAndFlush(custom(author, "Second private task"));
        entityManager.clear();
        for (Long id : Set.of(first.getId(), second.getId())) {
            Task found = tasks.findById(id).orElseThrow();
            assertThat(found.getType()).isEqualTo(TaskType.CUSTOM);
            assertThat(found.getCatalogNumber()).isNull();
            assertThat(found.getDifficulty()).isNull();
            assertThat(found.getCreatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
        }
    }

    @Test
    void shouldShareTagsWithoutDuplicatingJoinRows() {
        User author = author();
        Tag shared = tag();
        Tag exclusive = tag();
        Task first = custom(author, "First");
        first.addTag(shared);
        first.addTag(shared);
        first.addTag(exclusive);
        Task second = custom(author, "Second");
        second.addTag(shared);
        tasks.saveAndFlush(first);
        tasks.saveAndFlush(second);
        entityManager.clear();
        assertThat(tasks.findById(first.getId()).orElseThrow().getTags()).extracting(Tag::getId)
                .containsExactlyInAnyOrder(shared.getId(), exclusive.getId());
        assertThat(tasks.findById(second.getId()).orElseThrow().getTags()).extracting(Tag::getId)
                .containsExactly(shared.getId());
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM task_tags WHERE task_id IN (:first, :second)")
                .setParameter("first", first.getId()).setParameter("second", second.getId()).getSingleResult();
        assertThat(count.longValue()).isEqualTo(3);
    }

    @Test
    void shouldScopeRepositoryReadsToTaskLanguageAndAuthor() {
        User firstAuthor = author();
        User secondAuthor = author();
        Task first = custom(firstAuthor, "First");
        TaskLanguage java = first.addLanguage(ProgrammingLanguage.JAVA);
        TaskFile javaFile = java.addFile("main.txt", TaskFileType.EDITABLE, "// TODO JAVA");
        TaskLanguage cpp = first.addLanguage(ProgrammingLanguage.CPP);
        TaskFile cppFile = cpp.addFile("main.txt", TaskFileType.HIDDEN_TEST, "CPP secret");
        Task second = custom(secondAuthor, "Second");
        TaskLanguage otherJava = second.addLanguage(ProgrammingLanguage.JAVA);
        TaskFile otherFile = otherJava.addFile("main.txt", TaskFileType.READ_ONLY, "Other user");
        tasks.saveAndFlush(first);
        tasks.saveAndFlush(second);
        Tag tag = tag();
        entityManager.clear();

        assertThat(tags.findBySlug(tag.getSlug())).isPresent().get().extracting(Tag::getId).isEqualTo(tag.getId());
        assertThat(tags.existsBySlug(tag.getSlug())).isTrue();
        assertThat(tags.findById(tag.getId())).isPresent();
        String missingSlug = "missing-" + UUID.randomUUID();
        assertThat(tags.findBySlug(missingSlug)).isEmpty();
        assertThat(tags.existsBySlug(missingSlug)).isFalse();
        assertThat(tags.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(tasks.findAllByAuthor_Id(firstAuthor.getId())).extracting(Task::getId).containsExactly(first.getId());
        assertThat(tasks.findAllByAuthor_Id(secondAuthor.getId())).extracting(Task::getId).containsExactly(second.getId());
        assertThat(tasks.findAllByAuthor_Id(Long.MAX_VALUE)).isEmpty();
        assertThat(tasks.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(languages.findByTask_IdAndLanguage(first.getId(), ProgrammingLanguage.JAVA))
                .isPresent().get().extracting(TaskLanguage::getId).isEqualTo(java.getId());
        assertThat(languages.findByTask_IdAndLanguage(second.getId(), ProgrammingLanguage.JAVA))
                .isPresent().get().extracting(TaskLanguage::getId).isEqualTo(otherJava.getId());
        assertThat(languages.findAllByTask_Id(first.getId())).extracting(TaskLanguage::getId)
                .containsExactlyInAnyOrder(java.getId(), cpp.getId());
        assertThat(languages.findAllByTask_Id(second.getId())).extracting(TaskLanguage::getId).containsExactly(otherJava.getId());
        assertThat(languages.findByTask_IdAndLanguage(first.getId(), ProgrammingLanguage.PYTHON)).isEmpty();
        assertThat(languages.findByTask_IdAndLanguage(Long.MAX_VALUE, ProgrammingLanguage.JAVA)).isEmpty();
        assertThat(languages.findAllByTask_Id(Long.MAX_VALUE)).isEmpty();
        assertThat(languages.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(files.findAllByTaskLanguage_Id(java.getId())).extracting(TaskFile::getId).containsExactly(javaFile.getId());
        assertThat(files.findAllByTaskLanguage_Id(cpp.getId())).extracting(TaskFile::getId).containsExactly(cppFile.getId());
        assertThat(files.findAllByTaskLanguage_Id(otherJava.getId())).extracting(TaskFile::getId).containsExactly(otherFile.getId());
        assertThat(files.findById(javaFile.getId())).isPresent();
        assertThat(files.findById(Long.MAX_VALUE)).isEmpty();
        assertThat(files.findAllByTaskLanguage_Id(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void shouldCascadeJpaDeletionWithoutDeletingSharedTagsAuthorOrOtherTask() {
        User author = author();
        Tag shared = tag();
        Tag exclusive = tag();
        Task first = custom(author, "Delete me");
        first.addTag(shared);
        first.addTag(exclusive);
        TaskLanguage firstLanguage = first.addLanguage(ProgrammingLanguage.JAVA);
        TaskFile firstFile = firstLanguage.addFile("main", TaskFileType.EDITABLE, "// TODO");
        Task second = custom(author, "Keep me");
        second.addTag(shared);
        TaskLanguage secondLanguage = second.addLanguage(ProgrammingLanguage.CPP);
        TaskFile secondFile = secondLanguage.addFile("main", TaskFileType.READ_ONLY, "keep");
        tasks.saveAndFlush(first);
        tasks.saveAndFlush(second);
        entityManager.clear();
        tasks.deleteById(first.getId());
        tasks.flush();
        entityManager.clear();

        assertThat(tasks.findById(first.getId())).isEmpty();
        assertThat(languages.findById(firstLanguage.getId())).isEmpty();
        assertThat(files.findById(firstFile.getId())).isEmpty();
        assertThat(tasks.findById(second.getId())).isPresent();
        assertThat(languages.findById(secondLanguage.getId())).isPresent();
        assertThat(files.findById(secondFile.getId())).isPresent();
        assertThat(tags.findById(shared.getId())).isPresent();
        assertThat(tags.findById(exclusive.getId())).isPresent();
        assertThat(users.findById(author.getId())).isPresent();
        assertThat(tasks.findById(second.getId()).orElseThrow().getTags()).extracting(Tag::getId).containsExactly(shared.getId());
        Number count = (Number) entityManager.createNativeQuery("SELECT count(*) FROM task_tags WHERE task_id = :id")
                .setParameter("id", first.getId()).getSingleResult();
        assertThat(count.longValue()).isZero();
    }
}
