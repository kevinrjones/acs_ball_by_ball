import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, Router, UrlTree} from '@angular/router';
import {NEVER, of} from 'rxjs';
import {MatchSearchComponent} from './match-search.component';

describe('MatchSearchComponent', () => {
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate', 'createUrlTree', 'serializeUrl']);
    router.navigate.and.returnValue(Promise.resolve(true));
    Object.defineProperty(router, 'events', {value: NEVER});
    router.createUrlTree.and.returnValue({} as UrlTree);
    router.serializeUrl.and.returnValue('');

    await TestBed.configureTestingModule({
      imports: [MatchSearchComponent],
      providers: [
        {provide: Router, useValue: router},
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(convertToParamMap({
              team: 'India', opponents: 'Pakistan', teamExactMatch: 'true',
              opponentsExactMatch: 'false', venue: '0', matchType: 'itt', matchResult: '0'
            }))
          }
        }
      ]
    }).compileComponents();
  });

  it('should preserve every filter in the search history entry before navigating to results', async () => {
    const fixture = TestBed.createComponent(MatchSearchComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.filters()).toEqual(jasmine.objectContaining({
      team: 'India', opponents: 'Pakistan', teamExactMatch: true
    }));
    const input = fixture.nativeElement.querySelector('input[name="team"]') as HTMLInputElement;
    expect(input.value).toBe('India');
    expect((fixture.nativeElement.querySelector('#match-search-type') as HTMLSelectElement).value).toBe('itt');
    expect((fixture.nativeElement.querySelector('#match-search-result') as HTMLSelectElement).value).toBe('0');

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    const submitEvent = new Event('submit', {bubbles: true, cancelable: true});
    form.dispatchEvent(submitEvent);
    await fixture.whenStable();

    expect(submitEvent.defaultPrevented).toBeTrue();
    expect(router.navigate.calls.allArgs()).toEqual([
      [[], {
        relativeTo: jasmine.anything(),
        queryParams: {
          team: 'India', teamExactMatch: 'true', opponents: 'Pakistan', opponentsExactMatch: 'false',
          venue: '0', startDate: '', endDate: '', matchType: 'itt', matchResult: '0', page: '1', pageSize: '20'
        },
        replaceUrl: true
      }],
      [
      ['/matches/results'],
      {
        queryParams: {
          team: 'India', teamExactMatch: 'true', opponents: 'Pakistan', opponentsExactMatch: 'false',
          venue: '0', startDate: '', endDate: '', matchType: 'itt', matchResult: '0', page: '1', pageSize: '20'
        }
      }
      ]
    ]);
  });

  it('should tab through each team name before its exact-match checkbox', () => {
    const fixture = TestBed.createComponent(MatchSearchComponent);
    fixture.detectChanges();

    const controls = Array.from(
      fixture.nativeElement.querySelectorAll('.search-matchup-grid input')
    ) as HTMLInputElement[];

    expect(controls.map((control) => control.id)).toEqual([
      'match-search-team', 'team-exact-match', 'match-search-opponents', 'opponents-exact-match'
    ]);
  });

  it('should reject missing team fields and reversed dates without navigating', () => {
    const fixture = TestBed.createComponent(MatchSearchComponent);
    fixture.detectChanges();

    fixture.componentInstance.resetSearch();
    fixture.componentInstance.submitSearch();
    expect(fixture.componentInstance.errorMessage()).toBe('Enter a team and opponent name with at least 3 characters each.');

    fixture.componentInstance.filters.update((filters) => ({
      ...filters, team: 'India', opponents: 'Pakistan', startDate: '2025-01-01', endDate: '2024-01-01'
    }));
    fixture.componentInstance.submitSearch();
    expect(fixture.componentInstance.errorMessage()).toBe('The start date must be on or before the end date.');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should disable search until both team names have at least three characters', () => {
    const fixture = TestBed.createComponent(MatchSearchComponent);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button.disabled).toBeFalse();

    fixture.componentInstance.resetSearch();
    fixture.detectChanges();
    expect(button.disabled).toBeTrue();

    fixture.componentInstance.filters.update((filters) => ({...filters, team: 'India'}));
    fixture.detectChanges();
    expect(button.disabled).toBeTrue();

    fixture.componentInstance.filters.update((filters) => ({...filters, opponents: 'Pakistan'}));
    fixture.detectChanges();
    expect(button.disabled).toBeFalse();
  });

  it('should show an error for each name that has fewer than three characters', () => {
    const fixture = TestBed.createComponent(MatchSearchComponent);
    fixture.detectChanges();

    const teamInput = fixture.nativeElement.querySelector('input[name="team"]') as HTMLInputElement;
    const opponentsInput = fixture.nativeElement.querySelector('input[name="opponents"]') as HTMLInputElement;
    teamInput.value = 'ab';
    teamInput.dispatchEvent(new Event('input', {bubbles: true}));
    opponentsInput.value = 'x';
    opponentsInput.dispatchEvent(new Event('input', {bubbles: true}));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#match-search-team-error')?.textContent).toContain(
      'The name must be at least 3 characters'
    );
    expect(fixture.nativeElement.querySelector('#match-search-opponents-error')?.textContent).toContain(
      'The name must be at least 3 characters'
    );
    expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBeTrue();
  });

  it('should disable the submit button after typing an invalid team name', () => {
    const fixture = TestBed.createComponent(MatchSearchComponent);
    fixture.detectChanges();

    const teamInput = fixture.nativeElement.querySelector('input[name="team"]') as HTMLInputElement;
    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    teamInput.value = 'ab';
    teamInput.dispatchEvent(new Event('input', {bubbles: true}));
    fixture.detectChanges();

    expect(button.disabled).toBeTrue();
    button.click();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should apply a preset to the visible card filters', () => {
    const fixture = TestBed.createComponent(MatchSearchComponent);
    fixture.detectChanges();

    fixture.componentInstance.applyPreset(fixture.componentInstance.presets[0]);
    expect(fixture.componentInstance.filters()).toEqual(jasmine.objectContaining({
      team: 'England', opponents: 'Australia', matchType: 't', startDate: '2023-06-01'
    }));
  });
});