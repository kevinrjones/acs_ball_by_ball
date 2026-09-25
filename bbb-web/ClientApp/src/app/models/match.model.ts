export interface MatchSummary {
  matchKey: number;
  sourceMatchId: number;
  fileName: string;
  matchType: string;
  season: string;
  competition?: string;
  date?: string;
  team1?: string;
  score1?: string;
  overs1?: string;
  isTeam1Winner?: boolean;
  team2?: string;
  score2?: string;
  overs2?: string;
  isTeam2Winner?: boolean;
  result?: string;
  format?: string;
}

export interface RecentMatchesResponse {
  matches: MatchSummary[];
}

export type Venue = 0 | 1 | 2;
export type MatchType = 'all' | 't' | 'o' | 'itt' | 'f' | 'a' | 'tt' | 'wt' | 'wo' | 'witt' | 'wf' | 'wa' | 'wtt' | 'sec';
export type MatchResult = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11;

export interface MatchSearchFilters {
  readonly team: string;
  readonly teamExactMatch: boolean;
  readonly opponents: string;
  readonly opponentsExactMatch: boolean;
  readonly venue: Venue;
  readonly startDate: string;
  readonly endDate: string;
  readonly matchType: MatchType;
  readonly matchResult: MatchResult;
}

export interface MatchSearchQuery extends MatchSearchFilters {
  readonly page: number;
  readonly pageSize: number;
}

export interface MatchSearchResult {
  readonly matchKey: number;
  readonly sourceMatchId: number;
  readonly fileName: string;
  readonly matchType?: string | null;
  readonly season?: string | null;
  readonly competition?: string | null;
  readonly date?: string | null;
  readonly team1?: string | null;
  readonly team2?: string | null;
  readonly ground?: string | null;
  readonly result?: string | null;
}

export interface MatchSearchPagination {
  readonly page: number;
  readonly pageSize: number;
  readonly totalResults: number;
  readonly hasNext: boolean;
  readonly nextPage?: number | null;
}

export interface MatchSearchResponse {
  readonly matches: MatchSearchResult[];
  readonly pagination: MatchSearchPagination;
}

export interface ApiError {
  code: string;
  message: string;
}
