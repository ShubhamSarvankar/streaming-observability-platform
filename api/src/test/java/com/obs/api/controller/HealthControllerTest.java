package com.obs.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void returnsStatusUp() throws Exception {
        mvc.perform(get("/api/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void respondsWithJsonContentType() throws Exception {
        mvc.perform(get("/api/health"))
           .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void rejectsPostMethod() throws Exception {
        mvc.perform(post("/api/health"))
           .andExpect(status().isMethodNotAllowed());
    }
}
