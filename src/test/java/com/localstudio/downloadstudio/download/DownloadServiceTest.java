package com.localstudio.downloadstudio.download;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadServiceTest {
    @Test
    void statusFinishedStatesAreCorrect() {
        assertThat(DownloadStatus.COMPLETED.isFinished()).isTrue();
        assertThat(DownloadStatus.FAILED.isFinished()).isTrue();
        assertThat(DownloadStatus.CANCELED.isFinished()).isTrue();
        assertThat(DownloadStatus.DOWNLOADING.isFinished()).isFalse();
        assertThat(DownloadStatus.QUEUED.isFinished()).isFalse();
    }
}
