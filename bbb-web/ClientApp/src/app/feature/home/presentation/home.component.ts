import {CommonModule} from '@angular/common';
import {Component, inject, OnInit, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {MatchSummary} from '../../../models/match.model';
import {AuthenticationService} from '../../../services/authentication.service';
import {ApplicationMetadataService} from '../../../services/application-metadata.service';
import {MatchService} from '../../../services/match.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
  private readonly matchService = inject(MatchService);
  private readonly applicationMetadataService = inject(ApplicationMetadataService);
  readonly authService = inject(AuthenticationService);

  readonly matches = signal<MatchSummary[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly hasLoaded = signal(false);

  ngOnInit(): void {
    this.applicationMetadataService.loadMetadata();
    this.loadRecentMatches();
  }

  loadRecentMatches(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.matchService.getRecentMatches(10).subscribe({
      next: (response) => {
        this.matches.set(response.result.matches);
        this.hasLoaded.set(true);
        this.isLoading.set(false);
      },
      error: (error) => {
        const errorDetail = error?.error?.errorMessage || error?.error?.message || error?.message || 'The API is currently unavailable.';
        this.errorMessage.set(`Failed to load matches: ${errorDetail}`);
        this.isLoading.set(false);
      }
    });
  }

  getBadgeClass(format?: string): string {
    const normalizedFormat = (format || '').toLowerCase();
    if (normalizedFormat.includes('women')) return 'text-rose-900 bg-rose-50 border-rose-200/70';
    if (normalizedFormat.includes('t20')) return 'text-emerald-800 bg-emerald-50 border-emerald-100';
    if (normalizedFormat.includes('fc') || normalizedFormat.includes('test')) return 'text-indigo-900 bg-indigo-50 border-indigo-200';
    if (normalizedFormat.includes('odi') || normalizedFormat.includes('lista')) return 'text-blue-900 bg-blue-50 border-blue-200';
    return 'text-slate-700 bg-slate-100 border-slate-200';
  }

  get latestDateRange(): string {
    const dates = this.matches()
      .map((match) => match.date)
      .filter((date): date is string => typeof date === 'string' && date.length > 0 && date !== 'MISSING');
    if (this.hasLoaded() && dates.length > 0) {
      const first = dates[0];
      const last = dates[dates.length - 1];
      return first === last ? first : `${last} - ${first}`;
    }
    return 'Last 10 Days';
  }
}