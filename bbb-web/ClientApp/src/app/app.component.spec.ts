import { TestBed } from '@angular/core/testing';
import { computed, signal, WritableSignal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { AppComponent } from './app.component';
import { MatchService } from './services/match.service';
import { AuthenticationService, Session, UserProfileResponse } from './services/authentication.service';
import { RecentMatchesResponse } from './models/match.model';
import { Envelope } from './models/envelope.model';

describe('AppComponent', () => {
  let matchServiceMock: jasmine.SpyObj<MatchService>;
  let authServiceMock: jasmine.SpyObj<AuthenticationService>;
  let sessionSignal: WritableSignal<Session>;

  beforeEach(async () => {
    matchServiceMock = jasmine.createSpyObj<MatchService>('MatchService', ['getRecentMatches']);
    matchServiceMock.getRecentMatches.and.returnValue(NEVER);
    authServiceMock = jasmine.createSpyObj<AuthenticationService>('AuthenticationService', ['getSession', 'getUserProfile']);

    sessionSignal = signal<Session>(null);
    Object.defineProperty(authServiceMock, 'session', { value: sessionSignal });
    Object.defineProperty(authServiceMock, 'isAuthenticated', { value: computed(() => sessionSignal() !== null) });
    Object.defineProperty(authServiceMock, 'isAnonymous', { value: computed(() => sessionSignal() === null) });
    Object.defineProperty(authServiceMock, 'userName', {
      value: computed(() => sessionSignal()?.claims.find(c => c.type === 'name')?.value || null)
    });
    Object.defineProperty(authServiceMock, 'email', {
      value: computed(() => sessionSignal()?.claims.find(c => c.type === 'email')?.value || null)
    });
    Object.defineProperty(authServiceMock, 'logoutUrl', {
      value: computed(() => sessionSignal()?.claims.find(c => c.type === 'bff:logout_url')?.value || undefined)
    });

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        { provide: MatchService, useValue: matchServiceMock },
        { provide: AuthenticationService, useValue: authServiceMock }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the brand header and search controls', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('header')?.textContent).toContain('Maiden');
    expect(compiled.querySelector('header')?.textContent).toContain('Ball by Ball');
    expect(compiled.querySelector('h1')?.textContent).toContain('Ball by Ball');
  });

  it('should render Sign In link when user is unauthenticated', () => {
    sessionSignal.set(null);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const signInBtn = compiled.querySelector('#sign-in-button');
    expect(signInBtn).toBeTruthy();
    expect(signInBtn?.getAttribute('href')).toBe('/bff/login');
    expect(compiled.querySelector('#sign-out-button')).toBeNull();
    expect(compiled.querySelector('[data-purpose="user-profile-section"]')).toBeNull();
  });

  it('should render user name and Sign Out button when user is authenticated', () => {
    sessionSignal.set({
      claims: [
        { type: 'sub', value: 'user-1' },
        { type: 'name', value: 'Kevin Jones' },
        { type: 'email', value: 'kevin@knowledgespike.com' },
        { type: 'bff:logout_url', value: '/bff/logout?id=123' }
      ]
    });
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#sign-in-button')).toBeNull();

    const signOutBtn = compiled.querySelector('#sign-out-button');
    expect(signOutBtn).toBeTruthy();
    expect(signOutBtn?.getAttribute('href')).toBe('/bff/logout?id=123');
    expect(compiled.textContent).toContain('Kevin Jones');
    expect(compiled.querySelector('[data-purpose="user-profile-section"]')).toBeTruthy();
  });

  it('should load user profile when loadUserProfile is invoked', () => {
    sessionSignal.set({
      claims: [{ type: 'sub', value: 'user-1' }, { type: 'name', value: 'Kevin Jones' }]
    });
    const mockProfile: UserProfileResponse = {
      subject: 'user-1',
      name: 'Kevin Jones',
      email: 'kevin@knowledgespike.com',
      roles: ['BB.Admin']
    };
    authServiceMock.getUserProfile.and.returnValue(
      of({
        result: mockProfile,
        errorMessage: '',
        timeGenerated: new Date().toISOString()
      })
    );

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    fixture.componentInstance.loadUserProfile();
    fixture.detectChanges();

    expect(authServiceMock.getUserProfile).toHaveBeenCalled();
    expect(fixture.componentInstance.userProfile()).toEqual(mockProfile);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#user-profile-card')?.textContent).toContain('BB.Admin');
  });

  it('should display initial sample matches prior to loading from API', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const articles = compiled.querySelectorAll('#matches article');
    expect(articles.length).toBe(fixture.componentInstance.sampleMatches.length);
    expect(compiled.textContent).toContain('Bangladesh Women');
    expect(compiled.textContent).toContain('India Women');
  });

  it('should load matches from MatchService when loadRecentMatches is called', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const mockResponse: RecentMatchesResponse = {
      matches: [
        {
          matchKey: 101,
          sourceMatchId: 1001,
          matchType: 'T20',
          season: '2026',
          fileName: 't20-match-1.json',
          competition: 'Asia Cup 2026',
          date: '10 Sept 2026',
          team1: 'India',
          score1: '150-5',
          overs1: '(20ov)',
          isTeam1Winner: true,
          team2: 'Pakistan',
          score2: '140-8',
          overs2: '(20ov)',
          isTeam2Winner: false,
          result: 'India won by 10 runs',
          format: 't20'
        }
      ]
    };
    matchServiceMock.getRecentMatches.and.returnValue(
      of({
        result: mockResponse,
        errorMessage: '',
        timeGenerated: new Date().toISOString()
      })
    );

    fixture.componentInstance.loadRecentMatches();
    fixture.detectChanges();

    expect(matchServiceMock.getRecentMatches).toHaveBeenCalledWith(10);
    expect(fixture.componentInstance.matches().length).toBe(1);
    expect(fixture.componentInstance.hasLoaded()).toBeTrue();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Asia Cup 2026');
    expect(compiled.textContent).toContain('India');
    expect(compiled.textContent).toContain('150-5');
    expect(compiled.textContent).toContain('Pakistan');
    expect(compiled.textContent).toContain('140-8');
    expect(compiled.textContent).toContain('India won by 10 runs');
  });

  it('should display MISSING when match fields are missing', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const mockResponse: RecentMatchesResponse = {
      matches: [
        {
          matchKey: 102,
          sourceMatchId: 1002,
          matchType: 'T20',
          season: '2026',
          fileName: 't20-match-2.json',
          competition: '',
          date: '',
          team1: '',
          score1: '',
          team2: '',
          score2: '',
          result: '',
          format: ''
        }
      ]
    };
    matchServiceMock.getRecentMatches.and.returnValue(
      of({
        result: mockResponse,
        errorMessage: '',
        timeGenerated: new Date().toISOString()
      })
    );

    fixture.componentInstance.loadRecentMatches();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('MISSING');
  });

  it('should show error banner when MatchService fails', () => {
    const fixture = TestBed.createComponent(AppComponent);
    matchServiceMock.getRecentMatches.and.returnValue(
      throwError(() => ({ error: { errorMessage: 'Network error' } }))
    );

    fixture.componentInstance.loadRecentMatches();
    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage()).toContain('Network error');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load matches: Network error');
  });

  it('should show "No matches found" when API returns empty list', () => {
    const fixture = TestBed.createComponent(AppComponent);
    matchServiceMock.getRecentMatches.and.returnValue(
      of({
        result: { matches: [] },
        errorMessage: '',
        timeGenerated: new Date().toISOString()
      })
    );

    fixture.componentInstance.loadRecentMatches();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No matches found.');
  });

  it('should NOT render scorecard link when user is unauthenticated', () => {
    sessionSignal.set(null);
    const fixture = TestBed.createComponent(AppComponent);
    const mockResponse: RecentMatchesResponse = {
      matches: [
        {
          matchKey: 101,
          sourceMatchId: 1001,
          matchType: 'T20',
          season: '2026',
          fileName: 't20-match-1.json',
          competition: 'Asia Cup 2026',
          date: '10 Sept 2026',
          team1: 'India',
          score1: '150-5',
          result: 'India won by 10 runs',
          format: 't20'
        }
      ]
    };
    matchServiceMock.getRecentMatches.and.returnValue(
      of({
        result: mockResponse,
        errorMessage: '',
        timeGenerated: new Date().toISOString()
      })
    );

    fixture.componentInstance.loadRecentMatches();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const scorecardLink = compiled.querySelector('.match-card__link');
    expect(scorecardLink).toBeNull();
  });

  it('should render scorecard link when user is authenticated', () => {
    sessionSignal.set({
      claims: [
        { type: 'sub', value: 'user-1' },
        { type: 'name', value: 'Kevin Jones' }
      ]
    });
    const fixture = TestBed.createComponent(AppComponent);
    const mockResponse: RecentMatchesResponse = {
      matches: [
        {
          matchKey: 101,
          sourceMatchId: 1001,
          matchType: 'T20',
          season: '2026',
          fileName: 't20-match-1.json',
          competition: 'Asia Cup 2026',
          date: '10 Sept 2026',
          team1: 'India',
          score1: '150-5',
          result: 'India won by 10 runs',
          format: 't20'
        }
      ]
    };
    matchServiceMock.getRecentMatches.and.returnValue(
      of({
        result: mockResponse,
        errorMessage: '',
        timeGenerated: new Date().toISOString()
      })
    );

    fixture.componentInstance.loadRecentMatches();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const scorecardLink = compiled.querySelector('.match-card__link') as HTMLAnchorElement;
    expect(scorecardLink).toBeTruthy();
    expect(scorecardLink.getAttribute('href')).toContain('/scorecard/cardbyid/101');
  });
});
