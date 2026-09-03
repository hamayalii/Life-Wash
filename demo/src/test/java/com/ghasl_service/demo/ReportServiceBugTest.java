package com.ghasl_service.demo;

import com.ghasl_service.demo.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ReportServiceBugTest {

    @Autowired
    private ReportService reportService;

    @Test
    public void testDailyReport() {
        System.out.println(reportService.generateDailyReport());
        System.out.println(reportService.generateMonthlyReport());
    }
}
