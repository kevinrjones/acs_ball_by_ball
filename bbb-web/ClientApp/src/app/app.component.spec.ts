import {TestBed} from '@angular/core/testing';
import {computed, signal, WritableSignal} from '@angular/core';
import {provideRouter} from '@angular/router';
import {NEVER, of, throwError} from 'rxjs';
import {AppComponent} from './app.component';
import {MatchService} from './services/match.service';
import {AuthenticationService, Session} from './services/authentication.service';
import {ApplicationMetadataService} from './services/application-metadata.service';
import {RecentMatchesResponse} from './models/match.model';

describe('AppComponent', () => {
  let matchServiceMock: jasmine.SpyObj<MatchService>;
  let authServiceMock: jasmine.SpyObj<AuthenticationService>;
  let applicationMetadataServiceMock: jasmine.SpyObj<ApplicationMetadataService>;
  let sessionSignal: WritableSignal<Session>;

  beforeEach(async () => {
    matchServiceMock = jasmine.createSpyObj<MatchService>('MatchService', ['getRecentMatches']);
    matchServiceMock.getRecentMatches.and.returnValue(NEVER);
    authServiceMock = jasmine.createSpyObj<AuthenticationService>('AuthenticationService', ['getSession']);
    applicationMetadataServiceMock = jasmine.createSpyObj<ApplicationMetadataService>('ApplicationMetadataService', ['getMetadata']);
    applicationMetadataServiceMock.getMetadata.and.returnValue(NEVER);

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
        { provide: AuthenticationService, useValue: authServiceMock },
        { provide: ApplicationMetadataService, useValue: applicationMetadataServiceMock }
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

  it('should render dynamic footer metadata', () => {
    applicationMetadataServiceMock.getMetadata.and.returnValue(of({
      result: {
        dataLastUpdated: '24 September 2026 at 07:29 BST',
        applicationVersion: 'Application version: v0.1.117. Built on 23rd of September 2026 at 06:59 GMT+00:00'
      },
      errorMessage: '',
      timeGenerated: '2026-09-24T06:29:00Z'
    }));
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const footer = fixture.nativeElement.querySelector('footer') as HTMLElement;
    expect(footer.textContent).toContain('Copyright © 2026 Kevin Jones');
    expect(footer.textContent).toContain('Data last updated: 24 September 2026 at 07:29 BST');
    expect(footer.textContent).toContain('Application version: v0.1.117');
    const footerContent = footer.querySelector('div');
    expect(footerContent?.querySelectorAll(':scope > p').length).toBe(0);
    expect(footerContent?.querySelectorAll(':scope > span').length).toBe(2);
    expect(footerContent?.classList.contains('flex')).toBeTrue();
    expect(footerContent?.classList.contains('items-center')).toBeTrue();
    expect(footerContent?.classList.contains('justify-between')).toBeTrue();
    expect(footerContent?.classList.contains('gap-4')).toBeTrue();
    expect(footer.querySelector('.app-footer__timestamp')?.textContent).toContain('·');
    expect(footer.textContent).not.toContain('Built with Angular and Ktor');
  });

  it('should render Login and Signup actions when user is unauthenticated', () => {
    sessionSignal.set(null);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const userMenuTrigger = compiled.querySelector('.user-menu-trigger');
    expect(userMenuTrigger?.tagName).toBe('BUTTON');
    expect(userMenuTrigger?.textContent).toContain('Anonymous');
    expect(userMenuTrigger?.querySelector('.user-icon')).toBeTruthy();
    expect(userMenuTrigger?.querySelector('.user-menu-chevron')).toBeTruthy();
    expect(compiled.querySelector('#login-button')).toBeNull();
    expect(compiled.querySelector('#signup-button')).toBeNull();
    expect(compiled.querySelector('#sign-out-button')).toBeNull();

    (userMenuTrigger as HTMLButtonElement).click();
    fixture.detectChanges();

    const loginBtn = compiled.querySelector('#login-button');
    const signupBtn = compiled.querySelector('#signup-button');
    expect(loginBtn?.textContent).toContain('Login');
    expect(loginBtn?.getAttribute('href')).toBe('/bff/login');
    expect(signupBtn?.textContent).toContain('Signup');
    expect(signupBtn?.getAttribute('href')).toBe('/bff/signup');
  });

  it('should render the email user menu trigger and Logout action when authenticated', () => {
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
    expect(compiled.querySelector('#login-button')).toBeNull();

    const userMenuTrigger = compiled.querySelector('.user-menu-trigger');
    expect(userMenuTrigger?.textContent).toContain('kevin@knowledgespike.com');
    expect(userMenuTrigger?.querySelector('.user-icon')).toBeTruthy();
    expect(userMenuTrigger?.querySelector('.user-menu-chevron')).toBeTruthy();

    (userMenuTrigger as HTMLButtonElement).click();
    fixture.detectChanges();

    const signOutBtn = compiled.querySelector('#sign-out-button');
    expect(signOutBtn).toBeTruthy();
    expect(signOutBtn?.textContent).toContain('Logout');
    expect(signOutBtn?.getAttribute('href')).toBe('/bff/logout?id=123');
  });

  it('should close the user menu when clicking outside or pressing Escape', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const userMenuTrigger = compiled.querySelector('.user-menu-trigger') as HTMLButtonElement;

    userMenuTrigger.click();
    fixture.detectChanges();
    expect(compiled.querySelector('.user-menu-panel')).toBeTruthy();

    document.dispatchEvent(new MouseEvent('click'));
    fixture.detectChanges();
    expect(compiled.querySelector('.user-menu-panel')).toBeNull();

    userMenuTrigger.click();
    fixture.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(compiled.querySelector('.user-menu-panel')).toBeNull();
  });


  it('should display a loading overlay without sample matches while API request is pending', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.isLoading()).toBeTrue();
    expect(compiled.querySelector('.loading-container')).toBeTruthy();
    expect(compiled.querySelectorAll('#matches article').length).toBe(0);
    expect(compiled.textContent).not.toContain('Bangladesh Women');
    expect(compiled.textContent).not.toContain('India Women');
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
    expect(fixture.componentInstance.isLoading()).toBeFalse();
    expect(fixture.componentInstance.hasLoaded()).toBeFalse();
    expect(compiled.querySelectorAll('#matches article').length).toBe(0);
    expect(compiled.textContent).not.toContain('Bangladesh Women');
    expect(compiled.textContent).not.toContain('India Women');
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
