package fritids.norskgolf;

import fritids.norskgolf.entities.Course;
import fritids.norskgolf.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The production courses table already has ~160 rows and no "active" column. Hibernate's
 * ddl-auto=update must add it WITH a default, or Postgres rejects the ALTER and the reconciler
 * then fails on a missing column. This boots against a pre-created table that has rows but no
 * "active" column and proves the existing row comes back active.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ddlcheck;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:ddl-check-schema.sql",
        // dry run: the startup reconciler would otherwise deactivate the legacy row (no club in
        // the list matches it) and this test would measure the reconciler, not the DDL default.
        "app.clubs.dry-run=true",
        "spring.jpa.properties.hibernate.show_sql=true"
})
class ActiveColumnMigrationTest {

    @Autowired private CourseRepository courseRepository;

    @Test
    void addsTheActiveColumnToAnExistingTableWithRowsAndBackfillsThemAsActive() {
        Course legacy = courseRepository.findByExternalId("osm-legacy").orElseThrow();
        assertTrue(legacy.isActive(), "rows that existed before the column was added must default to active");
    }
}
