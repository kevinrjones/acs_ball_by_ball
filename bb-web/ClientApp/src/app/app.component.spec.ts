import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AppComponent } from './app.component';
import { MatchService } from './services/match.service';
import { RecentMatchesResponse } from './models/match.model';

describe('AppComponent', () => {
  let matchServiceMock: jasmine.SpyObj<MatchService>;

  beforeEach(async () => {
    matchServiceMock = jasmine.createSpyObj<MatchService>('MatchService', ['getRecentMatches']);

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        { provide: MatchService, useValue: matchServiceMock }
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
          fileName: 't20-match-1.json'
        }
      ]
    };
    matchServiceMock.getRecentMatches.and.returnValue(of(mockResponse));

    fixture.componentInstance.loadRecentMatches();
    fixture.detectChanges();

    expect(matchServiceMock.getRecentMatches).toHaveBeenCalledWith(8);
    expect(fixture.componentInstance.matches().length).toBe(1);
    expect(fixture.componentInstance.hasLoaded()).toBeTrue();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('t20-match-1.json');
    expect(compiled.textContent).toContain('Match #101');
  });

  it('should show error banner when MatchService fails', () => {
    const fixture = TestBed.createComponent(AppComponent);
    matchServiceMock.getRecentMatches.and.returnValue(
      throwError(() => ({ error: { message: 'Network error' } }))
    );

    fixture.componentInstance.loadRecentMatches();
    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage()).toContain('Network error');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load matches: Network error');
  });

  it('should show "No matches found" when API returns empty list', () => {
    const fixture = TestBed.createComponent(AppComponent);
    matchServiceMock.getRecentMatches.and.returnValue(of({ matches: [] }));

    fixture.componentInstance.loadRecentMatches();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No matches found.');
  });
});
