package com.example.tracing.javaservice.trace;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(TraceDemoController.class)
class TraceDemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TraceDemoService traceDemoService;

    @Test
    void createReturnsCreatedResponse() throws Exception {
        TraceDemoResponse response = new TraceDemoResponse(
                7L,
                "hello",
                "COMPLETED",
                Instant.parse("2026-01-01T00:00:00Z"),
                "trace-123",
                new KotlinTraceResponse("kotlin-service", "OK", "redis:key", "object-key", "trace-123")
        );

        given(traceDemoService.createAndTrace(new TraceDemoRequest("hello"))).willReturn(response);

        mockMvc.perform(post("/api/v1/trace-demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(7))
                .andExpect(jsonPath("$.traceId").value("trace-123"))
                .andExpect(jsonPath("$.downstream.service").value("kotlin-service"));
    }

    @Test
    void getReturnsStoredTrace() throws Exception {
        StoredTraceResponse response = new StoredTraceResponse(
                7L,
                "hello",
                "COMPLETED",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z")
        );

        given(traceDemoService.getRequest(7L)).willReturn(response);

        mockMvc.perform(get("/api/v1/trace-demo/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(7))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
