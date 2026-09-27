import {CommonModule} from '@angular/common';
import {Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {
  MatchScoresheetResponse,
  ScoresheetDelivery,
  ScoresheetInnings
} from '../../../models/match.model';
import {parseMatchSearchQuery, serializeMatchSearchQuery} from '../../../models/match-search-query.codec';
import {MatchService} from '../../../services/match.service';

export type ScoresheetState = 'loading' | 'results' | 'not-found' | 'error';

export interface ScoresheetOverRow {
  readonly overNumber: number;
  readonly deliveries: readonly ScoresheetDelivery[];
  readonly totalRuns: number;
  readonly wicketCount: number;
  readonly boundaryCount: number;
  readonly extras: string;
}

export interface ScoresheetPlayerNotation {
  readonly player: string;
  readonly deliveries: readonly ScoresheetDelivery[];
  readonly runs: number;
  readonly balls: number;
}

export interface ScoresheetLedger {
  readonly runs: number;
  readonly wickets: number;
}

@Component({
  selector: 'app-selected-match',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './selected-match-placeholder.component.html'
})
export class SelectedMatchPlaceholderComponent implements OnInit {
  readonly matchKey = signal<string | null>(null);
  readonly isValidMatchKey = signal(false);
  readonly returnQueryParams = signal<Record<string, string>>({});
  readonly state = signal<ScoresheetState>('loading');
  readonly scoresheet = signal<MatchScoresheetResponse | null>(null);
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

  extrasDescription(delivery: ScoresheetDelivery): string {
    return [
      delivery.wides > 0 ? 'wd' : '',
      delivery.noBalls > 0 ? 'nb' : '',
      delivery.byes > 0 ? 'b' : '',
      delivery.legByes > 0 ? 'lb' : ''
    ].filter(Boolean).join(' ');
  }

  wicketDescription(delivery: ScoresheetDelivery): string {
    return delivery.wickets.map((wicket) => {
      const fielders = wicket.fielders.length > 0 ? ` (${wicket.fielders.join(', ')})` : '';
      return `${wicket.kind || 'Wicket'}${fielders}`;
    }).join('; ');
  }

  overRows(innings: ScoresheetInnings): ScoresheetOverRow[] {
    const deliveriesByOver = new Map<number, ScoresheetDelivery[]>();
    innings.deliveries.forEach((delivery) => {
      const deliveries = deliveriesByOver.get(delivery.overNumber) || [];
      deliveries.push(delivery);
      deliveriesByOver.set(delivery.overNumber, deliveries);
    });

    return Array.from(deliveriesByOver.entries())
      .sort(([left], [right]) => left - right)
      .map(([overNumber, deliveries]) => ({
        overNumber,
        deliveries,
        totalRuns: deliveries.reduce((total, delivery) => total + delivery.totalRuns, 0),
        wicketCount: deliveries.reduce((total, delivery) => total + delivery.wicketCount, 0),
        boundaryCount: deliveries.filter((delivery) => delivery.batterRuns === 4 || delivery.batterRuns === 6).length,
        extras: this.overExtras(deliveries)
      }));
  }

  battingPair(row: ScoresheetOverRow): ScoresheetPlayerNotation[] {
    const players = new Set<string>();
    row.deliveries.forEach((delivery) => {
      players.add(delivery.batter?.trim() || 'Unknown batter');
      players.add(delivery.nonStriker?.trim() || 'Unknown non-striker');
    });

    return Array.from(players).slice(0, 2).map((player) => {
      const strikerDeliveries = row.deliveries.filter((delivery) => delivery.batter?.trim() === player);
      return {
        player,
        deliveries: row.deliveries,
        runs: strikerDeliveries.reduce((total, delivery) => total + delivery.batterRuns, 0),
        balls: strikerDeliveries.filter((delivery) => delivery.wides === 0 && delivery.noBalls === 0).length
      };
    });
  }

  playerNotationAt(row: ScoresheetOverRow, index: number): ScoresheetPlayerNotation | undefined {
    return this.battingPair(row)[index];
  }

  bowlerSummaries(row: ScoresheetOverRow): string[] {
    const deliveriesByBowler = new Map<string, ScoresheetDelivery[]>();
    row.deliveries.forEach((delivery) => {
      const bowler = delivery.bowler?.trim() || 'Unknown bowler';
      const deliveries = deliveriesByBowler.get(bowler) || [];
      deliveries.push(delivery);
      deliveriesByBowler.set(bowler, deliveries);
    });

    return Array.from(deliveriesByBowler.entries()).map(([bowler, deliveries]) => {
      const runs = deliveries.reduce((total, delivery) => total + delivery.totalRuns, 0);
      const wickets = deliveries.reduce((total, delivery) => total + delivery.wicketCount, 0);
      return `${bowler} · ${deliveries.length} balls · ${runs} runs · ${wickets} wkts`;
    });
  }

  runningLedger(innings: ScoresheetInnings, overNumber: number): ScoresheetLedger {
    return this.overRows(innings)
      .filter((row) => row.overNumber <= overNumber)
      .reduce((ledger, row) => ({
        runs: ledger.runs + row.totalRuns,
        wickets: ledger.wickets + row.wicketCount
      }), {runs: 0, wickets: 0});
  }

  sourceBallRange(row: ScoresheetOverRow): string {
    const sourceBallIds = row.deliveries.map((delivery) => delivery.sourceBallId);
    const first = sourceBallIds[0];
    const last = sourceBallIds[sourceBallIds.length - 1];
    return first === last ? `#${first}` : `#${first}–#${last}`;
  }

  deliverySymbol(delivery: ScoresheetDelivery): string {
    if (delivery.wicketCount > 0) {
      return 'W';
    }
    if (delivery.wides > 0 || delivery.noBalls > 0) {
      return '+';
    }
    if (delivery.byes > 0 || delivery.legByes > 0) {
      return 'Δ';
    }
    if (delivery.totalRuns === 0) {
      return '•';
    }
    return delivery.totalRuns.toString();
  }

  deliverySymbolClass(delivery: ScoresheetDelivery): string {
    if (delivery.wicketCount > 0) {
      return 'matrix-symbol--wicket';
    }
    if (delivery.wides > 0 || delivery.noBalls > 0) {
      return 'matrix-symbol--extra';
    }
    if (delivery.batterRuns === 4 || delivery.batterRuns === 6) {
      return 'matrix-symbol--boundary';
    }
    return delivery.totalRuns === 0 ? 'matrix-symbol--dot' : 'matrix-symbol--run';
  }

  overNotes(row: ScoresheetOverRow): string {
    const notes: string[] = [];
    const wickets = row.deliveries.flatMap((delivery) => delivery.wickets.map((wicket) => wicket.kind || 'Wicket'));
    if (wickets.length > 0) {
      notes.push(`${wickets.length} wicket${wickets.length === 1 ? '' : 's'}: ${wickets.join(', ')}`);
    }
    if (row.extras !== '—') {
      notes.push(row.extras);
    }
    if (row.boundaryCount > 0) {
      notes.push(`${row.boundaryCount} boundary${row.boundaryCount === 1 ? '' : 'ies'}`);
    }
    return notes.join(' · ') || 'No wicket or extra events recorded';
  }

  sumDeliveryRuns(total: number, delivery: ScoresheetDelivery): number {
    return total + delivery.totalRuns;
  }

  totalDeliveries(response: MatchScoresheetResponse): number {
    return response.innings.reduce((total, innings) => total + innings.deliveries.length, 0);
  }

  private overExtras(deliveries: readonly ScoresheetDelivery[]): string {
    const byes = deliveries.reduce((total, delivery) => total + delivery.byes, 0);
    const legByes = deliveries.reduce((total, delivery) => total + delivery.legByes, 0);
    const wides = deliveries.reduce((total, delivery) => total + delivery.wides, 0);
    const noBalls = deliveries.reduce((total, delivery) => total + delivery.noBalls, 0);
    const parts = [
      byes > 0 ? `B:${byes}` : '',
      legByes > 0 ? `LB:${legByes}` : '',
      wides > 0 ? `W:${wides}` : '',
      noBalls > 0 ? `NB:${noBalls}` : ''
    ].filter(Boolean);
    return parts.length > 0 ? parts.join(' ') : '—';
  }

  private loadScoresheet(matchKey: number): void {
    this.state.set('loading');
    this.errorMessage.set(null);
    this.matchService.getScoresheet(matchKey).subscribe({
      next: (response) => {
        this.scoresheet.set(response.result);
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