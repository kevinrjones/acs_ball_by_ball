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
    expect(fixture.nativeElement.textContent).toContain('Batters linear notation');
    expect(fixture.nativeElement.textContent).toContain('Batter One');
    expect(fixture.nativeElement.textContent).toContain('4');
    expect(fixture.nativeElement.textContent).toContain('Wicket');
    expect(fixture.nativeElement.textContent).toContain('4/1');
    expect(fixture.componentInstance.overRows(fixture.componentInstance.scoresheet()!.innings[0])).toHaveSize(1);
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

    const cells = fixture.nativeElement.querySelectorAll('.linear-matrix tbody tr td');
    const strikerCell = cells[4] as HTMLElement;
    const nonStrikerCell = cells[5] as HTMLElement;

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