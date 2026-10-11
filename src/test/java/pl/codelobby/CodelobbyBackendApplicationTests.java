
package pl.codelobby;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = "DB_PASSWORD=unused")
class CodelobbyBackendApplicationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres =
			new PostgreSQLContainer("postgres:17-alpine")
					.withDatabaseName("codelobby_test");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void shouldConnectToPostgresql() {
		String database = jdbcTemplate.queryForObject(
				"SELECT current_database()",
				String.class
		);

		assertEquals("codelobby_test", database);
	}

	@Test
	void shouldApplyFlywayMigration() {
		Integer migrationCount = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '1' AND success = true
                """,
				Integer.class
		);

		assertEquals(1, migrationCount);

		Boolean tableExists = jdbcTemplate.queryForObject(
				"""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = 'app_metadata'
                )
                """,
				Boolean.class
		);

		assertTrue(tableExists);
	}
}
