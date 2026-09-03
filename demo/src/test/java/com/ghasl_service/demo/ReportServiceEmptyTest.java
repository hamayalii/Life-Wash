package com.ghasl_service.demo;

import com.ghasl_service.demo.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;


@SpringBootTest
@Transactional  // rolls back after each test — does NOT permanently delete production data
public class ReportServiceEmptyTest {

    @Autowired
    private ReportService reportService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testEmptyReport() {
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM leads");
        System.out.println(reportService.generateDailyReport());
        System.out.println(reportService.generateMonthlyReport());
    }
}
