import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap, Router, UrlTree} from '@angular/router';
import {NEVER, of} from 'rxjs';
import {MatchSearchComponent} from './match-search.component';

describe('MatchSearchComponent', () => {
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate', 'createUrlTree', 'serializeUrl']);
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

  it('should restore card filters from route state and navigate to results', () => {
    const fixture = TestBed.createComponent(MatchSearchComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.filters()).toEqual(jasmine.objectContaining({
      team: 'India', opponents: 'Pakistan', teamExactMatch: true
    }));
    const input = fixture.nativeElement.querySelector('input[name="team"]') as HTMLInputElement;
    expect(input.value).toBe('India');

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    const submitEvent = new Event('submit', {bubbles: true, cancelable: true});
    form.dispatchEvent(submitEvent);

    expect(submitEvent.defaultPrevented).toBeTrue();
    expect(router.navigate).toHaveBeenCalledWith(
      ['/matches/results'],
      {
        queryParams: {
          team: 'India', teamExactMatch: 'true', opponents: 'Pakistan', opponentsExactMatch: 'false',
          venue: '0', startDate: '', endDate: '', matchType: 'itt', matchResult: '0', page: '1', pageSize: '20'
        }
      }
    );
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

  it('should apply a preset to the visible card filters', () => {
    const fixture = TestBed.createComponent(MatchSearchComponent);
    fixture.detectChanges();

    fixture.componentInstance.applyPreset(fixture.componentInstance.presets[0]);
    expect(fixture.componentInstance.filters()).toEqual(jasmine.objectContaining({
      team: 'England', opponents: 'Australia', matchType: 't', startDate: '2023-06-01'
    }));
  });
});