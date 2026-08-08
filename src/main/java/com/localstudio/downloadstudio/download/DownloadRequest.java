package com.localstudio.downloadstudio.download;

import jakarta.validation.constraints.NotBlank;

public record DownloadRequest(
        @NotBlank(message = "Paste a video or playlist URL") String url
) {}
