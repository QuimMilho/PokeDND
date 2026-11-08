import { Component, LOCALE_ID, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet, MatToolbarModule, MatButtonModule, MatCardModule],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  /** The language this build was made for ("en" or "pt"). */
  protected readonly language = inject(LOCALE_ID).slice(0, 2);
}
