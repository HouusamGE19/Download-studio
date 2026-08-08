package com.localstudio.downloadstudio.download;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Component
class HistoryStore {
    private final ObjectMapper objectMapper;
    private final Path historyFile;

    HistoryStore(ObjectMapper objectMapper, @Value("${download.history}") String historyPath) {
        this.objectMapper = objectMapper;
        this.historyFile = Path.of(historyPath).toAbsolutePath().normalize();
    }

    List<DownloadSnapshot> load() {
        if (!Files.isRegularFile(historyFile)) return List.of();
        try {
            return objectMapper.readValue(historyFile.toFile(), new TypeReference<>() {});
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    synchronized void save(List<DownloadSnapshot> snapshots) {
        try {
            Files.createDirectories(historyFile.getParent());
            Path temporary = historyFile.resolveSibling(historyFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), snapshots);
            Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            // A history failure must never stop an active download.
        }
    }
}
