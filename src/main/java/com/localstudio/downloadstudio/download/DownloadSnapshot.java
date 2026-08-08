package com.localstudio.downloadstudio.download;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DownloadSnapshot(
        UUID id,
        String url,
        String outputDirectory,
        String browser,
        DownloadStatus status,
        double progress,
        String title,
        String speed,
        String eta,
        String currentFile,
        String message,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        List<String> logs
) {}
