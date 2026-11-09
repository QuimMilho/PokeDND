import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { problemText, toProblem } from '../core/api-error';
import { ExportApi, ExportJob, pdfName, saveBlob } from './export-api';

/** The person's earlier exports, newest first, with a download button while the file still exists (`/cards/jobs`). */
@Component({
  selector: 'app-export-jobs',
  imports: [DatePipe, RouterLink, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <main class="jobs">
      <h1 i18n="@@jobsTitle">Earlier exports</h1>
      @if (loading()) {
        <mat-spinner diameter="36" />
      } @else if (failure(); as text) {
        <p class="problem" role="alert">{{ text }}</p>
      } @else if (jobs().length === 0) {
        <p data-testid="jobs-empty" i18n="@@jobsEmpty">You have not exported any cards yet.</p>
      } @else {
        <ul data-testid="jobs-list">
          @for (j of jobs(); track j.id) {
            <li>
              <span>
                {{ j.createdAt | date: 'medium' }} ·
                @switch (j.state) {
                  @case ('done') {
                    <span i18n="@@jobsDone">{{ j.total }} cards, {{ j.pages }} pages</span>
                  }
                  @case ('failed') {
                    <span i18n="@@jobsFailed">failed</span>
                  }
                  @case ('expired') {
                    <span i18n="@@jobsExpired">expired</span>
                  }
                  @default {
                    <span i18n="@@jobsRunning">in progress</span>
                  }
                }
              </span>
              @if (j.state === 'done') {
                <button mat-stroked-button type="button" (click)="download(j)" i18n="@@jobsDownload">Download</button>
              }
            </li>
          }
        </ul>
      }
      <a mat-button routerLink="/cards/export" i18n="@@jobsNew">New export</a>
    </main>
  `,
  styles: `
    .jobs {
      box-sizing: border-box;
      max-width: 760px;
      margin: 0 auto;
      padding: 16px;
    }
    h1 {
      font: var(--mat-sys-headline-small);
    }
    ul {
      list-style: none;
      padding: 0;
    }
    li {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
      padding: 8px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .problem {
      color: var(--mat-sys-error);
    }
  `,
})
export class ExportJobs {
  private readonly api = inject(ExportApi);

  protected readonly jobs = signal<ExportJob[]>([]);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  constructor() {
    this.api.list().subscribe({
      next: (jobs) => {
        this.jobs.set(jobs);
        this.loading.set(false);
      },
      error: (e) => {
        this.failure.set(problemText(toProblem(e)));
        this.loading.set(false);
      },
    });
  }

  protected download(job: ExportJob): void {
    this.api.pdf(job).subscribe({
      next: (blob) => saveBlob(blob, pdfName(job)),
      error: (e) => this.failure.set(problemText(toProblem(e))),
    });
  }
}
