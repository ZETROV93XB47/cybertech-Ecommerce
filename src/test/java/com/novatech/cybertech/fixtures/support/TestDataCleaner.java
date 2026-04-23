package com.novatech.cybertech.fixtures.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Utility component that wipes MySQL tables between integration tests.
 *
 * <p>Each statement is wrapped in its own try/catch so the cleaner is harmless when running under
 * a profile that doesn't have all tables (e.g. an H2-only sub-profile). The H2 dialect rejects
 * {@code SET FOREIGN_KEY_CHECKS}, hence the per-statement isolation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataCleaner {

    private static final List<String> TABLES = List.of(
            "paymentTable",
            "orderItemTable",
            "stockTable",
            "cartItemTable",
            "cartTable",
            "wishlistTable",
            "reviewTable",
            "orderTable",
            "bankCardTable",
            "notificationTable",
            "productTable",
            "userTable"
    );

    private final JdbcTemplate jdbcTemplate;

    public void wipe() {
        executeSilently("SET FOREIGN_KEY_CHECKS=0");

        TABLES.forEach(table -> executeSilently("TRUNCATE TABLE " + table));

        executeSilently("SET FOREIGN_KEY_CHECKS=1");
    }

    private void executeSilently(final String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (final RuntimeException e) {
            log.debug("Skipped cleanup statement [{}]: {}", sql, e.getMessage());
        }
    }
}
