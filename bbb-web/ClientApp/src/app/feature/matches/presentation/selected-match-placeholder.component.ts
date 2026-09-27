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
  readonly dismissed: boolean;
  readonly dismissalRuns: number | null;
  readonly dismissalBalls: number | null;
  readonly dismissalFours: number | null;
  readonly dismissalSixes: number | null;
}

export interface ScoresheetLedger {
  readonly runs: number;
  readonly wickets: number;
}

interface BattingLaneAssignment {
  readonly lane: number;
  readonly player: string;
}

interface BattingScore {
  readonly runs: number;
  readonly balls: number;
  readonly fours: number;
  readonly sixes: number;
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
    const descriptions = delivery.wickets.map((wicket) => {
      const fielders = wicket.fielders.length > 0 ? ` (${wicket.fielders.join(', ')})` : '';
      const kind = wicket.kind?.trim() || 'Wicket';
      return `${kind.charAt(0).toUpperCase()}${kind.slice(1)}${fielders}`;
    });
    return descriptions.length > 0 ? descriptions.join('; ') : delivery.wicketCount > 0 ? 'Wicket' : '';
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

  battingPair(innings: ScoresheetInnings, row: ScoresheetOverRow): ScoresheetPlayerNotation[][] {
    const assignments = this.battingLaneAssignments(innings);
    const dismissalScores = this.battingScoresAtDismissal(innings);
    return [0, 1].map((lane) => {
      const players = Array.from(new Set(
        row.deliveries
          .flatMap((delivery) => assignments.get(delivery.deliveryKey) || [])
          .filter((assignment) => assignment.lane === lane)
          .map((assignment) => assignment.player)
      ));

      return players.map((player) => {
        const strikerDeliveries = row.deliveries.filter((delivery) =>
          assignments.get(delivery.deliveryKey)?.some((assignment) => assignment.player === player) &&
          delivery.batter?.trim() === player
        );
        const dismissalScore = dismissalScores.get(player);
        return {
          player,
          deliveries: row.deliveries,
          runs: strikerDeliveries.reduce((total, delivery) => total + delivery.batterRuns, 0),
          balls: strikerDeliveries.filter((delivery) => delivery.wides === 0).length,
          dismissed: strikerDeliveries.some((delivery) => delivery.wicketCount > 0),
          dismissalRuns: dismissalScore?.runs ?? null,
          dismissalBalls: dismissalScore?.balls ?? null,
          dismissalFours: dismissalScore?.fours ?? null,
          dismissalSixes: dismissalScore?.sixes ?? null
        };
      });
    });
  }

  dismissalSummary(entry: ScoresheetPlayerNotation): string {
    const boundaries = [
      entry.dismissalFours ? `${entry.dismissalFours}x4` : '',
      entry.dismissalSixes ? `${entry.dismissalSixes}x6` : ''
    ].filter(Boolean).join(', ');
    return `(${entry.dismissalRuns}r, ${entry.dismissalBalls}b${boundaries ? `, ${boundaries}` : ''})`;
  }

  playerNotationsAt(innings: ScoresheetInnings, row: ScoresheetOverRow, index: number): ScoresheetPlayerNotation[] {
    return this.battingPair(innings, row)[index] || [];
  }

  private battingLaneAssignments(innings: ScoresheetInnings): Map<number, BattingLaneAssignment[]> {
    const lanes: Array<string | undefined> = [undefined, undefined];
    const playerLanes = new Map<string, number>();
    const assignments = new Map<number, BattingLaneAssignment[]>();
    const deliveries = [...innings.deliveries].sort((left, right) =>
      left.inningsOrder - right.inningsOrder || left.deliveryKey - right.deliveryKey
    );

    deliveries.forEach((delivery) => {
      const players = Array.from(new Set([
        delivery.batter?.trim() || 'Unknown batter',
        delivery.nonStriker?.trim() || 'Unknown non-striker'
      ]));
      players.forEach((player) => {
        if (!playerLanes.has(player)) {
          const lane = lanes.indexOf(undefined);
          if (lane >= 0) {
            lanes[lane] = player;
            playerLanes.set(player, lane);
          }
        }
      });

      assignments.set(delivery.deliveryKey, players.flatMap((player) => {
        const lane = playerLanes.get(player);
        return lane === undefined ? [] : [{lane, player}];
      }));

      const batter = players[0];
      const batterLane = playerLanes.get(batter);
      if (delivery.wicketCount > 0 && batterLane !== undefined) {
        lanes[batterLane] = undefined;
        playerLanes.delete(batter);
      }
    });

    return assignments;
  }

  private battingScoresAtDismissal(innings: ScoresheetInnings): Map<string, BattingScore> {
    const scores = new Map<string, BattingScore>();
    const dismissalScores = new Map<string, BattingScore>();
    const deliveries = [...innings.deliveries].sort((left, right) =>
      left.inningsOrder - right.inningsOrder || left.deliveryKey - right.deliveryKey
    );

    deliveries.forEach((delivery) => {
      const player = delivery.batter?.trim();
      if (!player) {
        return;
      }

      const previous = scores.get(player) || {runs: 0, balls: 0, fours: 0, sixes: 0};
      const score = {
        runs: previous.runs + delivery.batterRuns,
        balls: previous.balls + (delivery.wides === 0 ? 1 : 0),
        fours: previous.fours + (delivery.batterRuns === 4 ? 1 : 0),
        sixes: previous.sixes + (delivery.batterRuns === 6 ? 1 : 0)
      };
      scores.set(player, score);

      if (delivery.wicketCount > 0 && !dismissalScores.has(player)) {
        dismissalScores.set(player, score);
      }
    });

    return dismissalScores;
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
    const wicketNotes = row.deliveries.flatMap((delivery) => {
      const description = this.wicketDescription(delivery);
      return description
        ? description.split('; ').map((wicket) => `${delivery.batter?.trim() || 'Unknown batter'} — ${wicket}`)
        : [];
    });
    if (wicketNotes.length > 0) {
      notes.push(...wicketNotes.map((wicket, index) => `WICKET ${index + 1}: ${wicket}`));
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