export type DownloadStatus = 'QUEUED' | 'DOWNLOADING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELED';

export interface DownloadJob {
  id: string;
  url: string;
  status: DownloadStatus;
  progress: number;
  title: string;
  speed: string;
  eta: string;
  currentFile: string;
  message: string;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  logs: string[];
}

export interface SystemStatus {
  ready: boolean;
  ytDlp: boolean;
  ffmpeg: boolean;
  script: boolean;
  defaultDirectory: string;
  message: string;
}

