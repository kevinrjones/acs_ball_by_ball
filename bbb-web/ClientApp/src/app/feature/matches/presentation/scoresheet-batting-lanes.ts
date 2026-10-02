import {ScoresheetDelivery} from '../domain/match.model';
import {deliverySymbol, deliverySymbolClass, formatDismissalSummary} from './scoresheet-formatting';

export interface BattingLaneAssignment {
  readonly lane: number;
  readonly player: string;
}

export interface BattingScore {
  readonly runs: number;
  readonly balls: number;
  readonly fours: number;
  readonly sixes: number;
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

export function battingLanes(
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

export function dismissedPlayersByDelivery(deliveries: readonly ScoresheetDelivery[]): Map<number, string> {
  const dismissedPlayers = new Map<number, string>();
  deliveries.forEach((delivery, index) => {
    const dismissedPlayer = dismissedPlayerForDelivery(deliveries, index);
    if (dismissedPlayer) {
      dismissedPlayers.set(delivery.deliveryKey, dismissedPlayer);
    }
  });
  return dismissedPlayers;
}

export function battingLaneAssignments(
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

export function battingScoresAtDismissal(
  deliveries: readonly ScoresheetDelivery[],
  dismissedPlayers: ReadonlyMap<number, string>
): Map<string, BattingScore> {
  const scores = new Map<string, BattingScore>();
  const dismissalScores = new Map<string, BattingScore>();
  deliveries.forEach((delivery) => {
    const player = delivery.batter?.trim();
    if (player) {
      const previous = scores.get(player) || emptyBattingScore();
      scores.set(player, addDeliveryToBattingScore(previous, delivery));
    }
    const dismissedPlayer = dismissedPlayers.get(delivery.deliveryKey);
    if (dismissedPlayer && !dismissalScores.has(dismissedPlayer)) {
      dismissalScores.set(dismissedPlayer, scores.get(dismissedPlayer) || emptyBattingScore());
    }
  });
  return dismissalScores;
}

export function addBattingScores(scores: Map<string, BattingScore>, deliveries: readonly ScoresheetDelivery[]): void {
  deliveries.forEach((delivery) => {
    const player = delivery.batter?.trim();
    if (!player) {
      return;
    }
    const previous = scores.get(player) || emptyBattingScore();
    scores.set(player, addDeliveryToBattingScore(previous, delivery));
  });
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
      ? formatDismissalSummary(dismissalScore.runs, dismissalScore.balls, dismissalScore.fours, dismissalScore.sixes)
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

function addDeliveryToBattingScore(previous: BattingScore, delivery: ScoresheetDelivery): BattingScore {
  return {
    runs: previous.runs + delivery.batterRuns,
    balls: previous.balls + (delivery.wides === 0 ? 1 : 0),
    fours: previous.fours + (delivery.batterRuns === 4 ? 1 : 0),
    sixes: previous.sixes + (delivery.batterRuns === 6 ? 1 : 0)
  };
}

function emptyBattingScore(): BattingScore {
  return {runs: 0, balls: 0, fours: 0, sixes: 0};
}