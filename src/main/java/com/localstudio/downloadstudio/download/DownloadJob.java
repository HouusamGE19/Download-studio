package com.localstudio.downloadstudio.download;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.UUID;

final class DownloadJob {
    private static final int MAX_LOG_LINES = 160;

    final UUID id;
    final String url;
    final String outputDirectory;
    final String browser;
    final Instant createdAt;
    DownloadStatus status;
    double progress;
    String title = "Preparing download";
    String speed = "";
    String eta = "";
    String currentFile = "";
    String message = "Waiting in queue";
    String failureReason = "";
    Instant startedAt;
    Instant finishedAt;
    final Deque<String> logs = new ArrayDeque<>();

    DownloadJob(DownloadRequest request, String normalizedDirectory) {
        this.id = UUID.randomUUID();
        this.url = request.url().trim();
        this.outputDirectory = normalizedDirectory;
        this.browser = "";
        this.createdAt = Instant.now();
        this.status = DownloadStatus.QUEUED;
    }

    DownloadJob(DownloadSnapshot snapshot) {
        this.id = snapshot.id();
        this.url = snapshot.url();
        this.outputDirectory = snapshot.outputDirectory();
        this.browser = snapshot.browser();
        this.createdAt = snapshot.createdAt();
        this.status = snapshot.status().isFinished() ? snapshot.status() : DownloadStatus.CANCELED;
        this.progress = snapshot.progress();
        this.title = snapshot.title();
        this.speed = snapshot.speed();
        this.eta = snapshot.eta();
        this.currentFile = snapshot.currentFile();
        this.message = snapshot.status().isFinished() ? snapshot.message() : "Interrupted when the app closed";
        this.startedAt = snapshot.startedAt();
        this.finishedAt = snapshot.finishedAt() == null ? Instant.now() : snapshot.finishedAt();
        if (snapshot.logs() != null) this.logs.addAll(snapshot.logs());
    }

    synchronized void addLog(String line) {
        if (line == null || line.isBlank()) return;
        logs.addLast(line);
        while (logs.size() > MAX_LOG_LINES) logs.removeFirst();
    }

    synchronized DownloadSnapshot snapshot() {
        return new DownloadSnapshot(id, url, outputDirectory, browser, status, progress, title,
                speed, eta, currentFile, message, createdAt, startedAt, finishedAt,
                new ArrayList<>(logs));
    }
}
