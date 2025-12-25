package dev.backend.controller;

import dev.backend.service.FrontendService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FrontendController.class)
@AutoConfigureMockMvc(addFilters = false)
class FrontendControllerWebMvcTest {

    @Autowired MockMvc mvc;

    @MockitoBean
    FrontendService frontendService;

    @Test
    void ok() throws Exception {
        mvc.perform(get("/api/backend/recent"))
                .andExpect(status().isOk());
    }
}

