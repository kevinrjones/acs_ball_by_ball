import {CommonModule} from '@angular/common';
import {Component, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {Subscription} from 'rxjs';
import {MatchSearchQuery, MatchSearchResult} from '../../../models/match.model';
import {parseMatchSearchQuery, serializeMatchSearchQuery} from '../../../models/match-search-query.codec';
import {MatchService} from '../../../services/match.service';

export type MatchSearchState = 'idle' | 'loading' | 'results' | 'no-results' | 'error';

@Component({
  selector: 'app-match-results',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './match-results.component.html'
})
export class MatchResultsComponent implements OnInit, OnDestroy {
  readonly state = signal<MatchSearchState>('idle');
  readonly searchDescription = signal<string | null>(null);
  readonly matches = signal<MatchSearchResult[]>([]);
  readonly totalResults = signal(0);
  readonly currentPage = signal(1);
  readonly pageSize = signal(20);
  readonly hasNext = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly matchService = inject(MatchService);
  private routeSubscription?: Subscription;
  private searchSubscription?: Subscription;
  private currentQuery: MatchSearchQuery | null = null;

  ngOnInit(): void {
    this.routeSubscription = this.activatedRoute.queryParamMap.subscribe((params) => {
      const parsedQuery = parseMatchSearchQuery(params);
      if (parsedQuery.error) {
        this.currentQuery = null;
        this.searchDescription.set(null);
        this.matches.set([]);
        this.errorMessage.set(parsedQuery.error);
        this.state.set('error');
        return;
      }
      const searchQuery = parsedQuery.query;
      if (!searchQuery) {
        this.currentQuery = null;
        this.searchDescription.set(null);
        this.matches.set([]);
        this.totalResults.set(0);
        this.hasNext.set(false);
        this.errorMessage.set(null);
        this.state.set('idle');
        return;
      }
      this.currentQuery = searchQuery;
      this.searchDescription.set(`${searchQuery.team} v ${searchQuery.opponents}`);
      this.currentPage.set(searchQuery.page);
      this.pageSize.set(searchQuery.pageSize);
      this.loadMatches(searchQuery);
    });
  }

  ngOnDestroy(): void {
    this.routeSubscription?.unsubscribe();
    this.searchSubscription?.unsubscribe();
  }

  queryParams(): Record<string, string | number> {
    return this.currentQuery ? serializeMatchSearchQuery(this.currentQuery) : {};
  }

  retrySearch(): void {
    if (this.currentQuery) {
      this.loadMatches(this.currentQuery);
    }
  }

  goToPage(page: number): void {
    if (this.currentQuery && page >= 1 && (page !== this.currentQuery.page || page === 1)) {
      void this.router.navigate(['/matches/results'], {
        queryParams: serializeMatchSearchQuery({...this.currentQuery, page})
      });
    }
  }

  private loadMatches(searchQuery: MatchSearchQuery): void {
    this.searchSubscription?.unsubscribe();
    this.state.set('loading');
    this.errorMessage.set(null);
    this.searchSubscription = this.matchService.searchMatches(searchQuery).subscribe({
      next: (response) => {
        const matches = response.result.matches;
        this.matches.set(matches);
        this.totalResults.set(response.result.pagination.totalResults);
        this.currentPage.set(response.result.pagination.page);
        this.pageSize.set(response.result.pagination.pageSize);
        this.hasNext.set(response.result.pagination.hasNext);
        this.state.set(matches.length > 0 ? 'results' : 'no-results');
      },
      error: (error) => {
        this.errorMessage.set(this.getErrorMessage(error));
        this.state.set('error');
      }
    });
  }


  private getErrorMessage(error: {error?: {errorMessage?: string; message?: string}; message?: string}): string {
    return error?.error?.errorMessage || error?.error?.message || error?.message || 'The API is currently unavailable.';
  }
}