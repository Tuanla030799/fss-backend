package com.fss.backend.config;

import com.fss.backend.common.HealthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = CacheHeaderWebMvcTest.TestConfig.class)
@TestPropertySource(properties = "app.upload-dir=build/tmp/test-uploads")
class CacheHeaderWebMvcTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        Path uploadFile = Path.of("build/tmp/test-uploads/2026-01-01/a.png");
        Files.createDirectories(uploadFile.getParent());
        Files.write(uploadFile, new byte[]{1, 2, 3});

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(new ApiNoCacheFilter())
                .build();
    }

    @Test
    void apiShouldReturnNoCacheHeaders() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"));
    }

    @Test
    void filesShouldReturnPublicCacheHeaders() throws Exception {
        mockMvc.perform(get("/files/2026-01-01/a.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
    }

    @Configuration
    @EnableWebMvc
    @Import({HealthController.class, UploadResourceConfig.class})
    static class TestConfig {
    }
}
