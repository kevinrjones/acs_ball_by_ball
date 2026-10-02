import {ScoresheetDelivery} from '../domain/match.model';

export interface ScoresheetBowlerSummary {
  readonly bowler: string;
  readonly overs: string;
  readonly maidens: number;
  readonly runs: number;
  readonly wickets: number;
  readonly isFirstAppearance: boolean;
}

export interface BowlerFigures {
  readonly legalBalls: number;
  readonly maidens: number;
  readonly runs: number;
  readonly wickets: number;
}

export function bowlingLanes(
  deliveries: readonly ScoresheetDelivery[],
  overNumber: number,
  figuresByBowler: Map<string, BowlerFigures>,
  displayedBowlers: Set<string>
): readonly [readonly ScoresheetBowlerSummary[], readonly ScoresheetBowlerSummary[]] {
  const deliveriesByBowler = new Map<string, ScoresheetDelivery[]>();
  deliveries.forEach((delivery) => {
    const bowler = delivery.bowler?.trim() || 'Unknown bowler';
    const bowlerDeliveries = deliveriesByBowler.get(bowler) || [];
    bowlerDeliveries.push(delivery);
    deliveriesByBowler.set(bowler, bowlerDeliveries);
  });
  const summaries = Array.from(deliveriesByBowler.entries()).map(([bowler, bowlerDeliveries]) =>
    cumulativeBowlerSummary(bowler, bowlerDeliveries, figuresByBowler, displayedBowlers)
  );
  const lane = overNumber % 2 === 0 ? 1 : 0;
  const lanes: [ScoresheetBowlerSummary[], ScoresheetBowlerSummary[]] = [[], []];
  lanes[lane].push(...summaries);
  return lanes;
}

function cumulativeBowlerSummary(
  bowler: string,
  deliveries: readonly ScoresheetDelivery[],
  figuresByBowler: Map<string, BowlerFigures>,
  displayedBowlers: Set<string>
): ScoresheetBowlerSummary {
  const previous = figuresByBowler.get(bowler) || emptyBowlerFigures();
  const legalBalls = deliveries.filter(isLegalDelivery).length;
  const runs = deliveries.reduce((total, delivery) => total + bowlerRunsConceded(delivery), 0);
  const figures = {
    legalBalls: previous.legalBalls + legalBalls,
    maidens: previous.maidens + (legalBalls === 6 && runs === 0 ? 1 : 0),
    runs: previous.runs + runs,
    wickets: previous.wickets + deliveries.reduce((total, delivery) => total + delivery.wicketCount, 0)
  };
  figuresByBowler.set(bowler, figures);
  const isFirstAppearance = !displayedBowlers.has(bowler);
  displayedBowlers.add(bowler);
  return {
    bowler,
    overs: formatBowlerOvers(figures.legalBalls),
    maidens: figures.maidens,
    runs: figures.runs,
    wickets: figures.wickets,
    isFirstAppearance
  };
}

function emptyBowlerFigures(): BowlerFigures {
  return {legalBalls: 0, maidens: 0, runs: 0, wickets: 0};
}

function formatBowlerOvers(legalBalls: number): string {
  const completedOvers = Math.floor(legalBalls / 6);
  const ballsInCurrentOver = legalBalls % 6;
  return ballsInCurrentOver === 0 ? completedOvers.toString() : `${completedOvers}.${ballsInCurrentOver}`;
}

function isLegalDelivery(delivery: ScoresheetDelivery): boolean {
  return delivery.wides === 0 && delivery.noBalls === 0;
}

function bowlerRunsConceded(delivery: ScoresheetDelivery): number {
  return Math.max(0, delivery.totalRuns - delivery.byes - delivery.legByes);
}