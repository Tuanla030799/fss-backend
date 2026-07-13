package com.fss.backend.logging;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {
    private final LogStreamService logStreamService;

    public AdminLogController(LogStreamService logStreamService) {
        this.logStreamService = logStreamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "200") int lines) {
        return logStreamService.streamCurrentLog(lines);
    }
}
