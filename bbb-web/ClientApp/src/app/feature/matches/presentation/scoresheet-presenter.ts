import {
  MatchScoresheetResponse,
  ScoresheetDelivery,
  ScoresheetInnings
} from '../../../models/match.model';

export interface ScoresheetExtraTotals {
  readonly byes: number;
  readonly legByes: number;
  readonly wides: number;
  readonly noBalls: number;
}

export interface ScoresheetLedger {
  readonly runs: number;
  readonly wickets: number;
  readonly extras: ScoresheetExtraTotals;
  readonly extrasDisplay: string;
}

export interface ScoresheetDeliverySymbol {
  readonly deliveryKey: number;
  readonly value: string;
  readonly className: string;
  readonly ariaLabel?: string;
}

export interface ScoresheetPlayerNotation {
  readonly player: string;
  readonly isFirstAppearance: boolean;
  readonly symbols: readonly ScoresheetDeliverySymbol[];
  readonly lastPlayedSymbolIndex: number;
  readonly runs: number;
  readonly balls: number;
  readonly dismissed: boolean;
  readonly scoreLabel: string;
}

export interface ScoresheetBowlerSummary {
  readonly bowler: string;
  readonly overs: string;
  readonly maidens: number;
  readonly runs: number;
  readonly wickets: number;
  readonly isFirstAppearance: boolean;
}

export interface ScoresheetOverRow {
  readonly overNumber: number;
  readonly deliveries: readonly ScoresheetDelivery[];
  readonly totalRuns: number;
  readonly wicketCount: number;
  readonly boundaryCount: number;
  readonly overExtras: string;
  readonly bowlingLanes: readonly [
    readonly ScoresheetBowlerSummary[],
    readonly ScoresheetBowlerSummary[]
  ];
  readonly battingLanes: readonly [
    readonly ScoresheetPlayerNotation[],
    readonly ScoresheetPlayerNotation[]
  ];
  readonly notes: string;
  readonly ledger: ScoresheetLedger;
}

export interface PresentedScoresheetInnings extends ScoresheetInnings {
  readonly rows: readonly ScoresheetOverRow[];
  readonly totalRuns: number;
  readonly totalWickets: number;
  readonly totalLabel: string;
  readonly cardScoreLabel: string;
  readonly oversLabel: string;
  readonly runRateLabel: string;
  readonly cardFooterLabel: string;
}

