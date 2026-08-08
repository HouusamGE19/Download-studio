import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DownloadJob, DownloadStatus } from './models';
import { DownloadService } from './download.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  readonly downloads = inject(DownloadService);
  readonly form = this.fb.nonNullable.group({
    url: ['', [Validators.required]]
  });

  readonly submitting = signal(false);
  readonly initializing = signal(true);
  readonly selectedJobId = signal<string | null>(null);
  readonly selectedJob = computed(() => {
    const id = this.selectedJobId();
    return id ? this.downloads.jobs().find(job => job.id === id) ?? null : null;
  });
  readonly toast = signal<{ kind: 'success' | 'error'; message: string } | null>(null);
  readonly darkTheme = signal(localStorage.getItem('download-studio-theme') !== 'light');

  readonly activeJobs = computed(() => this.downloads.jobs().filter(job => !this.finished(job)));
  readonly completedCount = computed(() => this.downloads.jobs().filter(job => job.status === 'COMPLETED').length);
  readonly totalProgress = computed(() => {
    const active = this.activeJobs();
    return active.length ? Math.round(active.reduce((sum, job) => sum + job.progress, 0) / active.length) : 0;
  });


  async ngOnInit(): Promise<void> {
    this.applyTheme();
    try {
      await this.downloads.initialize();
    } catch (error) {
      this.showError(error, 'Could not connect to the local service');
    } finally {
      this.initializing.set(false);
    }
  }

  async submit(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    try {
      if ('Notification' in window && Notification.permission === 'default') {
        await Notification.requestPermission();
      }
      const value = this.form.getRawValue();
      await this.downloads.start(value.url);
      this.form.controls.url.reset('');
      this.showToast('success', 'Added to the download queue');
    } catch (error) {
      this.showError(error, 'Could not start the download');
    } finally {
      this.submitting.set(false);
    }
  }

  async cancel(job: DownloadJob, event?: Event): Promise<void> {
    event?.stopPropagation();
    try {
      await this.downloads.cancel(job.id);
      this.showToast('success', 'Download canceled');
    } catch (error) {
      this.showError(error, 'Could not cancel the download');
    }
  }

  async retry(job: DownloadJob, event?: Event): Promise<void> {
    event?.stopPropagation();
    try {
      await this.downloads.retry(job.id);
      this.showToast('success', 'Download added to the queue again');
    } catch (error) {
      this.showError(error, 'Could not retry the download');
    }
  }

  async clearFinished(): Promise<void> {
    try {
      await this.downloads.clearFinished();
      this.selectedJobId.set(null);
    } catch (error) {
      this.showError(error, 'Could not clear the history');
    }
  }

  toggleTheme(): void {
    this.darkTheme.update(value => !value);
    localStorage.setItem('download-studio-theme', this.darkTheme() ? 'dark' : 'light');
    this.applyTheme();
  }

  typeLabel(url: string): string {
    if (!url.trim()) return 'Paste a link to begin';
    return url.includes('/playlist?') ? 'Playlist detected' : 'Video detected';
  }

  statusLabel(status: DownloadStatus): string {
    const labels: Record<DownloadStatus, string> = {
      QUEUED: 'In queue', DOWNLOADING: 'Downloading', PROCESSING: 'Processing',
      COMPLETED: 'Complete', FAILED: 'Failed', CANCELED: 'Canceled'
    };
    return labels[status];
  }

  statusClass(status: DownloadStatus): string {
    return status.toLowerCase();
  }

  finished(job: DownloadJob): boolean {
    return ['COMPLETED', 'FAILED', 'CANCELED'].includes(job.status);
  }


  trackJob(_: number, job: DownloadJob): string {
    return job.id;
  }

  private applyTheme(): void {
    document.documentElement.dataset['theme'] = this.darkTheme() ? 'dark' : 'light';
  }

  private showError(error: unknown, fallback: string): void {
    const message = error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
      ? error.error.message : fallback;
    this.showToast('error', message);
  }

  private showToast(kind: 'success' | 'error', message: string): void {
    this.toast.set({ kind, message });
    window.setTimeout(() => {
      if (this.toast()?.message === message) this.toast.set(null);
    }, 4200);
  }
}
