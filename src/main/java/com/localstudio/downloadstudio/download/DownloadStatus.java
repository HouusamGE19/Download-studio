package com.localstudio.downloadstudio.download;

public enum DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELED;

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
