package com.obs.api.exception;

import com.obs.api.controller.PathMetricController;
import com.obs.api.service.PathMetricService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the error response contract (status, body shape, message) produced by
 * ApiExceptionHandler for each exception type it handles.
 */
@WebMvcTest(PathMetricController.class)
class ApiExceptionHandlerTest {

    @Autowired MockMvc mvc;
    @MockBean  PathMetricService service;

    private static final String PATH = "/api/metrics/path";
    private static final String FROM = "1995-07-01T00:00:00Z";
    private static final String TO   = "1995-08-01T00:00:00Z";

    @Test
    void badRequestExceptionYields400WithMessageAndStructuredBody() throws Exception {
        mvc.perform(get(PATH)
                .param("path", "   ")   // blank path → BadRequestException
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.status").value(400))
           .andExpect(jsonPath("$.error").value("Bad Request"))
           .andExpect(jsonPath("$.message").value("path must not be blank"))
           .andExpect(jsonPath("$.timestamp").exists())
           .andExpect(jsonPath("$.path").value(PATH));
    }

    @Test
    void missingRequiredParamYields400WithGenericMessage() throws Exception {
        mvc.perform(get(PATH)
                // "path" param deliberately omitted → MissingServletRequestParameterException
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.status").value(400))
           .andExpect(jsonPath("$.message").value("invalid or missing parameter"))
           .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void databaseUnavailableYields503WithStructuredBody() throws Exception {
        when(service.range(any(), any(), any()))
            .thenThrow(new DataAccessResourceFailureException("cassandra down"));

        mvc.perform(get(PATH)
                .param("path", "/index.html")
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isServiceUnavailable())
           .andExpect(jsonPath("$.status").value(503))
           .andExpect(jsonPath("$.error").value("Service Unavailable"))
           .andExpect(jsonPath("$.message").value("database unavailable"))
           .andExpect(jsonPath("$.timestamp").exists())
           .andExpect(jsonPath("$.path").value(PATH));
    }

    @Test
    void invalidIso8601ParamYields400WithSpecificMessage() throws Exception {
        mvc.perform(get(PATH)
                .param("path", "/index.html")
                .param("from", "not-a-date")
                .param("to",   TO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.status").value(400))
           .andExpect(jsonPath("$.message").value("invalid ISO-8601 value for parameter: from"));
    }

    @Test
    void errorBodyIncludesRequestPath() throws Exception {
        mvc.perform(get(PATH)
                .param("path", "/index.html")
                .param("from", "bad")
                .param("to",   TO))
           .andExpect(jsonPath("$.path").value(PATH));
    }

    @Test
    void successfulRequestDoesNotReturnErrorBody() throws Exception {
        when(service.range(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get(PATH)
                .param("path", "/index.html")
                .param("from", FROM)
                .param("to",   TO))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").doesNotExist())
           .andExpect(jsonPath("$.error").doesNotExist());
    }
}
