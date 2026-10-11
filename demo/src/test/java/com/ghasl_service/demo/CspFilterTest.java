package com.ghasl_service.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Tests for CspReportOnlyFilter.
 * Verifies the CSP-Report-Only header is absent when disabled and present when enabled.
 */
public class CspFilterTest {

    /** Header must be absent when app.csp.report-only.enabled=false (the default). */
    @SpringBootTest
    @TestPropertySource(properties = {
            "app.csp.report-only.enabled=false",
            "app.geocode.user-agent=",
            "app.geocode.referer="
    })
    static class DisabledTest {

        @Autowired
        private WebApplicationContext context;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }

        @Test
        void cspHeaderAbsentWhenDisabled() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(header().doesNotExist("Content-Security-Policy-Report-Only"));
        }
    }

    /** Header must be present when app.csp.report-only.enabled=true. */
    @SpringBootTest
    @TestPropertySource(properties = {
            "app.csp.report-only.enabled=true",
            "app.geocode.user-agent=",
            "app.geocode.referer="
    })
    static class EnabledTest {

        @Autowired
        private WebApplicationContext context;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }

        @Test
        void cspHeaderPresentWhenEnabled() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(header().exists("Content-Security-Policy-Report-Only"))
                    .andExpect(header().string("Content-Security-Policy-Report-Only",
                            org.hamcrest.Matchers.containsString("default-src 'self'")));
        }
    }
}
