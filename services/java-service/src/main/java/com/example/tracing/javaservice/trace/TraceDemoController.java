package com.example.tracing.javaservice.trace;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trace-demo")
public class TraceDemoController {

    private final TraceDemoService traceDemoService;

    public TraceDemoController(TraceDemoService traceDemoService) {
        this.traceDemoService = traceDemoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TraceDemoResponse create(@Valid @RequestBody TraceDemoRequest request) {
        return traceDemoService.createAndTrace(request);
    }

    @GetMapping("/{requestId}")
    public StoredTraceResponse getById(@PathVariable long requestId) {
        return traceDemoService.getRequest(requestId);
    }
}
