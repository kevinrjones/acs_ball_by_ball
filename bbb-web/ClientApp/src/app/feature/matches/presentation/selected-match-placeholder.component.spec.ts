import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap} from '@angular/router';
import {of} from 'rxjs';
import {MatchService} from '../../../services/match.service';
import {SelectedMatchPlaceholderComponent} from './selected-match-placeholder.component';
import {MatchScoresheetResponse, ScoresheetDelivery} from '../../../models/match.model';

describe('SelectedMatchPlaceholderComponent', () => {
  it('should preserve the complete valid search query when returning to results', async () => {
    await configure({
      team: 'India', teamExactMatch: 'true', opponents: 'Pakistan', opponentsExactMatch: 'false',
      venue: '0', startDate: '2023-01-01', endDate: '2023-12-31', matchType: 'itt', matchResult: '3',
      page: '2', pageSize: '10'
    });
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.returnQueryParams()).toEqual({
      team: 'India', teamExactMatch: 'true', opponents: 'Pakistan', opponentsExactMatch: 'false',
      venue: '0', startDate: '2023-01-01', endDate: '2023-12-31', matchType: 'itt', matchResult: '3',
      page: '2', pageSize: '10'
    });
  });

  it('should reject a malformed match key', async () => {
    await configure({}, 'not-a-key');
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.isValidMatchKey()).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('selected match key is invalid');
  });

  it('should render deliveries as a grouped linear over-by-over matrix', async () => {
    const firstDelivery = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 4, totalRuns: 4
    });
    const secondDelivery = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 0, totalRuns: 0, wicketCount: 1,
      wickets: [{wicketKey: 11, kind: 'Bowled', fielders: []}]
    });
    await configure({}, '101', scoresheet(firstDelivery, secondDelivery));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.linear-matrix')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Source ball');
    expect(fixture.nativeElement.textContent).toContain('Batters linear notation');
    expect(fixture.nativeElement.textContent).toContain('Batter One');
    expect(fixture.nativeElement.textContent).toContain('4');
    expect(fixture.nativeElement.textContent).toContain('Wicket');
    expect(fixture.nativeElement.textContent).toContain('4/1');
    expect(fixture.componentInstance.scoresheet()!.innings[0].rows).toHaveSize(1);
  });

  it('should display batters side-by-side with placeholders for the non-striker', async () => {
    const deliveryOne = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 4, totalRuns: 4
    });
    const deliveryTwo = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'Batter One',
      nonStriker: 'Batter Two', batterRuns: 1, totalRuns: 1
    });
    await configure({}, '101', scoresheet(deliveryOne, deliveryTwo));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const batterCells = fixture.nativeElement.querySelectorAll('.linear-matrix__batter-cell') as NodeListOf<HTMLElement>;
    const strikerCell = batterCells[0];
    const nonStrikerCell = batterCells[1];

    expect(strikerCell.classList).toContain('linear-matrix__batter-cell');
    expect(nonStrikerCell.classList).toContain('linear-matrix__batter-cell');
    expect(strikerCell.querySelectorAll('.matrix-symbol')).toHaveSize(2);
    expect(nonStrikerCell.textContent).toContain('Batter Two');
    expect(nonStrikerCell.querySelectorAll('.matrix-symbol')).toHaveSize(2);
    expect(nonStrikerCell.querySelectorAll('.matrix-symbol--non-striker-placeholder')).toHaveSize(2);
    expect(fixture.nativeElement.querySelectorAll('.matrix-player-line')).toHaveSize(2);
    expect(strikerCell.querySelectorAll('.matrix-player-line')).toHaveSize(1);
    expect(nonStrikerCell.querySelectorAll('.matrix-player-line')).toHaveSize(1);
  });

  it('should show dismissal details for an out batter and in the notes', async () => {
    const dismissal = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'D. Hassan',
      nonStriker: 'Blake', wicketCount: 1, wickets: [{wicketKey: 157781, kind: 'caught', fielders: ['RG Sharma']}]
    });
    await configure({}, '101', scoresheet(dismissal));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const batterCell = fixture.nativeElement.querySelector('.linear-matrix__batter-cell') as HTMLElement;
    const notesCell = fixture.nativeElement.querySelector('.linear-matrix__notes') as HTMLElement;

    expect(batterCell.textContent).toContain('D. Hassan');
    expect(batterCell.textContent).toContain('(0r, 1b)');
    expect(notesCell.textContent).toContain('WICKET 1: D. Hassan — Caught (RG Sharma)');
  });

  it('should show a dismissed batter score with cumulative boundaries and an out marker', async () => {
    const firstDelivery = delivery({
      deliveryKey: 1, sourceBallId: 101, inningsOrder: 1, overNumber: 1, ballInOver: 1,
      batter: 'D. Hassan', nonStriker: 'Blake', batterRuns: 4, totalRuns: 4
    });
    const wideDelivery = delivery({
      deliveryKey: 2, sourceBallId: 102, inningsOrder: 2, overNumber: 1, ballInOver: 2,
      batter: 'D. Hassan', nonStriker: 'Blake', batterRuns: 0, wides: 1, totalRuns: 1
    });
    const secondDelivery = delivery({
      deliveryKey: 3, sourceBallId: 103, inningsOrder: 3, overNumber: 2, ballInOver: 1,
      batter: 'D. Hassan', nonStriker: 'Blake', batterRuns: 6, totalRuns: 6
    });
    const dismissal = delivery({
      deliveryKey: 4, sourceBallId: 104, inningsOrder: 4, overNumber: 2, ballInOver: 2,
      batter: 'D. Hassan', nonStriker: 'Blake', batterRuns: 1, totalRuns: 1, wicketCount: 1,
      wickets: [{wicketKey: 157781, kind: 'caught', fielders: ['RG Sharma']}]
    });
    await configure({}, '101', scoresheet(firstDelivery, wideDelivery, secondDelivery, dismissal));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const dismissedScore = fixture.nativeElement.querySelector('.matrix-dismissed') as HTMLElement;

    expect(dismissedScore.textContent?.trim()).toBe('(11r, 3b, 1x4, 1x6)');
    expect(fixture.nativeElement.textContent).toContain('[OUT]');
  });

  it('should mark only the dismissed batter as out', async () => {
    const dismissal = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'JG Bethell',
      nonStriker: 'BM Duckett', wicketCount: 1, wickets: [{wicketKey: 157781, kind: 'caught', fielders: ['RG Sharma']}]
    });
    await configure({}, '101', scoresheet(dismissal));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const batterCells = fixture.nativeElement.querySelectorAll('.linear-matrix__batter-cell') as NodeListOf<HTMLElement>;
    const dismissedCell = Array.from(batterCells).find((cell) => cell.textContent?.includes('JG Bethell'))!;
    const notDismissedCell = Array.from(batterCells).find((cell) => cell.textContent?.includes('BM Duckett'))!;

    expect(dismissedCell.textContent).toContain('W');
    expect(dismissedCell.textContent).toContain('(0r, 1b)');
    expect(dismissedCell.textContent).toContain('[OUT]');
    expect(notDismissedCell.querySelector('.matrix-dismissed')).toBeNull();
    expect(notDismissedCell.querySelector('.matrix-symbol--wicket')).toBeNull();
  });

  it('should display cumulative wickets in the end-of-over ledger', async () => {
    const firstWicket = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1, batter: 'First Batter',
      nonStriker: 'Second Batter', wicketCount: 1, wickets: [{wicketKey: 1, kind: 'bowled', fielders: []}]
    });
    const secondWicket = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 2, ballInOver: 1, batter: 'Second Batter',
      nonStriker: 'Third Batter', wicketCount: 1, wickets: [{wicketKey: 2, kind: 'caught', fielders: []}]
    });
    await configure({}, '101', scoresheet(firstWicket, secondWicket));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr') as NodeListOf<HTMLTableRowElement>;
    expect((rows[0].querySelector('.linear-matrix__wickets') as HTMLElement).textContent?.trim()).toBe('1');
    expect((rows[1].querySelector('.linear-matrix__wickets') as HTMLElement).textContent?.trim()).toBe('2');
  });

  it('should display cumulative extras by type in the end-of-over ledger', async () => {
    const firstOver = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1,
      byes: 3, wides: 1, totalRuns: 4
    });
    const secondOver = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 2, ballInOver: 1,
      legByes: 2, totalRuns: 2
    });
    const thirdOver = delivery({
      deliveryKey: 3, sourceBallId: 103, overNumber: 3, ballInOver: 1
    });
    await configure({}, '101', scoresheet(firstOver, secondOver, thirdOver));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr') as NodeListOf<HTMLTableRowElement>;
    const extras = Array.from(rows).map((row) =>
      (row.querySelector('.linear-matrix__extras') as HTMLElement).textContent?.trim()
    );

    expect(extras).toEqual(['B:3 W:1 (4)', 'B:3 LB:2 W:1 (6)', 'B:3 LB:2 W:1 (6)']);
  });

  it('should keep batting lanes stable when the strike changes and a batter is replaced', async () => {
    const firstBall = delivery({
      deliveryKey: 1, sourceBallId: 101, overNumber: 1, ballInOver: 1, batter: 'BM Duckett',
      nonStriker: 'JG Bethell'
    });
    const secondBall = delivery({
      deliveryKey: 2, sourceBallId: 102, overNumber: 1, ballInOver: 2, batter: 'JG Bethell',
      nonStriker: 'BM Duckett'
    });
    const wicketBall = delivery({
      deliveryKey: 3, sourceBallId: 103, overNumber: 2, ballInOver: 1, batter: 'JG Bethell',
      nonStriker: 'BM Duckett', wicketCount: 1,
      wickets: [{wicketKey: 157781, kind: 'caught', fielders: ['RG Sharma']}]
    });
    const incomingBatterBall = delivery({
      deliveryKey: 4, sourceBallId: 104, overNumber: 3, ballInOver: 1, batter: 'BM Duckett',
      nonStriker: 'H Brook'
    });
    await configure({}, '101', scoresheet(firstBall, secondBall, wicketBall, incomingBatterBall));
    const fixture = TestBed.createComponent(SelectedMatchPlaceholderComponent);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr') as NodeListOf<HTMLTableRowElement>;
    const batterCells = (row: HTMLTableRowElement): NodeListOf<HTMLElement> =>
      row.querySelectorAll('.linear-matrix__batter-cell');

    expect(batterCells(rows[0])[0].textContent).toContain('BM Duckett');
    expect(batterCells(rows[0])[1].textContent).toContain('JG Bethell');
    expect(batterCells(rows[1])[0].textContent).toContain('BM Duckett');
    expect(batterCells(rows[1])[1].textContent).toContain('JG Bethell');
    expect(batterCells(rows[2])[0].textContent).toContain('BM Duckett');
    expect(batterCells(rows[2])[1].textContent).toContain('H Brook');
  });
});

async function configure(
  query: Record<string, string>,
  matchKey = '101',
  result: MatchScoresheetResponse = scoresheet()
): Promise<void> {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [SelectedMatchPlaceholderComponent],
    providers: [
      {
        provide: ActivatedRoute,
        useValue: {snapshot: {paramMap: convertToParamMap({matchKey}), queryParamMap: convertToParamMap(query)}}
      },
      {
        provide: MatchService,
        useValue: {getScoresheet: () => of({result})}
      }
    ]
  }).compileComponents();
}

function scoresheet(...deliveries: ScoresheetDelivery[]): MatchScoresheetResponse {
  return {
    context: {matchKey: 101, sourceMatchId: 1001, fileName: 'match.json', team1: 'England', team2: 'India'},
    completeness: deliveries.length > 0 ? 'COMPLETE' : 'EMPTY',
    missingData: deliveries.length > 0 ? [] : ['deliveries'],
    innings: deliveries.length > 0 ? [{inningsNumber: 1, battingTeam: 'England', bowlingTeam: 'India', deliveries}] : [],
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
    batter: 'Batter',
    nonStriker: 'Non-striker',
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