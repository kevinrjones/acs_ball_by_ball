import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatchService } from './services/match.service';
import { MatchSummary } from './models/match.model';
import { AuthenticationService, UserProfileResponse } from './services/authentication.service';
import { ApplicationMetadataService } from './services/application-metadata.service';
import { ApplicationMetadata } from './models/application-metadata.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  private readonly matchService = inject(MatchService);
  private readonly applicationMetadataService = inject(ApplicationMetadataService);
  public readonly authService = inject(AuthenticationService);

  readonly matches = signal<MatchSummary[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly hasLoaded = signal<boolean>(false);

  readonly userProfile = signal<UserProfileResponse | null>(null);
  readonly isProfileLoading = signal<boolean>(false);
  readonly profileError = signal<string | null>(null);
  readonly applicationMetadata = signal<ApplicationMetadata | null>(null);

  ngOnInit(): void {
    this.loadRecentMatches();
    this.loadApplicationMetadata();
  }

  loadApplicationMetadata(): void {
    this.applicationMetadataService.getMetadata().subscribe({
      next: (response) => this.applicationMetadata.set(response.result),
      error: () => this.applicationMetadata.set(null)
    });
  }

  getBadgeClass(format?: string): string {
    const f = (format || '').toLowerCase();
    if (f.includes('women')) return 'text-rose-900 bg-rose-50 border-rose-200/70';
    if (f.includes('t20')) return 'text-emerald-800 bg-emerald-50 border-emerald-100';
    if (f.includes('fc') || f.includes('test')) return 'text-indigo-900 bg-indigo-50 border-indigo-200';
    if (f.includes('odi') || f.includes('lista')) return 'text-blue-900 bg-blue-50 border-blue-200';
    return 'text-slate-700 bg-slate-100 border-slate-200';
  }

  get latestDateRange(): string {
    if (this.hasLoaded() && this.matches().length > 0) {
      const dates = this.matches()
        .map(m => m.date)
        .filter((d): d is string => typeof d === 'string' && d.length > 0 && d !== 'MISSING');
      if (dates.length > 0) {
        const first = dates[0];
        const last = dates[dates.length - 1];
        return first === last ? first : `${last} - ${first}`;
      }
    }
    return 'Last 10 Days';
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
      error: (err) => {
        const errorDetail = err?.error?.errorMessage || err?.error?.message || err?.message || 'The API is currently unavailable.';
        this.errorMessage.set(`Failed to load matches: ${errorDetail}`);
        this.isLoading.set(false);
      }
    });
  }

  loadUserProfile(): void {
    this.isProfileLoading.set(true);
    this.profileError.set(null);

    this.authService.getUserProfile().subscribe({
      next: (response) => {
        this.userProfile.set(response.result);
        this.isProfileLoading.set(false);
      },
      error: (err) => {
        const errorDetail = err?.error?.errorMessage || err?.error?.message || err?.message || 'Unable to load profile.';
        this.profileError.set(`Profile access rejected: ${errorDetail}`);
        this.isProfileLoading.set(false);
      }
    });
  }
}
