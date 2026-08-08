package com.localstudio.downloadstudio.download;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DownloadService {
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final Pattern FALLBACK_PROGRESS = Pattern.compile("\\[download]\\s+([0-9.]+)%.*?(?:at\\s+([^\\s]+))?.*?(?:ETA\\s+([^\\s]+))?");

    private final Map<UUID, DownloadJob> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, Process> processes = new ConcurrentHashMap<>();
    private final ExecutorService queue = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "download-queue");
        thread.setDaemon(false);
        return thread;
    });
    private final DownloadEventHub eventHub;
    private final HistoryStore historyStore;
    private final Path scriptPath;
    private final Path downloadDirectory;

    public DownloadService(DownloadEventHub eventHub, HistoryStore historyStore,
                           @Value("${download.script}") String scriptPath,
                           @Value("${download.default-directory}") String downloadDirectory) {
        this.eventHub = eventHub;
        this.historyStore = historyStore;
        this.scriptPath = Path.of(scriptPath).toAbsolutePath().normalize();
        this.downloadDirectory = Path.of(downloadDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    void restoreHistory() {
        historyStore.load().stream().limit(50).map(DownloadJob::new).forEach(job -> jobs.put(job.id, job));
    }

    public DownloadSnapshot create(DownloadRequest request) {
        validateUrl(request.url());
        try {
            Files.createDirectories(downloadDirectory);
            if (!Files.isWritable(downloadDirectory)) throw new IllegalArgumentException("The downloads directory is not writable");
        } catch (IOException error) {
            throw new IllegalArgumentException("Could not create the downloads directory");
        }
        DownloadJob job = new DownloadJob(request, downloadDirectory.toString());
        jobs.put(job.id, job);
        changed(job);
        queue.submit(() -> execute(job));
        return job.snapshot();
    }

    public List<DownloadSnapshot> list() {
        return jobs.values().stream().map(DownloadJob::snapshot)
                .sorted(Comparator.comparing(DownloadSnapshot::createdAt).reversed()).limit(50).toList();
    }

    public DownloadSnapshot get(UUID id) {
        return requireJob(id).snapshot();
    }

    public DownloadSnapshot cancel(UUID id) {
        DownloadJob job = requireJob(id);
        synchronized (job) {
            if (job.status.isFinished()) return job.snapshot();
            job.status = DownloadStatus.CANCELED;
            job.message = "Canceled";
            job.finishedAt = Instant.now();
        }
        Process process = processes.get(id);
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        changed(job);
        return job.snapshot();
    }

    public DownloadSnapshot retry(UUID id) {
        DownloadSnapshot previous = requireJob(id).snapshot();
        return create(new DownloadRequest(previous.url()));
    }

    public void clearFinished() {
        jobs.entrySet().removeIf(entry -> entry.getValue().snapshot().status().isFinished());
        persist();
    }

    public Path scriptPath() {
        return scriptPath;
    }

    private void execute(DownloadJob job) {
        synchronized (job) {
            if (job.status == DownloadStatus.CANCELED) return;
            job.status = DownloadStatus.DOWNLOADING;
            job.startedAt = Instant.now();
            job.message = "Connecting to media source";
        }
        changed(job);
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(scriptPath.toString());
        command.add(job.url);
        command.add(job.outputDirectory);

        try {
            if (!Files.isRegularFile(scriptPath)) throw new IOException("Download script not found at " + scriptPath);
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.directory(scriptPath.getParent().toFile());
            Process process = builder.start();
            processes.put(job.id, process);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseOutput(job, ANSI.matcher(line).replaceAll(""));
                }
            }
            int exitCode = process.waitFor();
            synchronized (job) {
                if (job.status != DownloadStatus.CANCELED) {
                    job.finishedAt = Instant.now();
                    if (exitCode == 0) {
                        job.status = DownloadStatus.COMPLETED;
                        job.progress = 100;
                        job.message = "Download finished";
                    } else {
                        job.status = DownloadStatus.FAILED;
                        job.message = job.failureReason.isBlank()
                                ? "Download failed (exit code " + exitCode + ")"
                                : job.failureReason;
                    }
                }
            }
        } catch (Exception error) {
            synchronized (job) {
                if (job.status != DownloadStatus.CANCELED) {
                    job.status = DownloadStatus.FAILED;
                    job.message = error.getMessage() == null ? "Download failed" : error.getMessage();
                    job.addLog(job.message);
                    job.finishedAt = Instant.now();
                }
            }
        } finally {
            processes.remove(job.id);
            changed(job);
        }
    }

    private void parseOutput(DownloadJob job, String line) {
        synchronized (job) {
            job.addLog(line);
            if (line.startsWith("DL_PROGRESS|")) {
                String[] parts = line.split("\\|", 5);
                if (parts.length > 1) job.progress = parsePercent(parts[1]);
                if (parts.length > 2) job.speed = cleanValue(parts[2]);
                if (parts.length > 3) job.eta = cleanValue(parts[3]);
                if (parts.length > 4 && !cleanValue(parts[4]).isBlank()) job.title = cleanValue(parts[4]);
                job.status = DownloadStatus.DOWNLOADING;
                job.message = "Downloading";
            } else if (line.startsWith("DL_PROCESSING|")) {
                job.status = DownloadStatus.PROCESSING;
                job.title = cleanValue(line.substring("DL_PROCESSING|".length()));
                job.message = "Merging and embedding metadata";
            } else if (line.startsWith("DL_FILE|")) {
                job.currentFile = cleanValue(line.substring("DL_FILE|".length()));
            } else if (line.startsWith("ERROR:")) {
                job.failureReason = cleanValue(line.substring("ERROR:".length()));
                job.message = job.failureReason;
            } else {
                Matcher fallback = FALLBACK_PROGRESS.matcher(line);
                if (fallback.find()) {
                    job.progress = parsePercent(fallback.group(1));
                    if (fallback.group(2) != null) job.speed = fallback.group(2);
                    if (fallback.group(3) != null) job.eta = fallback.group(3);
                }
                if (line.contains("Downloading item")) job.message = line.trim();
            }
        }
        eventHub.publish(job.snapshot());
    }

    private double parsePercent(String value) {
        try {
            return Math.max(0, Math.min(100, Double.parseDouble(value.replace("%", "").trim())));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String cleanValue(String value) {
        if (value == null) return "";
        String cleaned = value.trim();
        return "NA".equalsIgnoreCase(cleaned) || "N/A".equalsIgnoreCase(cleaned) ? "" : cleaned;
    }

    private void validateUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException("Enter a valid http or https URL");
            }
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Enter a valid video or playlist URL");
        }
    }

    private DownloadJob requireJob(UUID id) {
        DownloadJob job = jobs.get(id);
        if (job == null) throw new IllegalArgumentException("Download not found");
        return job;
    }

    private void changed(DownloadJob job) {
        DownloadSnapshot snapshot = job.snapshot();
        eventHub.publish(snapshot);
        persist();
    }

    private void persist() {
        historyStore.save(list());
    }

    @PreDestroy
    void shutdown() {
        processes.values().forEach(Process::destroy);
        queue.shutdownNow();
        persist();
    }
}