export interface PresentedScoresheet extends Omit<MatchScoresheetResponse, 'innings'> {
  readonly innings: readonly PresentedScoresheetInnings[];
  readonly totalDeliveries: number;
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

interface BowlerFigures {
  readonly legalBalls: number;
  readonly maidens: number;
  readonly runs: number;
  readonly wickets: number;
}

interface OverAccumulator {
  readonly deliveries: ScoresheetDelivery[];
  readonly totalRuns: number;
  readonly wicketCount: number;
  readonly boundaryCount: number;
}

const EMPTY_EXTRA_TOTALS: ScoresheetExtraTotals = Object.freeze({
  byes: 0,
  legByes: 0,
  wides: 0,
  noBalls: 0
});

/**
 * The warehouse contract associates a wicket with a delivery, not a separate
 * dismissed-player field. The following delivery is used to identify which
 * member of the previous batting pair was replaced, with the delivery batter
 * as the fallback when that evidence is unavailable or ambiguous.
 */
export function presentScoresheet(response: MatchScoresheetResponse): PresentedScoresheet {
  const innings = response.innings.map(presentInnings);
  return {
    context: response.context,
    completeness: response.completeness,
    missingData: response.missingData,
    innings,
    totalDeliveries: innings.reduce((total, current) => total + current.deliveries.length, 0)
  };
}

function presentInnings(innings: ScoresheetInnings): PresentedScoresheetInnings {
  const deliveries = orderDeliveries(innings.deliveries);
  const dismissedPlayers = dismissedPlayersByDelivery(deliveries);
  const assignments = battingLaneAssignments(deliveries, dismissedPlayers);
  const dismissalScores = battingScoresAtDismissal(deliveries, dismissedPlayers);
  const rows = buildOverRows(deliveries, assignments, dismissalScores, dismissedPlayers);
  const totalRuns = deliveries.reduce((total, delivery) => total + delivery.totalRuns, 0);
  const totalWickets = deliveries.reduce((total, delivery) => total + delivery.wicketCount, 0);

  return {
    ...innings,
    deliveries,
    rows,
    totalRuns,
    totalWickets,
    totalLabel: formatInningsTotal(totalRuns, totalWickets),
    cardScoreLabel: formatCardScore(totalRuns, totalWickets),
    oversLabel: formatOvers(deliveries),
    runRateLabel: formatRunRate(totalRuns, deliveries),
    cardFooterLabel: totalWickets >= 10 ? 'All Out' : 'Innings complete'
  };
}

function formatInningsTotal(totalRuns: number, totalWickets: number): string {
  return totalWickets >= 10 ? `${totalRuns} ao` : `${totalRuns}`;
}

function formatCardScore(totalRuns: number, totalWickets: number): string {
  if (totalWickets >= 10) {
    return `${totalRuns} ao`;
  }
  return totalWickets > 0 ? `${totalRuns}/${totalWickets}` : `${totalRuns}`;
}

function formatOvers(deliveries: readonly ScoresheetDelivery[]): string {
  const legalBalls = deliveries.filter((delivery) => delivery.wides === 0).length;
  return `${Math.floor(legalBalls / 6)}.${legalBalls % 6} ov`;
}

function formatRunRate(totalRuns: number, deliveries: readonly ScoresheetDelivery[]): string {
  const legalBalls = deliveries.filter((delivery) => delivery.wides === 0).length;
  return legalBalls === 0 ? '—' : (totalRuns * 6 / legalBalls).toFixed(2);
}

function orderDeliveries(deliveries: readonly ScoresheetDelivery[]): ScoresheetDelivery[] {
  return [...deliveries].sort((left, right) =>
    left.inningsOrder - right.inningsOrder || left.deliveryKey - right.deliveryKey
  );
}

function buildOverRows(
  deliveries: readonly ScoresheetDelivery[],
  assignments: ReadonlyMap<number, readonly BattingLaneAssignment[]>,
  dismissalScores: ReadonlyMap<string, BattingScore>,
  dismissedPlayers: ReadonlyMap<number, string>
): ScoresheetOverRow[] {
  const overs = groupDeliveriesByOver(deliveries);
  const displayedPlayers = new Set<string>();
  const battingScores = new Map<string, BattingScore>();
  const displayedBowlers = new Set<string>();
  const bowlerFigures = new Map<string, BowlerFigures>();
  let ledger = emptyLedger();

  return overs.map(({overNumber, deliveries: overDeliveries}) => {
    const over = summarizeOver(overDeliveries);
    const overExtras = extraTotals(overDeliveries);
    ledger = addToLedger(ledger, over, overExtras);
    addBattingScores(battingScores, overDeliveries);
    return {
      overNumber,
      deliveries: overDeliveries,
      totalRuns: over.totalRuns,
      wicketCount: over.wicketCount,
      boundaryCount: over.boundaryCount,
      overExtras: formatOverExtras(overExtras),
      bowlingLanes: bowlingLanes(overDeliveries, overNumber, bowlerFigures, displayedBowlers),
      battingLanes: battingLanes(overDeliveries, assignments, battingScores, dismissalScores, displayedPlayers, dismissedPlayers),
      notes: overNotes(overDeliveries, overExtras, over.boundaryCount, dismissedPlayers),
      ledger
    };
  });
}

function groupDeliveriesByOver(deliveries: readonly ScoresheetDelivery[]): Array<{
  readonly overNumber: number;
  readonly deliveries: ScoresheetDelivery[];
}> {
  const deliveriesByOver = new Map<number, ScoresheetDelivery[]>();
  deliveries.forEach((delivery) => {
    const overDeliveries = deliveriesByOver.get(delivery.overNumber) || [];
    overDeliveries.push(delivery);
    deliveriesByOver.set(delivery.overNumber, overDeliveries);
  });
  return Array.from(deliveriesByOver.entries())
    .sort(([left], [right]) => left - right)
    .map(([overNumber, overDeliveries]) => ({overNumber, deliveries: overDeliveries}));
}

function summarizeOver(deliveries: readonly ScoresheetDelivery[]): OverAccumulator {
  return {
    deliveries: [...deliveries],
    totalRuns: deliveries.reduce((total, delivery) => total + delivery.totalRuns, 0),
    wicketCount: deliveries.reduce((total, delivery) => total + delivery.wicketCount, 0),
    boundaryCount: deliveries.filter((delivery) => delivery.batterRuns === 4 || delivery.batterRuns === 6).length
  };
}

function battingLanes(
  deliveries: readonly ScoresheetDelivery[],
  assignments: ReadonlyMap<number, readonly BattingLaneAssignment[]>,
  battingScores: ReadonlyMap<string, BattingScore>,
  dismissalScores: ReadonlyMap<string, BattingScore>,
  displayedPlayers: Set<string>,
  dismissedPlayers: ReadonlyMap<number, string>
): readonly [readonly ScoresheetPlayerNotation[], readonly ScoresheetPlayerNotation[]] {
  const playersByLane: [string[], string[]] = [[], []];
  deliveries.forEach((delivery) => {
    (assignments.get(delivery.deliveryKey) || []).forEach((assignment) => {
      if (!playersByLane[assignment.lane].includes(assignment.player)) {
        playersByLane[assignment.lane].push(assignment.player);
      }
    });
  });

  const firstLane = playerNotations(playersByLane[0], deliveries, assignments, battingScores, dismissalScores, displayedPlayers, dismissedPlayers);
  const secondLane = playerNotations(playersByLane[1], deliveries, assignments, battingScores, dismissalScores, displayedPlayers, dismissedPlayers);
  return [firstLane, secondLane];
}

function playerNotations(
  players: readonly string[],
  deliveries: readonly ScoresheetDelivery[],
  assignments: ReadonlyMap<number, readonly BattingLaneAssignment[]>,
  battingScores: ReadonlyMap<string, BattingScore>,
  dismissalScores: ReadonlyMap<string, BattingScore>,
  displayedPlayers: Set<string>,
  dismissedPlayers: ReadonlyMap<number, string>
): ScoresheetPlayerNotation[] {
  return players.map((player) => {
    const isFirstAppearance = !displayedPlayers.has(player);
    const notation = playerNotation(player, deliveries, assignments, battingScores, dismissalScores, isFirstAppearance, dismissedPlayers);
    displayedPlayers.add(player);
    return notation;
  });
}

function playerNotation(
  player: string,
  deliveries: readonly ScoresheetDelivery[],
  assignments: ReadonlyMap<number, readonly BattingLaneAssignment[]>,
  battingScores: ReadonlyMap<string, BattingScore>,
  dismissalScores: ReadonlyMap<string, BattingScore>,
  isFirstAppearance: boolean,
  dismissedPlayers: ReadonlyMap<number, string>
): ScoresheetPlayerNotation {
  const strikerDeliveries = deliveries.filter((delivery) =>
    isPlayerAssigned(delivery, player, assignments) && delivery.batter?.trim() === player
  );
  const score = battingScores.get(player) || emptyBattingScore();
  const dismissed = deliveries.some((delivery) => dismissedPlayers.get(delivery.deliveryKey) === player);
  const dismissalScore = dismissed ? dismissalScores.get(player) : undefined;

  return {
    player,
    isFirstAppearance,
    symbols: deliveries.map((delivery) => deliverySymbolFor(player, delivery, strikerDeliveries, dismissedPlayers)),
    lastPlayedSymbolIndex: lastPlayedSymbolIndex(deliveries, player, strikerDeliveries, dismissedPlayers),
    runs: score.runs,
    balls: score.balls,
    dismissed,
    scoreLabel: dismissed && dismissalScore
      ? dismissalSummary(dismissalScore)
      : `(${score.runs}r, ${score.balls}b)`
  };
}

function lastPlayedSymbolIndex(
  deliveries: readonly ScoresheetDelivery[],
  player: string,
  strikerDeliveries: readonly ScoresheetDelivery[],
  dismissedPlayers: ReadonlyMap<number, string>
): number {
  const strikerDeliveryKeys = new Set(strikerDeliveries.map((delivery) => delivery.deliveryKey));
  return deliveries.reduce(
    (lastIndex, delivery, index) => strikerDeliveryKeys.has(delivery.deliveryKey) || dismissedPlayers.get(delivery.deliveryKey) === player
      ? index
      : lastIndex,
    -1
  );
}

function isPlayerAssigned(
  delivery: ScoresheetDelivery,
  player: string,
  assignments: ReadonlyMap<number, readonly BattingLaneAssignment[]>
): boolean {
  return (assignments.get(delivery.deliveryKey) || []).some((assignment) => assignment.player === player);
}

function deliverySymbolFor(
  player: string,
  delivery: ScoresheetDelivery,
  strikerDeliveries: readonly ScoresheetDelivery[],
  dismissedPlayers: ReadonlyMap<number, string>
): ScoresheetDeliverySymbol {
  const isDismissedPlayer = dismissedPlayers.get(delivery.deliveryKey) === player;
  const isStriker = strikerDeliveries.some((strikerDelivery) => strikerDelivery.deliveryKey === delivery.deliveryKey);
  if (!isDismissedPlayer && !isStriker) {
    return {
      deliveryKey: delivery.deliveryKey,
      value: '',
      className: 'matrix-symbol--non-striker-placeholder',
      ariaLabel: `${player} was non-striker on this delivery`
    };
  }
  return {
    deliveryKey: delivery.deliveryKey,
    value: deliverySymbol(delivery, isDismissedPlayer),
    className: deliverySymbolClass(delivery, isDismissedPlayer)
  };
}

function dismissedPlayersByDelivery(deliveries: readonly ScoresheetDelivery[]): Map<number, string> {
  const dismissedPlayers = new Map<number, string>();
  deliveries.forEach((delivery, index) => {
    const dismissedPlayer = dismissedPlayerForDelivery(deliveries, index);
    if (dismissedPlayer) {
      dismissedPlayers.set(delivery.deliveryKey, dismissedPlayer);
    }
  });
  return dismissedPlayers;
}

function dismissedPlayerForDelivery(
  deliveries: readonly ScoresheetDelivery[],
  deliveryIndex: number
): string | undefined {
  const delivery = deliveries[deliveryIndex];
  if (!delivery || delivery.wicketCount === 0) {
    return undefined;
  }

  const batter = delivery.batter?.trim() || undefined;
  const currentPlayers = Array.from(new Set([batter, delivery.nonStriker?.trim() || undefined].filter(
    (player): player is string => player !== undefined
  )));
  const nextDelivery = deliveries[deliveryIndex + 1];
  if (!nextDelivery || currentPlayers.length !== 2) {
    return batter;
  }

  const nextPlayers = new Set([
    nextDelivery.batter?.trim() || undefined,
    nextDelivery.nonStriker?.trim() || undefined
  ]);
  const replacedPlayer = currentPlayers.filter((player) => !nextPlayers.has(player));
  return replacedPlayer.length === 1 ? replacedPlayer[0] : batter;
}

function battingLaneAssignments(
  deliveries: readonly ScoresheetDelivery[],
  dismissedPlayers: ReadonlyMap<number, string>
): Map<number, BattingLaneAssignment[]> {
  const lanes: Array<string | undefined> = [undefined, undefined];
  const playerLanes = new Map<string, number>();
  const assignments = new Map<number, BattingLaneAssignment[]>();

  deliveries.forEach((delivery) => {
    const players = Array.from(new Set([
      delivery.batter?.trim() || 'Unknown batter',
      delivery.nonStriker?.trim() || 'Unknown non-striker'
    ]));
    players.forEach((player) => assignPlayerToLane(player, lanes, playerLanes));
    assignments.set(delivery.deliveryKey, players.flatMap((player) => {
      const lane = playerLanes.get(player);
      return lane === undefined ? [] : [{lane, player}];
    }));
    releaseDismissedBatter(delivery, lanes, playerLanes, dismissedPlayers);
  });
  return assignments;
}

function assignPlayerToLane(player: string, lanes: Array<string | undefined>, playerLanes: Map<string, number>): void {
  if (playerLanes.has(player)) {
    return;
  }
  const lane = lanes.indexOf(undefined);
  if (lane >= 0) {
    lanes[lane] = player;
    playerLanes.set(player, lane);
  }
}

function releaseDismissedBatter(
  delivery: ScoresheetDelivery,
  lanes: Array<string | undefined>,
  playerLanes: Map<string, number>,
  dismissedPlayers: ReadonlyMap<number, string>
): void {
  const dismissedBatter = dismissedPlayers.get(delivery.deliveryKey);
  const batterLane = dismissedBatter ? playerLanes.get(dismissedBatter) : undefined;
  if (dismissedBatter && batterLane !== undefined) {
    lanes[batterLane] = undefined;
    playerLanes.delete(dismissedBatter);
  }
}

function battingScoresAtDismissal(
  deliveries: readonly ScoresheetDelivery[],
  dismissedPlayers: ReadonlyMap<number, string>
): Map<string, BattingScore> {
  const scores = new Map<string, BattingScore>();
  const dismissalScores = new Map<string, BattingScore>();
  deliveries.forEach((delivery) => {
    const player = delivery.batter?.trim();
    if (player) {
      const previous = scores.get(player) || emptyBattingScore();
      scores.set(player, {
        runs: previous.runs + delivery.batterRuns,
        balls: previous.balls + (delivery.wides === 0 ? 1 : 0),
        fours: previous.fours + (delivery.batterRuns === 4 ? 1 : 0),
        sixes: previous.sixes + (delivery.batterRuns === 6 ? 1 : 0)
      });
    }
    const dismissedPlayer = dismissedPlayers.get(delivery.deliveryKey);
    if (dismissedPlayer && !dismissalScores.has(dismissedPlayer)) {
      dismissalScores.set(dismissedPlayer, scores.get(dismissedPlayer) || emptyBattingScore());
    }
  });
  return dismissalScores;
}

function addBattingScores(scores: Map<string, BattingScore>, deliveries: readonly ScoresheetDelivery[]): void {
  deliveries.forEach((delivery) => {
    const player = delivery.batter?.trim();
    if (!player) {
      return;
    }
    const previous = scores.get(player) || emptyBattingScore();
    scores.set(player, {
      runs: previous.runs + delivery.batterRuns,
      balls: previous.balls + (delivery.wides === 0 ? 1 : 0),
      fours: previous.fours + (delivery.batterRuns === 4 ? 1 : 0),
      sixes: previous.sixes + (delivery.batterRuns === 6 ? 1 : 0)
    });
  });
}

function emptyBattingScore(): BattingScore {
  return {runs: 0, balls: 0, fours: 0, sixes: 0};
}

function bowlingLanes(
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

function overNotes(
  deliveries: readonly ScoresheetDelivery[],
  extras: ScoresheetExtraTotals,
  boundaryCount: number,
  dismissedPlayers: ReadonlyMap<number, string>
): string {
  const notes: string[] = [];
  const wicketNotes = deliveries.flatMap((delivery) => {
    const description = wicketDescription(delivery);
    return description
      ? description.split('; ').map((wicket) => `${dismissedPlayers.get(delivery.deliveryKey) || delivery.batter?.trim() || 'Unknown batter'} — ${wicket}`)
      : [];
  });
  if (wicketNotes.length > 0) {
    notes.push(...wicketNotes.map((wicket, index) => `WICKET ${index + 1}: ${wicket}`));
  }
  const formattedExtras = formatOverExtras(extras);
  if (formattedExtras !== '—') {
    notes.push(formattedExtras);
  }
  if (boundaryCount > 0) {
    notes.push(`${boundaryCount} boundary${boundaryCount === 1 ? '' : 'ies'}`);
  }
  return notes.join('\n') || 'No wicket or extra events recorded';
}

function wicketDescription(delivery: ScoresheetDelivery): string {
  const descriptions = delivery.wickets.map((wicket) => {
    const fielders = wicket.fielders.length > 0 ? ` (${wicket.fielders.join(', ')})` : '';
    const kind = wicket.kind?.trim() || 'Wicket';
    return `${kind.charAt(0).toUpperCase()}${kind.slice(1)}${fielders}`;
  });
  return descriptions.length > 0 ? descriptions.join('; ') : delivery.wicketCount > 0 ? 'Wicket' : '';
}

function deliverySymbol(delivery: ScoresheetDelivery, includeWicket = true): string {
  if (includeWicket && delivery.wicketCount > 0) {
    return 'W';
  }
  if (delivery.wides > 0) {
    return `${delivery.wides}wd`;
  }
  if (delivery.noBalls > 0) {
    return `${delivery.noBalls}nb`;
  }
  if (delivery.legByes > 0) {
    return `${delivery.legByes}lb`;
  }
  if (delivery.byes > 0) {
    return `${delivery.byes}b`;
  }
  if (delivery.totalRuns === 0) {
    return '•';
  }
  return delivery.totalRuns.toString();
}

function deliverySymbolClass(delivery: ScoresheetDelivery, includeWicket = true): string {
  if (includeWicket && delivery.wicketCount > 0) {
    return 'matrix-symbol--wicket';
  }
  if (delivery.wides > 0 || delivery.noBalls > 0 || delivery.legByes > 0 || delivery.byes > 0) {
    return 'matrix-symbol--extra';
  }
  if (delivery.batterRuns === 4 || delivery.batterRuns === 6) {
    return 'matrix-symbol--boundary';
  }
  return delivery.totalRuns === 0 ? 'matrix-symbol--dot' : 'matrix-symbol--run';
}

function extraTotals(deliveries: readonly ScoresheetDelivery[]): ScoresheetExtraTotals {
  return deliveries.reduce((totals, delivery) => ({
    byes: totals.byes + delivery.byes,
    legByes: totals.legByes + delivery.legByes,
    wides: totals.wides + delivery.wides,
    noBalls: totals.noBalls + delivery.noBalls
  }), EMPTY_EXTRA_TOTALS);
}

function formatOverExtras(extras: ScoresheetExtraTotals): string {
  return extraParts(extras).join(' ') || '—';
}

function formatLedgerExtras(extras: ScoresheetExtraTotals): string {
  const parts = extraParts(extras);
  const total = extras.byes + extras.legByes + extras.wides + extras.noBalls;
  return parts.length > 0 ? `${parts.join(' ')} (${total})` : '—';
}

function extraParts(extras: ScoresheetExtraTotals): string[] {
  return [
    extras.byes > 0 ? `B:${extras.byes}` : '',
    extras.legByes > 0 ? `LB:${extras.legByes}` : '',
    extras.wides > 0 ? `W:${extras.wides}` : '',
    extras.noBalls > 0 ? `NB:${extras.noBalls}` : ''
  ].filter(Boolean);
}

function emptyLedger(): ScoresheetLedger {
  return {runs: 0, wickets: 0, extras: EMPTY_EXTRA_TOTALS, extrasDisplay: '—'};
}

function addToLedger(
  ledger: ScoresheetLedger,
  over: OverAccumulator,
  extras: ScoresheetExtraTotals
): ScoresheetLedger {
  const cumulativeExtras = {
    byes: ledger.extras.byes + extras.byes,
    legByes: ledger.extras.legByes + extras.legByes,
    wides: ledger.extras.wides + extras.wides,
    noBalls: ledger.extras.noBalls + extras.noBalls
  };
  return {
    runs: ledger.runs + over.totalRuns,
    wickets: ledger.wickets + over.wicketCount,
    extras: cumulativeExtras,
    extrasDisplay: formatLedgerExtras(cumulativeExtras)
  };
}

function dismissalSummary(score: BattingScore): string {
  const boundaries = [
    score.fours ? `${score.fours}x4` : '',
    score.sixes ? `${score.sixes}x6` : ''
  ].filter(Boolean).join(', ');
  return `(${score.runs}r, ${score.balls}b${boundaries ? `, ${boundaries}` : ''})`;
}
