import {TestBed} from '@angular/core/testing';
import {computed, signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {NEVER, of, throwError} from 'rxjs';
import {HomeComponent} from './home.component';
import {AuthenticationService, Session} from '../../../services/authentication.service';
import {MatchService} from '../../matches/data/match.service';
import {ApplicationMetadataService} from '../../../services/application-metadata.service';
import {RecentMatchesResponse} from '../../matches/domain/match.model';

describe('HomeComponent', () => {
  let matchService: jasmine.SpyObj<MatchService>;
  let applicationMetadataService: jasmine.SpyObj<ApplicationMetadataService>;
  let authService: jasmine.SpyObj<AuthenticationService>;
  let session: WritableSignal<Session>;

  beforeEach(async () => {
    matchService = jasmine.createSpyObj<MatchService>('MatchService', ['getRecentMatches']);
    matchService.getRecentMatches.and.returnValue(NEVER);
    applicationMetadataService = jasmine.createSpyObj<ApplicationMetadataService>('ApplicationMetadataService', ['getMetadata', 'loadMetadata']);
    Object.defineProperty(applicationMetadataService, 'metadata', {value: signal(null)});
    authService = jasmine.createSpyObj<AuthenticationService>('AuthenticationService', ['getSession']);
    session = signal<Session>({claims: [{type: 'name', value: 'Kevin Jones'}]});
    Object.defineProperty(authService, 'session', {value: session});
    Object.defineProperty(authService, 'isAuthenticated', {value: computed(() => session() !== null)});
    Object.defineProperty(authService, 'userName', {value: computed(() => session()?.claims[0]?.value ?? null)});
    Object.defineProperty(authService, 'email', {value: computed(() => null)});

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        {provide: MatchService, useValue: matchService},
        {provide: AuthenticationService, useValue: authService},
        {provide: ApplicationMetadataService, useValue: applicationMetadataService}
      ]
    }).compileComponents();
  });

  it('should load and render recent matches on the routed home page', () => {
    matchService.getRecentMatches.and.returnValue(of({
      result: {matches: [recentMatch()]},
      errorMessage: '',
      timeGenerated: new Date().toISOString()
    }));

    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(applicationMetadataService.loadMetadata).toHaveBeenCalled();
    expect(matchService.getRecentMatches).toHaveBeenCalledWith(10);
    expect(fixture.componentInstance.hasLoaded()).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Asia Cup 2026');
    const link = fixture.nativeElement.querySelector('.match-card__link') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toContain('/matches/1000010101/scoresheet');
    expect(link.getAttribute('aria-label')).toBe('View match scorecard');
  });

  it('should show the loading state without sample matches while the API is pending', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.isLoading()).toBeTrue();
    expect(fixture.nativeElement.querySelector('.loading-container')).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('#matches article').length).toBe(0);
  });

  it('should render an API error and allow the dashboard to retry', () => {
    matchService.getRecentMatches.and.returnValue(throwError(() => ({error: {errorMessage: 'Network error'}})));
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage()).toBe('Failed to load matches: Network error');
    expect(fixture.nativeElement.textContent).toContain('Failed to load matches: Network error');
    expect(fixture.componentInstance.isLoading()).toBeFalse();
  });

  it('should render a neutral placeholder for empty match summary fields', () => {
    matchService.getRecentMatches.and.returnValue(of({
      result: {matches: [{
        ...recentMatch(),
        competition: '',
        date: '',
        team1: '',
        score1: '',
        team2: '',
        score2: '',
        result: '',
        format: ''
      }]},
      errorMessage: '',
      timeGenerated: new Date().toISOString()
    }));
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('—');
    expect(fixture.nativeElement.textContent).not.toContain('MISSING');
  });

  it('should render no matches when the API returns an empty list', () => {
    matchService.getRecentMatches.and.returnValue(of({
      result: {matches: []} as RecentMatchesResponse,
      errorMessage: '',
      timeGenerated: new Date().toISOString()
    }));
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No matches found.');
  });
});

function recentMatch(): RecentMatchesResponse['matches'][number] {
  return {
    publicMatchId: 1_000_010_101,
    matchType: 'T20',
    season: '2026',
    fileName: 't20-match-1.json',
    competition: 'Asia Cup 2026',
    date: '10 Sept 2026',
    team1: 'India',
    score1: '150-5',
    team2: 'Pakistan',
    score2: '140-8',
    result: 'India won by 10 runs',
    format: 't20'
  };
}