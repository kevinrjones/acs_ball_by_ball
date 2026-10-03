import {CommonModule} from '@angular/common';
import {Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, ParamMap, RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {EMPTY, Observable, of, switchMap, catchError} from 'rxjs';
import {parseMatchSearchQuery, serializeMatchSearchQuery} from '../domain/match-search-query.codec';
import {MatchService} from '../data/match.service';
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
  readonly publicMatchId = signal<string | null>(null);
  readonly isValidPublicMatchId = signal(false);
  readonly returnQueryParams = signal<Record<string, string>>({});
  readonly state = signal<ScoresheetState>('loading');
  readonly scoresheet = signal<PresentedScoresheet | null>(null);
  readonly activeInningsNumber = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);

  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly matchService = inject(MatchService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    const parameterMap$: Observable<ParamMap> =
      this.activatedRoute.paramMap ?? of(this.activatedRoute.snapshot.paramMap);
    parameterMap$.pipe(
      switchMap((paramMap) => this.loadRouteMatch(paramMap.get('publicMatchId'))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((response) => this.applyScoresheet(response.result));
  }

  retry(): void {
    const publicMatchId = this.publicMatchId();
    if (publicMatchId && this.isValidPublicMatchId()) {
      this.loadScoresheet(Number(publicMatchId));
    }
  }

  selectInnings(inningsNumber: number): void {
    if (this.scoresheet()?.innings.some((innings) => innings.inningsNumber === inningsNumber)) {
      this.activeInningsNumber.set(inningsNumber);
    }
  }

  inningsOrdinal(inningsNumber: number): string {
    const remainder = inningsNumber % 100;
    if (remainder >= 11 && remainder <= 13) {
      return `${inningsNumber}th`;
    }
    switch (inningsNumber % 10) {
      case 1:
        return `${inningsNumber}st`;
      case 2:
        return `${inningsNumber}nd`;
      case 3:
        return `${inningsNumber}rd`;
      default:
        return `${inningsNumber}th`;
    }
  }

  onInningsCardKeydown(event: KeyboardEvent, inningsNumber: number): void {
    const innings = this.scoresheet()?.innings ?? [];
    const currentIndex = innings.findIndex((current) => current.inningsNumber === inningsNumber);
    if (currentIndex < 0) {
      return;
    }

    const nextIndex = this.nextTabIndex(event.key, currentIndex, innings.length);
    if (nextIndex === null) {
      return;
    }

    event.preventDefault();
    this.selectInnings(innings[nextIndex].inningsNumber);
    const cards = (event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('.scoresheet-innings-card');
    cards?.[nextIndex]?.focus();
  }

  private loadScoresheet(publicMatchId: number): void {
    this.loadRouteMatch(String(publicMatchId)).subscribe((response) => this.applyScoresheet(response.result));
  }

  private loadRouteMatch(rawPublicMatchId: string | null) {
    this.configureRouteState(rawPublicMatchId);
    if (!this.isValidPublicMatchId()) {
      return EMPTY;
    }
    this.state.set('loading');
    this.errorMessage.set(null);
    return this.matchService.getScoresheet(Number(rawPublicMatchId)).pipe(
      catchError((error: {status?: number; error?: {errorMessage?: string; message?: string}; message?: string}) => {
        this.handleScoresheetError(error);
        return EMPTY;
      })
    );
  }

  private configureRouteState(rawPublicMatchId: string | null): void {
    const parsedQuery = parseMatchSearchQuery(this.activatedRoute.snapshot.queryParamMap);
    this.returnQueryParams.set(parsedQuery.query ? serializeMatchSearchQuery(parsedQuery.query) : {});
    this.publicMatchId.set(rawPublicMatchId);
    const isValid = rawPublicMatchId !== null && /^[1-9]\d{9}$/.test(rawPublicMatchId);
    this.isValidPublicMatchId.set(isValid);
    this.scoresheet.set(null);
    this.activeInningsNumber.set(null);
    if (!isValid) {
      this.state.set('error');
      this.errorMessage.set('The selected public match ID is invalid.');
    }
  }

  private applyScoresheet(response: Parameters<typeof presentScoresheet>[0]): void {
    const presentedScoresheet = presentScoresheet(response);
    this.scoresheet.set(presentedScoresheet);
    this.activeInningsNumber.set(presentedScoresheet.innings[0]?.inningsNumber ?? null);
    this.state.set('results');
  }

  private handleScoresheetError(error: {status?: number; error?: {errorMessage?: string; message?: string}; message?: string}): void {
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

  private nextTabIndex(key: string, currentIndex: number, tabCount: number): number | null {
    if (tabCount === 0) {
      return null;
    }
    if (key === 'Home') {
      return 0;
    }
    if (key === 'End') {
      return tabCount - 1;
    }
    if (key === 'ArrowRight') {
      return (currentIndex + 1) % tabCount;
    }
    if (key === 'ArrowLeft') {
      return (currentIndex - 1 + tabCount) % tabCount;
    }
    return null;
  }
}