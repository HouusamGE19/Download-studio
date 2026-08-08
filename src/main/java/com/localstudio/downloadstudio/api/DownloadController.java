package com.localstudio.downloadstudio.api;

import com.localstudio.downloadstudio.download.DownloadEventHub;
import com.localstudio.downloadstudio.download.DownloadRequest;
import com.localstudio.downloadstudio.download.DownloadService;
import com.localstudio.downloadstudio.download.DownloadSnapshot;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/downloads")
public class DownloadController {
    private final DownloadService downloads;
    private final DownloadEventHub events;

    public DownloadController(DownloadService downloads, DownloadEventHub events) {
        this.downloads = downloads;
        this.events = events;
    }

    @GetMapping
    public List<DownloadSnapshot> list() {
        return downloads.list();
    }

    @GetMapping("/{id}")
    public DownloadSnapshot get(@PathVariable UUID id) {
        return downloads.get(id);
    }

    @PostMapping
    public DownloadSnapshot create(@Valid @RequestBody DownloadRequest request) {
        return downloads.create(request);
    }

    @PostMapping("/{id}/cancel")
    public DownloadSnapshot cancel(@PathVariable UUID id) {
        return downloads.cancel(id);
    }

    @PostMapping("/{id}/retry")
    public DownloadSnapshot retry(@PathVariable UUID id) {
        return downloads.retry(id);
    }

    @DeleteMapping("/finished")
    public Map<String, Boolean> clearFinished() {
        downloads.clearFinished();
        return Map.of("cleared", true);
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return events.subscribe(downloads.list());
    }
}
