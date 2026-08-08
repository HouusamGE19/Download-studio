package com.localstudio.downloadstudio.download;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DownloadEventHub {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(List<DownloadSnapshot> initialJobs) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("jobs").data(initialJobs));
        } catch (IOException error) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(DownloadSnapshot job) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("job").data(job));
            } catch (IOException error) {
                emitters.remove(emitter);
                emitter.complete();
            }
        }
    }
}
