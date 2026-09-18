import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatchService } from './services/match.service';
import { MatchSummary } from './models/match.model';
import { AuthenticationService, UserProfileResponse } from './services/authentication.service';
import { SAMPLE_MATCHES, SampleMatch } from './fixtures/sample-matches.fixture';

export type { SampleMatch };

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  private readonly matchService = inject(MatchService);
  public readonly authService = inject(AuthenticationService);

  readonly matches = signal<MatchSummary[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly hasLoaded = signal<boolean>(false);

  readonly userProfile = signal<UserProfileResponse | null>(null);
  readonly isProfileLoading = signal<boolean>(false);
  readonly profileError = signal<string | null>(null);

  readonly sampleMatches: SampleMatch[] = SAMPLE_MATCHES;

  ngOnInit(): void {
    // Optionally load recent matches on start or leave manual trigger
  }

  loadRecentMatches(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.matchService.getRecentMatches(8).subscribe({
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
