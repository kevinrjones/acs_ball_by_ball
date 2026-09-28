import {MatchScoresheetResponse, ScoresheetDelivery} from '../../../models/match.model';
import {presentScoresheet} from './scoresheet-presenter';

describe('presentScoresheet', () => {
  it('should prepare grouped rows with cumulative ledger values and separate over extras', () => {
    const response = scoresheet(
      delivery({deliveryKey: 1, overNumber: 1, byes: 3, wides: 1, totalRuns: 4}),
      delivery({deliveryKey: 2, overNumber: 2, legByes: 2, totalRuns: 2})
    );

    const innings = presentScoresheet(response).innings[0];

    expect(innings.rows).toHaveSize(2);
    expect(innings.rows[0].overExtras).toBe('B:3 W:1');
    expect(innings.rows[0].notes).toContain('B:3 W:1');
    expect(innings.rows[0].ledger.extrasDisplay).toBe('B:3 W:1 (4)');
    expect(innings.rows[1].ledger.extrasDisplay).toBe('B:3 LB:2 W:1 (6)');
    expect(innings.rows[1].bowlerSummaries[0]).toEqual({
      bowler: 'Bowler', balls: 1, runs: 2, wickets: 0
    });
  });

  it('should place multiple wicket notes on separate lines', () => {
    const firstWicket = delivery({
      deliveryKey: 1,
      wicketCount: 1,
      wickets: [{wicketKey: 1, kind: 'bowled', fielders: []}]
    });
    const secondWicket = delivery({
      deliveryKey: 2,
      inningsOrder: 2,
      ballInOver: 2,
      batter: 'Batter Two',
      wicketCount: 1,
      wickets: [{wicketKey: 2, kind: 'caught', fielders: ['Fielder']}]
    });

    const notes = presentScoresheet(scoresheet(firstWicket, secondWicket)).innings[0].rows[0].notes;

    expect(notes).toBe(
      'WICKET 1: Batter One — Bowled\nWICKET 2: Batter Two — Caught (Fielder)'
    );
  });

  it('should prepare striker notation, non-striker placeholders, and dismissal figures once', () => {
    const response = scoresheet(
      delivery({deliveryKey: 1, inningsOrder: 1, overNumber: 1, batter: 'Batter One', nonStriker: 'Batter Two', batterRuns: 4, totalRuns: 4}),
      delivery({deliveryKey: 2, inningsOrder: 2, overNumber: 1, batter: 'Batter One', nonStriker: 'Batter Two', wides: 1, totalRuns: 1}),
      delivery({deliveryKey: 3, inningsOrder: 3, overNumber: 2, batter: 'Batter One', nonStriker: 'Batter Two', batterRuns: 6, totalRuns: 6}),
      delivery({deliveryKey: 4, inningsOrder: 4, overNumber: 2, batter: 'Batter One', nonStriker: 'Batter Two', wicketCount: 1})
    );

    const rows = presentScoresheet(response).innings[0].rows;
    const firstLane = rows[0].battingLanes[0][0];
    const secondLane = rows[0].battingLanes[1][0];

    expect(firstLane.symbols.map((symbol) => symbol.value)).toEqual(['4', '1wd']);
    expect(secondLane.symbols.every((symbol) => symbol.className === 'matrix-symbol--non-striker-placeholder')).toBeTrue();
    expect(firstLane.showName).toBeTrue();
    expect(secondLane.showName).toBeTrue();
    expect(rows[1].battingLanes[0][0].scoreLabel).toBe('(10r, 3b, 1x4, 1x6)');
    expect(rows[1].battingLanes[0][0].showName).toBeFalse();
    expect(rows[1].battingLanes[0][0].dismissed).toBeTrue();
  });

  it('should show a replacement batter name when they enter a stable lane', () => {
    const rows = presentScoresheet(scoresheet(
      delivery({deliveryKey: 1, overNumber: 1, batter: 'Batter One', nonStriker: 'Batter Two'}),
      delivery({deliveryKey: 2, inningsOrder: 2, overNumber: 2, batter: 'Batter Two', nonStriker: 'Batter One'}),
      delivery({deliveryKey: 3, inningsOrder: 3, overNumber: 2, batter: 'Batter Two', nonStriker: 'Batter One', wicketCount: 1}),
      delivery({deliveryKey: 4, inningsOrder: 4, overNumber: 3, batter: 'Batter One', nonStriker: 'Batter Three'})
    )).innings[0].rows;

    expect(rows[0].battingLanes[0][0].showName).toBeTrue();
    expect(rows[0].battingLanes[1][0].showName).toBeTrue();
    expect(rows[1].battingLanes[0][0].showName).toBeFalse();
    expect(rows[1].battingLanes[1][0].showName).toBeFalse();
    expect(rows[2].battingLanes[0][0].showName).toBeFalse();
    expect(rows[2].battingLanes[1][0].showName).toBeTrue();
  });

  it('should render counted extra symbols for each delivery', () => {
    const rows = presentScoresheet(scoresheet(
      delivery({deliveryKey: 1, inningsOrder: 1, wides: 1, totalRuns: 1}),
      delivery({deliveryKey: 2, inningsOrder: 2, noBalls: 4, totalRuns: 4}),
      delivery({deliveryKey: 3, inningsOrder: 3, legByes: 2, totalRuns: 2}),
      delivery({deliveryKey: 4, inningsOrder: 4, byes: 3, totalRuns: 3})
    )).innings[0].rows;

    expect(rows[0].battingLanes[0][0].symbols.map((symbol) => symbol.value)).toEqual([
      '1wd', '4nb', '2lb', '3b'
    ]);
    expect(rows[0].battingLanes[0][0].symbols.every((symbol) => symbol.className === 'matrix-symbol--extra')).toBeTrue();
  });

  it('should keep the delivery input immutable while ordering the prepared view', () => {
    const later = delivery({deliveryKey: 2, inningsOrder: 2, overNumber: 2});
    const earlier = delivery({deliveryKey: 1, inningsOrder: 1, overNumber: 1});
    const response = scoresheet(later, earlier);

    const presented = presentScoresheet(response);

    expect(response.innings[0].deliveries).toEqual([later, earlier]);
    expect(presented.innings[0].deliveries).toEqual([earlier, later]);
  });

  it('should mark an innings all out in its prepared total label after ten wickets', () => {
    const deliveries = Array.from({length: 10}, (_, index) => delivery({
      deliveryKey: index + 1,
      inningsOrder: index + 1,
      batterRuns: index === 0 ? 302 : 0,
      totalRuns: index === 0 ? 302 : 0,
      wicketCount: 1
    }));

    const presentedInnings = presentScoresheet(scoresheet(...deliveries)).innings[0];

    expect(presentedInnings.totalWickets).toBe(10);
    expect(presentedInnings.totalLabel).toBe('302 ao');
  });

  it('should prepare compact card metrics using legal deliveries', () => {
    const presentedInnings = presentScoresheet(scoresheet(
      delivery({deliveryKey: 1, wides: 1, totalRuns: 1}),
      delivery({deliveryKey: 2, inningsOrder: 2, batterRuns: 5, totalRuns: 5, wicketCount: 1})
    )).innings[0];

    expect(presentedInnings.cardScoreLabel).toBe('6/1');
    expect(presentedInnings.oversLabel).toBe('0.1 ov');
    expect(presentedInnings.runRateLabel).toBe('36.00');
    expect(presentedInnings.cardFooterLabel).toBe('Innings complete');
  });
});

function scoresheet(...deliveries: ScoresheetDelivery[]): MatchScoresheetResponse {
  return {
    context: {matchKey: 101, sourceMatchId: 1001, fileName: 'match.json', team1: 'England', team2: 'India'},
    completeness: 'COMPLETE',
    missingData: [],
    innings: [{inningsNumber: 1, battingTeam: 'England', bowlingTeam: 'India', deliveries}]
  };
}

function delivery(overrides: Partial<ScoresheetDelivery>): ScoresheetDelivery {
  return {
    deliveryKey: 1,
    sourceBallId: 1,
    inningsOrder: 1,
    overNumber: 1,
    ballNumber: 1,
    ballInOver: 1,
    batter: 'Batter One',
    nonStriker: 'Batter Two',
    bowler: 'Bowler',
    batterRuns: 0,
    extraRuns: 0,
    totalRuns: 0,
    noBalls: 0,
    wides: 0,
    byes: 0,
    legByes: 0,
    nonBoundary: null,
    powerplay: 0,
    wicketCount: 0,
    wickets: [],
    ...overrides
  };
}