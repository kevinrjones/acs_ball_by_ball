import {CommonModule} from '@angular/common';
import {Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {MatchSearchFilters, MatchSearchQuery} from '../../../models/match.model';
import {
  EMPTY_MATCH_SEARCH_FILTERS,
  MATCH_RESULT_OPTIONS,
  MATCH_TYPE_OPTIONS,
  MatchSearchQueryParseResult,
  queryFromFilters,
  parseMatchSearchQuery,
  serializeMatchSearchQuery,
  validateMatchSearchQuery,
  VENUE_OPTIONS
} from '../../../models/match-search-query.codec';

interface SearchPreset {
  readonly id: string;
  readonly label: string;
  readonly filters: MatchSearchFilters;
}

@Component({
  selector: 'app-match-search',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './match-search.component.html'
})
export class MatchSearchComponent implements OnInit {
  readonly filters = signal<MatchSearchFilters>({...EMPTY_MATCH_SEARCH_FILTERS});
  readonly errorMessage = signal<string | null>(null);

  readonly venueOptions = VENUE_OPTIONS;
  readonly matchTypeOptions = MATCH_TYPE_OPTIONS;
  readonly resultOptions = MATCH_RESULT_OPTIONS;

  readonly presets: readonly SearchPreset[] = [
    {
      id: 'ashes', label: 'Ashes 2023', filters: {
        team: 'England', opponents: 'Australia', teamExactMatch: true, opponentsExactMatch: true, venue: 0,
        startDate: '2023-06-01', endDate: '2023-08-05', matchType: 't', matchResult: 0
      }
    },
    {
      id: 'cwc', label: 'CWC final 2023', filters: {
        team: 'India', opponents: 'Australia', teamExactMatch: false, opponentsExactMatch: false, venue: 0,
        startDate: '2023-11-15', endDate: '2023-11-19', matchType: 'o', matchResult: 0
      }
    },
  ];

  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.activatedRoute.queryParamMap.subscribe((params) => {
      this.restoreFilters(parseMatchSearchQuery(params));
    });
  }

  onTeamInput(event: Event): void {
    this.updateFilters({team: this.inputValue(event)});
  }

  onOpponentsInput(event: Event): void {
    this.updateFilters({opponents: this.inputValue(event)});
  }

  onTeamExactMatchChange(event: Event): void {
    this.updateFilters({teamExactMatch: this.checkedValue(event)});
  }

  onOpponentsExactMatchChange(event: Event): void {
    this.updateFilters({opponentsExactMatch: this.checkedValue(event)});
  }

  onVenueChange(event: Event): void {
    this.updateFilters({venue: Number(this.inputValue(event)) as MatchSearchFilters['venue']});
  }

  onStartDateInput(event: Event): void {
    this.updateFilters({startDate: this.inputValue(event)});
  }

  onEndDateInput(event: Event): void {
    this.updateFilters({endDate: this.inputValue(event)});
  }

  onMatchTypeChange(event: Event): void {
    this.updateFilters({matchType: this.inputValue(event) as MatchSearchFilters['matchType']});
  }

  onMatchResultChange(event: Event): void {
    this.updateFilters({matchResult: Number(this.inputValue(event)) as MatchSearchFilters['matchResult']});
  }

  applyPreset(preset: SearchPreset): void {
    this.filters.set({...preset.filters});
    this.errorMessage.set(null);
  }

  resetSearch(): void {
    this.filters.set({...EMPTY_MATCH_SEARCH_FILTERS});
    this.errorMessage.set(null);
  }

  submitSearch(event?: Event): void {
    event?.preventDefault();
    const filters: MatchSearchFilters = {
      ...this.filters(),
      team: this.filters().team.trim(),
      opponents: this.filters().opponents.trim()
    };
    const query = queryFromFilters(filters);
    const error = validateMatchSearchQuery(query);
    if (error) {
      this.errorMessage.set(error);
      return;
    }

    this.filters.set(filters);
    this.errorMessage.set(null);
    void this.router.navigate(['/matches/results'], {queryParams: serializeMatchSearchQuery(query)});
  }

  private restoreFilters(result: MatchSearchQueryParseResult): void {
    if (result.error) {
      this.filters.set({...EMPTY_MATCH_SEARCH_FILTERS});
      this.errorMessage.set(result.error);
      return;
    }
    this.filters.set(result.query ? this.filtersFromQuery(result.query) : {...EMPTY_MATCH_SEARCH_FILTERS});
    this.errorMessage.set(null);
  }

  private filtersFromQuery(query: MatchSearchQuery): MatchSearchFilters {
    const {page: _page, pageSize: _pageSize, ...filters} = query;
    return filters;
  }

  private updateFilters(changes: Partial<MatchSearchFilters>): void {
    this.filters.update((filters) => ({...filters, ...changes}));
    this.errorMessage.set(null);
  }

  private inputValue(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  private checkedValue(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

}