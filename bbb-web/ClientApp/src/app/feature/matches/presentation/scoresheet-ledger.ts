import {ScoresheetDelivery} from '../domain/match.model';

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

const EMPTY_EXTRA_TOTALS: ScoresheetExtraTotals = Object.freeze({
  byes: 0,
  legByes: 0,
  wides: 0,
  noBalls: 0
});

export function extraTotals(deliveries: readonly ScoresheetDelivery[]): ScoresheetExtraTotals {
  return deliveries.reduce((totals, delivery) => ({
    byes: totals.byes + delivery.byes,
    legByes: totals.legByes + delivery.legByes,
    wides: totals.wides + delivery.wides,
    noBalls: totals.noBalls + delivery.noBalls
  }), EMPTY_EXTRA_TOTALS);
}

export function formatOverExtras(extras: ScoresheetExtraTotals): string {
  return extraParts(extras).join(' ') || '—';
}

export function emptyLedger(): ScoresheetLedger {
  return {runs: 0, wickets: 0, extras: EMPTY_EXTRA_TOTALS, extrasDisplay: '—'};
}

export function addToLedger(
  ledger: ScoresheetLedger,
  totalRuns: number,
  wicketCount: number,
  extras: ScoresheetExtraTotals
): ScoresheetLedger {
  const cumulativeExtras = {
    byes: ledger.extras.byes + extras.byes,
    legByes: ledger.extras.legByes + extras.legByes,
    wides: ledger.extras.wides + extras.wides,
    noBalls: ledger.extras.noBalls + extras.noBalls
  };
  return {
    runs: ledger.runs + totalRuns,
    wickets: ledger.wickets + wicketCount,
    extras: cumulativeExtras,
    extrasDisplay: formatLedgerExtras(cumulativeExtras)
  };
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