import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DownloadJob, SystemStatus } from './models';

@Injectable({ providedIn: 'root' })
export class DownloadService {
  readonly jobs = signal<DownloadJob[]>([]);
  readonly connected = signal(false);
  readonly system = signal<SystemStatus | null>(null);
  private events?: EventSource;

  constructor(private readonly http: HttpClient) {}

  async initialize(): Promise<void> {
    const [system, jobs] = await Promise.all([
      firstValueFrom(this.http.get<SystemStatus>('/api/system/status')),
      firstValueFrom(this.http.get<DownloadJob[]>('/api/downloads'))
    ]);
    this.system.set(system);
    this.jobs.set(jobs);
    this.connectEvents();
  }

  start(url: string): Promise<DownloadJob> {
    return firstValueFrom(this.http.post<DownloadJob>('/api/downloads', { url }));
  }

  cancel(id: string): Promise<DownloadJob> {
    return firstValueFrom(this.http.post<DownloadJob>(`/api/downloads/${id}/cancel`, {}));
  }

  retry(id: string): Promise<DownloadJob> {
    return firstValueFrom(this.http.post<DownloadJob>(`/api/downloads/${id}/retry`, {}));
  }

  async clearFinished(): Promise<void> {
    await firstValueFrom(this.http.delete('/api/downloads/finished'));
    this.jobs.update(jobs => jobs.filter(job => !this.isFinished(job)));
  }

  private connectEvents(): void {
    this.events?.close();
    this.events = new EventSource('/api/downloads/events');
    this.events.addEventListener('open', () => this.connected.set(true));
    this.events.addEventListener('error', () => this.connected.set(false));
    this.events.addEventListener('jobs', event => {
      this.jobs.set(JSON.parse((event as MessageEvent<string>).data) as DownloadJob[]);
    });
    this.events.addEventListener('job', event => {
      const changed = JSON.parse((event as MessageEvent<string>).data) as DownloadJob;
      const previous = this.jobs().find(job => job.id === changed.id);
      this.jobs.update(jobs => [changed, ...jobs.filter(job => job.id !== changed.id)]
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
      if (previous && !this.isFinished(previous) && this.isFinished(changed)) this.notify(changed);
    });
  }

  private notify(job: DownloadJob): void {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    const success = job.status === 'COMPLETED';
    new Notification(success ? 'Download complete' : `Download ${job.status.toLowerCase()}`, {
      body: job.title || job.message,
      tag: job.id
    });
  }

  private isFinished(job: DownloadJob): boolean {
    return ['COMPLETED', 'FAILED', 'CANCELED'].includes(job.status);
  }
}
