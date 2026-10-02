import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, Router, UrlTree} from '@angular/router';
import {NEVER, of, throwError} from 'rxjs';
import {MatchResultsComponent} from './match-results.component';
import {MatchService} from '../../../services/match.service';
import {MatchSearchResponse} from '../../../models/match.model';

describe('MatchResultsComponent', () => {
  let matchService: jasmine.SpyObj<MatchService>;
  let router: jasmine.SpyObj<Router>;

  const response = (matches: MatchSearchResponse['matches']): {result: MatchSearchResponse; errorMessage: string; timeGenerated: string} => ({
    result: {
      matches,
      pagination: {page: 1, pageSize: 20, totalResults: matches.length, hasNext: false, nextPage: null}
    },
    errorMessage: '',
    timeGenerated: new Date().toISOString()
  });

  beforeEach(async () => {
    matchService = jasmine.createSpyObj<MatchService>('MatchService', ['searchMatches']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate', 'createUrlTree', 'serializeUrl']);
    Object.defineProperty(router, 'events', {value: NEVER});
    router.createUrlTree.and.returnValue({} as UrlTree);
    router.serializeUrl.and.returnValue('');

    await TestBed.configureTestingModule({
      imports: [MatchResultsComponent],
      providers: [
        {provide: MatchService, useValue: matchService},
        {provide: Router, useValue: router},
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(convertToParamMap({
              team: 'India', opponents: 'Pakistan', page: '1', pageSize: '20',
              teamExactMatch: 'true', opponentsExactMatch: 'true', venue: '0', matchType: 'all', matchResult: '0'
            }))
          }
        }
      ]
    }).compileComponents();
  });

  it('should show loading while a valid search is pending', () => {
    matchService.searchMatches.and.returnValue(NEVER);
    const fixture = TestBed.createComponent(MatchResultsComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('loading');
    expect(fixture.nativeElement.textContent).toContain('Searching match cards');
    expect(matchService.searchMatches).toHaveBeenCalledWith({
      team: 'India', teamExactMatch: true, opponents: 'Pakistan', opponentsExactMatch: true,
      venue: 0, startDate: '', endDate: '', matchType: 'all', matchResult: 0, page: 1, pageSize: 20
    });
  });

  it('should render deterministic match fields and a real selection link', () => {
    matchService.searchMatches.and.returnValue(of(response([{
      publicMatchId: 1_000_010_101,
      sourceMatchId: 1001,
      fileName: 'india-match.json',
      matchType: 'T20',
      season: '2026',
      competition: 'Asia Cup 2026',
      date: '10 Sept 2026',
      team1: 'India',
      team2: 'Pakistan',
      result: 'India won by 10 runs'
    }])));
    const fixture = TestBed.createComponent(MatchResultsComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('results');
    expect(fixture.nativeElement.textContent).toContain('Asia Cup 2026');
    expect(fixture.nativeElement.textContent).toContain('India');
    expect(fixture.nativeElement.textContent).toContain('Pakistan');
    expect(fixture.nativeElement.textContent).toContain('India won by 10 runs');
    const action = fixture.nativeElement.querySelector('a[data-result-action]') as HTMLAnchorElement;
    expect(action.tagName).toBe('A');
    expect(action.getAttribute('aria-label')).toContain('View match card');
    expect(router.createUrlTree).toHaveBeenCalledWith(
      ['/matches', 1_000_010_101, 'scoresheet'],
      jasmine.anything()
    );
    expect(fixture.componentInstance.queryParams()).toEqual({
      team: 'India', teamExactMatch: 'true', opponents: 'Pakistan', opponentsExactMatch: 'true',
      venue: '0', startDate: '', endDate: '', matchType: 'all', matchResult: '0', page: '1', pageSize: '20'
    });
  });

  it('should render no-results and error states', () => {
    matchService.searchMatches.and.returnValue(of(response([])));
    const emptyFixture = TestBed.createComponent(MatchResultsComponent);
    emptyFixture.detectChanges();
    expect(emptyFixture.componentInstance.state()).toBe('no-results');
    expect(emptyFixture.nativeElement.textContent).toContain('No matching match cards found');

    matchService.searchMatches.and.returnValue(throwError(() => new Error('Network error')));
    const errorFixture = TestBed.createComponent(MatchResultsComponent);
    errorFixture.detectChanges();
    expect(errorFixture.componentInstance.state()).toBe('error');
    expect(errorFixture.nativeElement.textContent).toContain('Network error');
  });

  it('should remain idle when the route has no query', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [MatchResultsComponent],
      providers: [
        {provide: MatchService, useValue: matchService},
        {provide: Router, useValue: router},
        {provide: ActivatedRoute, useValue: {queryParamMap: of(convertToParamMap({}))}}
      ]
    });
    const fixture = TestBed.createComponent(MatchResultsComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('idle');
    expect(matchService.searchMatches).not.toHaveBeenCalled();
  });

  it('should reject direct route state with missing required fields', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [MatchResultsComponent],
      providers: [
        {provide: MatchService, useValue: matchService},
        {provide: Router, useValue: router},
        {provide: ActivatedRoute, useValue: {queryParamMap: of(convertToParamMap({team: 'India'}))}}
      ]
    });
    const fixture = TestBed.createComponent(MatchResultsComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('error');
    expect(fixture.componentInstance.errorMessage()).toContain('team and opponent');
    expect(matchService.searchMatches).not.toHaveBeenCalled();
  });

  it('should reject malformed direct date ranges without searching', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [MatchResultsComponent],
      providers: [
        {provide: MatchService, useValue: matchService},
        {provide: Router, useValue: router},
        {
          provide: ActivatedRoute,
          useValue: {queryParamMap: of(convertToParamMap({
            team: 'India', opponents: 'Pakistan', startDate: '2026-02-30', endDate: '2026-03-01'
          }))}
        }
      ]
    });
    const fixture = TestBed.createComponent(MatchResultsComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.state()).toBe('error');
    expect(fixture.componentInstance.errorMessage()).toContain('YYYY-MM-DD');
    expect(matchService.searchMatches).not.toHaveBeenCalled();
  });
});