package pl.codelobby.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class TaskCatalogServiceIntegrationTest extends TaskIntegrationTestSupport {
    @Autowired TaskCatalogService catalog;
    private static final PageRequest BY_NUMBER = PageRequest.of(0, 10, Sort.by("catalogNumber"));

    @BeforeEach
    void seedCatalog() {
        var author = author();
        tasks.save(official(author, 42, "Binary Search"));
        tasks.save(official(author, 7, "binary tree"));
        tasks.save(official(author, 142, "Graph traversal"));
        tasks.save(custom(author, "Private binary 42"));
        tasks.save(custom(author(), "Binary Search"));
        tasks.flush();
        entityManager.clear();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    void shouldListOnlyOfficialTasksForEmptyQuery(String query) {
        Page<Task> result = catalog.search(query, BY_NUMBER);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).extracting(Task::getCatalogNumber).containsExactly(7, 42, 142);
        assertOfficial(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", " 42 ", "00042", "\t42\n"})
    void shouldSearchExactCatalogNumberRatherThanTitleOrNumberSubstring(String query) {
        Page<Task> result = catalog.search(query, BY_NUMBER);
        assertThat(result.getContent()).extracting(Task::getCatalogNumber).containsExactly(42);
        assertOfficial(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"binary", "BiNaRy", "  BINARY  ", "inary"})
    void shouldSearchTitleFragmentIgnoringCaseAndOuterWhitespace(String query) {
        Page<Task> result = catalog.search(query, BY_NUMBER);
        assertThat(result.getContent()).extracting(Task::getCatalogNumber).containsExactly(7, 42);
        assertOfficial(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "missing title", "2147483648", "99999999999999999999999999999999999999999"})
    void shouldReturnEmptyPageForMissingQueryOrNumberOutsideIntegerRange(String query) {
        PageRequest pageable = PageRequest.of(2, 2, Sort.by("title"));
        Page<Task> result = catalog.search(query, pageable);
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getPageable()).isEqualTo(pageable);
    }

    @Test
    void shouldApplySortingAndPaginationToWholeCatalog() {
        Sort sort = Sort.by(Sort.Direction.DESC, "catalogNumber");
        Page<Task> first = catalog.search(null, PageRequest.of(0, 2, sort));
        Page<Task> second = catalog.search(null, PageRequest.of(1, 2, sort));
        assertThat(first.getContent()).extracting(Task::getCatalogNumber).containsExactly(142, 42);
        assertThat(second.getContent()).extracting(Task::getCatalogNumber).containsExactly(7);
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isFalse();
        assertThat(catalog.search(null, PageRequest.of(2, 2, sort)).getContent()).isEmpty();
        assertOfficial(first);
        assertOfficial(second);
    }

    @Test
    void shouldApplySortingAndPaginationToTextSearch() {
        Sort sort = Sort.by(Sort.Direction.DESC, "catalogNumber");
        Page<Task> first = catalog.search("binary", PageRequest.of(0, 1, sort));
        Page<Task> second = catalog.search("binary", PageRequest.of(1, 1, sort));
        assertThat(first.getContent()).extracting(Task::getCatalogNumber).containsExactly(42);
        assertThat(second.getContent()).extracting(Task::getCatalogNumber).containsExactly(7);
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertOfficial(first);
        assertOfficial(second);
    }

    private static void assertOfficial(Page<Task> result) {
        assertThat(result.getContent()).allSatisfy(task -> assertThat(task.getType()).isEqualTo(TaskType.OFFICIAL));
    }
}
