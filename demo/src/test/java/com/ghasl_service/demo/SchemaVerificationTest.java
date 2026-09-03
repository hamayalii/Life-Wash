package com.ghasl_service.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class SchemaVerificationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void verifySchema() {
        System.out.println("=== orders table columns ===");
        List<Map<String, Object>> ordersColumns = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns " +
            "WHERE table_name = 'orders' ORDER BY ordinal_position"
        );
        ordersColumns.forEach(row ->
            System.out.println("  orders." + row.get("column_name") + " -> " + row.get("data_type"))
        );

        System.out.println("\n=== leads table columns ===");
        List<Map<String, Object>> leadsColumns = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns " +
            "WHERE table_name = 'leads' ORDER BY ordinal_position"
        );
        leadsColumns.forEach(row ->
            System.out.println("  leads." + row.get("column_name") + " -> " + row.get("data_type"))
        );

        // Assert columns exist
        boolean hasWorkStatus = ordersColumns.stream()
            .anyMatch(r -> "work_status".equals(r.get("column_name")));
        boolean hasLeadCreatedAt = leadsColumns.stream()
            .anyMatch(r -> "created_at".equals(r.get("column_name")));

        System.out.println("\n=== Assertions ===");
        System.out.println("orders.work_status exists: " + hasWorkStatus);
        System.out.println("leads.created_at exists:   " + hasLeadCreatedAt);

        if (!hasWorkStatus) throw new AssertionError("MISSING: orders.work_status column");
        if (!hasLeadCreatedAt) throw new AssertionError("MISSING: leads.created_at column");
        System.out.println("All schema assertions PASSED.");
    }
}
