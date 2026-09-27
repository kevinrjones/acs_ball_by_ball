import {CommonModule} from '@angular/common';
import {Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {parseMatchSearchQuery, serializeMatchSearchQuery} from '../../../models/match-search-query.codec';
import {MatchService} from '../../../services/match.service';
import {presentScoresheet, PresentedScoresheet} from './scoresheet-presenter';

export type ScoresheetState = 'loading' | 'results' | 'not-found' | 'error';

@Component({
  selector: 'app-selected-match',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './selected-match-placeholder.component.html',
  styleUrl: './selected-match-placeholder.component.css'
})
export class SelectedMatchPlaceholderComponent implements OnInit {
  readonly matchKey = signal<string | null>(null);
  readonly isValidMatchKey = signal(false);
  readonly returnQueryParams = signal<Record<string, string>>({});
  readonly state = signal<ScoresheetState>('loading');
  readonly scoresheet = signal<PresentedScoresheet | null>(null);
  readonly errorMessage = signal<string | null>(null);

  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly matchService = inject(MatchService);

  ngOnInit(): void {
    const rawMatchKey = this.activatedRoute.snapshot.paramMap.get('matchKey');
    const query = this.activatedRoute.snapshot.queryParamMap;
    const parsedQuery = parseMatchSearchQuery(query);
    this.returnQueryParams.set(parsedQuery.query ? serializeMatchSearchQuery(parsedQuery.query) : {});
    this.matchKey.set(rawMatchKey);
    const isValid = rawMatchKey !== null && /^[1-9]\d*$/.test(rawMatchKey);
    this.isValidMatchKey.set(isValid);
    if (isValid) {
      this.loadScoresheet(Number(rawMatchKey));
    } else {
      this.state.set('error');
      this.errorMessage.set('The selected match key is invalid.');
    }
  }

  retry(): void {
    const key = this.matchKey();
    if (key && this.isValidMatchKey()) {
      this.loadScoresheet(Number(key));
    }
  }

  private loadScoresheet(matchKey: number): void {
    this.state.set('loading');
    this.errorMessage.set(null);
    this.matchService.getScoresheet(matchKey).subscribe({
      next: (response) => {
        this.scoresheet.set(presentScoresheet(response.result));
        this.state.set('results');
      },
      error: (error: {status?: number; error?: {errorMessage?: string; message?: string}; message?: string}) => {
        if (error.status === 404) {
          this.state.set('not-found');
          this.errorMessage.set('The requested match was not found.');
        } else {
          this.state.set('error');
          this.errorMessage.set(
            error?.error?.errorMessage || error?.error?.message || error?.message || 'The scoresheet is currently unavailable.'
          );
        }
      }
    });
  }
}