import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap} from '@angular/router';
import {SelectedMatchPlaceholderComponent} from './selected-match-placeholder.component';

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
});

async function configure(query: Record<string, string>, matchKey = '101'): Promise<void> {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [SelectedMatchPlaceholderComponent],
    providers: [{
      provide: ActivatedRoute,
      useValue: {snapshot: {paramMap: convertToParamMap({matchKey}), queryParamMap: convertToParamMap(query)}}
    }]
  }).compileComponents();
}