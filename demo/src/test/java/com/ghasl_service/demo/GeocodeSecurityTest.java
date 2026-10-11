package com.ghasl_service.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "app.geocode.user-agent=LifeWashTest/1.0 (test@example.com)",
        "app.geocode.referer=https://lifewash.example.com"
})
public class GeocodeSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RestTemplate geocodeRestTemplate;

    private MockMvc mockMvc;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        mockServer = MockRestServiceServer.createServer(geocodeRestTemplate);
    }

    @Test
    void testUnauthenticatedReverseGeocodeReturns200() throws Exception {
        String jsonResponse = "{\"display_name\":\"Erbil Citadel, Erbil, Kurdistan\"}";
        mockServer.expect(ExpectedCount.once(), requestTo(containsString("nominatim.openstreetmap.org")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/geocode/reverse")
                        .param("lat", "36.19")
                        .param("lng", "44.01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("Erbil Citadel, Erbil, Kurdistan"));

        mockServer.verify();
    }

    @Test
    void testAnonymousPostOrdersReachabilityUnchanged() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
