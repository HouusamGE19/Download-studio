package com.localstudio.downloadstudio.api;

import com.localstudio.downloadstudio.download.DownloadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    public record SystemStatus(boolean ready, boolean ytDlp, boolean ffmpeg, boolean script,
                               String defaultDirectory, String message) {}

    private final DownloadService downloads;
    private final Path defaultDirectory;

    public SystemController(DownloadService downloads,
                            @Value("${download.default-directory}") String defaultDirectory) {
        this.downloads = downloads;
        this.defaultDirectory = Path.of(defaultDirectory).toAbsolutePath().normalize();
    }

    @GetMapping("/status")
    public SystemStatus status() {
        boolean ytDlp = executableExists("yt-dlp");
        boolean ffmpeg = executableExists("ffmpeg");
        boolean script = Files.isRegularFile(downloads.scriptPath());
        boolean ready = ytDlp && ffmpeg && script;
        String message = ready ? "Ready to download" : "Missing: " + String.join(", ",
                (!ytDlp ? "yt-dlp " : "") + (!ffmpeg ? "ffmpeg " : "") + (!script ? "download script" : ""));
        return new SystemStatus(ready, ytDlp, ffmpeg, script, defaultDirectory.toString(), message.trim());
    }

    private boolean executableExists(String command) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        return Arrays.stream(path.split(System.getProperty("path.separator")))
                .map(Path::of).map(directory -> directory.resolve(command))
                .anyMatch(file -> Files.isRegularFile(file) && Files.isExecutable(file));
    }
}
