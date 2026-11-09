import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, switchMap, takeWhile, timer } from 'rxjs';
import { API_BASE } from '../core/api-config';

/** What the export screen asks for (`ExportRequest` in the API; every part is optional). */
export interface ExportRequest {
  generations?: number[];
  types?: string[];
  primaryTypeOnly?: boolean;
  /** Card keys such as `0006_charizard`: the manual selection. */
  cards?: string[];
  includeForms?: boolean;
  backs?: boolean;
  /** For example `63x88` (millimetres). */
  size?: string;
  paper?: 'A4' | 'LETTER';
  language?: 'en' | 'pt';
  colour?: 'full' | 'ink-saving';
  copies?: number;
  showBackId?: boolean;
  bleedMm?: number;
  gapMm?: number;
}

export type JobState = 'queued' | 'running' | 'done' | 'failed' | 'expired';

/** An export job, as the API shows it (`JobDto`). */
export interface ExportJob {
  id: string;
  state: JobState;
  progress: number;
  total: number;
  message: string | null;
  warnings: string[];
  pages: number | null;
  createdAt: string;
  expiresAt: string | null;
  downloadUrl: string | null;
  manifestUrl: string | null;
  request: ExportRequest | null;
  options: Record<string, unknown> | null;
}

/** How often a running job is asked for its progress, in milliseconds (the tests make it shorter). */
export const EXPORT_POLL_MS = new InjectionToken<number>('EXPORT_POLL_MS', { providedIn: 'root', factory: () => 1000 });

/** A job that will not change any more. */
export function isFinished(job: Pick<ExportJob, 'state'>): boolean {
  return job.state === 'done' || job.state === 'failed' || job.state === 'expired';
}

/** Card export jobs (chapter 9.6 and 18.1). Anyone signed in may use them, and sees only their own jobs. */
@Injectable({ providedIn: 'root' })
export class ExportApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);
  private readonly pollMs = inject(EXPORT_POLL_MS);

  start(request: ExportRequest): Observable<ExportJob> {
    return this.http.post<ExportJob>(`${this.base}/cards/exports`, request);
  }

  get(id: string): Observable<ExportJob> {
    return this.http.get<ExportJob>(`${this.base}/cards/exports/${id}`);
  }

  list(limit = 25): Observable<ExportJob[]> {
    return this.http.get<ExportJob[]>(`${this.base}/cards/exports`, { params: { limit } });
  }

  /** Asks for the job every `everyMs` milliseconds until it is finished (the last answer is included). */
  watch(id: string, everyMs = this.pollMs): Observable<ExportJob> {
    return timer(0, everyMs).pipe(
      switchMap(() => this.get(id)),
      takeWhile((job) => !isFinished(job), true),
    );
  }

  /** The PDF (`downloadUrl` of a finished job). The call carries the access token, so it is made here and not by a plain link. */
  pdf(job: ExportJob): Observable<Blob> {
    return this.http.get(job.downloadUrl ?? `${this.base}/cards/exports/${job.id}/download`, { responseType: 'blob' });
  }

  manifest(job: ExportJob): Observable<Blob> {
    return this.http.get(job.manifestUrl ?? `${this.base}/cards/exports/${job.id}/manifest`, { responseType: 'blob' });
  }
}

/** Gives the browser a file to save. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // the browser needs a moment before the address can be let go
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** `pokednd-cards-1a2b3c4d.pdf` - the same name the API suggests. */
export function pdfName(job: Pick<ExportJob, 'id'>): string {
  return `pokednd-cards-${job.id.slice(0, 8)}.pdf`;
}
