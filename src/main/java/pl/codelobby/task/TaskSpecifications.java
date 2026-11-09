package pl.codelobby.task;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TaskSpecifications {

    private static final float MIN_WORD_SIMILARITY = 0.4f;

    private TaskSpecifications() {
    }

    public static Specification<Task> officialCatalog(
            String query,
            TaskDifficulty difficulty,
            String tag
    ) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            List<Order> orders = new ArrayList<>();

            conditions.add(
                    cb.equal(root.get("type"), TaskType.OFFICIAL)
            );

            if (query != null && !query.isBlank()) {
                String normalizedQuery = query.strip()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", " ");

                List<String> words = Arrays.stream(
                                normalizedQuery.split("[^\\p{L}\\p{N}]+")
                        )
                        .filter(word -> !word.isBlank())
                        .distinct()
                        .toList();

                List<Predicate> wordMatches = new ArrayList<>();
                List<Predicate> literalMatches = new ArrayList<>();
                List<Predicate> numberMatches = new ArrayList<>();

                var title = cb.lower(root.<String>get("title"));

                for (String word : words) {
                    if (word.matches("[0-9]+")) {
                        Predicate numberMatch;

                        try {
                            int number = Integer.parseInt(word);

                            numberMatch = cb.equal(
                                    root.get("catalogNumber"),
                                    number
                            );
                        } catch (NumberFormatException exception) {
                            numberMatch = cb.disjunction();
                        }

                        wordMatches.add(numberMatch);
                        literalMatches.add(numberMatch);
                        numberMatches.add(numberMatch);
                        continue;
                    }

                    Predicate containsWord = cb.like(
                            title,
                            "%" + escapeLike(word) + "%",
                            '\\'
                    );

                    literalMatches.add(containsWord);

                    if (word.length() < 3) {
                        wordMatches.add(containsWord);
                        continue;
                    }

                    var similarity = cb.function(
                            "word_similarity",
                            Float.class,
                            cb.literal(word),
                            title
                    );

                    wordMatches.add(
                            cb.or(
                                    containsWord,
                                    cb.ge(similarity, MIN_WORD_SIMILARITY)
                            )
                    );
                }

                conditions.add(
                        cb.or(wordMatches.toArray(Predicate[]::new))
                );

                Predicate matchesNumber = cb.or(
                        numberMatches.toArray(Predicate[]::new)
                );

                Predicate matchesTitle = cb.equal(
                        title,
                        normalizedQuery
                );

                var phraseSimilarity = cb.function(
                        "word_similarity",
                        Float.class,
                        cb.literal(normalizedQuery),
                        title
                );

                orders.add(cb.desc(pointFor(cb, matchesNumber)));
                orders.add(cb.desc(pointFor(cb, matchesTitle)));
                orders.add(cb.desc(countMatches(cb, wordMatches)));
                orders.add(cb.desc(countMatches(cb, literalMatches)));
                orders.add(cb.desc(phraseSimilarity));
                orders.add(cb.asc(cb.length(title)));
            }

            if (difficulty != null) {
                conditions.add(
                        cb.equal(root.get("difficulty"), difficulty)
                );
            }

            if (tag != null && !tag.isBlank()) {
                conditions.add(
                        cb.equal(
                                root.join("tags").get("slug"),
                                tag.trim()
                        )
                );
            }

            orders.add(cb.asc(root.get("catalogNumber")));
            orders.add(cb.asc(root.get("id")));

            if (criteriaQuery != null
                    && Task.class.equals(criteriaQuery.getResultType())) {
                criteriaQuery.orderBy(orders);
            }

            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    private static Expression<Integer> pointFor(
            CriteriaBuilder cb,
            Predicate condition
    ) {
        return cb.<Integer>selectCase()
                .when(condition, 1)
                .otherwise(0);
    }

    private static Expression<Integer> countMatches(
            CriteriaBuilder cb,
            List<Predicate> matches
    ) {
        Expression<Integer> count = cb.literal(0);

        for (Predicate match : matches) {
            count = cb.sum(count, pointFor(cb, match));
        }

        return count;
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}