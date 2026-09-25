import {ParamMap, Params} from '@angular/router';
import {
  MatchResult,
  MatchSearchFilters,
  MatchSearchQuery,
  MatchType,
  Venue
} from './match.model';

export const DEFAULT_PAGE = 1;
export const DEFAULT_PAGE_SIZE = 20;

export interface SearchOption<T extends string | number> {
  readonly value: T;
  readonly label: string;
}

export const VENUE_OPTIONS: readonly SearchOption<Venue>[] = [
  {value: 0, label: 'All venues'},
  {value: 1, label: 'Home'},
  {value: 2, label: 'Away'}
];

export const MATCH_TYPE_OPTIONS: readonly SearchOption<MatchType>[] = [
  {value: 'all', label: 'All matches'},
  {value: 't', label: 'Tests'},
  {value: 'o', label: 'ODIs'},
  {value: 'itt', label: 'International T20'},
  {value: 'f', label: 'First Class'},
  {value: 'a', label: 'List-A'},
  {value: 'tt', label: 'T20'},
  {value: 'wt', label: "Women's Tests"},
  {value: 'wo', label: "Women's ODIs"},
  {value: 'witt', label: "Women's International T20"},
  {value: 'wf', label: "Women's First Class"},
  {value: 'wa', label: "Women's List-A"},
  {value: 'wtt', label: "Women's T20"},
  {value: 'sec', label: 'Second XI Championship'}
];

export const MATCH_RESULT_OPTIONS: readonly SearchOption<MatchResult>[] = [
  {value: 0, label: 'All results'},
  {value: 1, label: 'Won'},
  {value: 2, label: 'Won by innings'},
  {value: 3, label: 'Won by runs'},
  {value: 4, label: 'Won by wickets'},
  {value: 5, label: 'Lost'},
  {value: 6, label: 'Lost by innings'},
  {value: 7, label: 'Lost by runs'},
  {value: 8, label: 'Lost by wickets'},
  {value: 9, label: 'Match drawn'},
  {value: 10, label: 'Tied'},
  {value: 11, label: 'No result'}
];

export const EMPTY_MATCH_SEARCH_FILTERS: MatchSearchFilters = {
  team: '',
  teamExactMatch: false,
  opponents: '',
  opponentsExactMatch: false,
  venue: 0,
  startDate: '',
  endDate: '',
  matchType: 'all',
  matchResult: 0
};

export interface MatchSearchQueryParseResult {
  readonly query: MatchSearchQuery | null;
  readonly error: string | null;
}

const QUERY_PARAMETER_NAMES = [
  'team', 'teamExactMatch', 'opponents', 'opponentsExactMatch', 'venue',
  'startDate', 'endDate', 'matchType', 'matchResult', 'page', 'pageSize'
] as const;

export function parseMatchSearchQuery(params: ParamMap): MatchSearchQueryParseResult {
  if (!QUERY_PARAMETER_NAMES.some((name) => params.has(name))) {
    return {query: null, error: null};
  }

  const query: MatchSearchQuery = {
    team: params.get('team')?.trim() ?? '',
    teamExactMatch: parseBoolean(params.get('teamExactMatch')) ?? false,
    opponents: params.get('opponents')?.trim() ?? '',
    opponentsExactMatch: parseBoolean(params.get('opponentsExactMatch')) ?? false,
    venue: parseNumber(params.get('venue'), 0) as Venue,
    startDate: params.get('startDate') ?? '',
    endDate: params.get('endDate') ?? '',
    matchType: (params.get('matchType') ?? 'all') as MatchType,
    matchResult: parseNumber(params.get('matchResult'), 0) as MatchResult,
    page: parseNumber(params.get('page'), DEFAULT_PAGE),
    pageSize: parseNumber(params.get('pageSize'), DEFAULT_PAGE_SIZE)
  };

  const error = validateMatchSearchQuery(query, params);
  return error ? {query: null, error} : {query, error: null};
}

export function serializeMatchSearchQuery(query: MatchSearchQuery): Params {
  return {
    team: query.team,
    teamExactMatch: String(query.teamExactMatch),
    opponents: query.opponents,
    opponentsExactMatch: String(query.opponentsExactMatch),
    venue: String(query.venue),
    startDate: query.startDate,
    endDate: query.endDate,
    matchType: query.matchType,
    matchResult: String(query.matchResult),
    page: String(query.page),
    pageSize: String(query.pageSize)
  };
}

export function queryFromFilters(filters: MatchSearchFilters): MatchSearchQuery {
  return {
    ...filters,
    page: DEFAULT_PAGE,
    pageSize: DEFAULT_PAGE_SIZE
  };
}

export function validateMatchSearchQuery(query: MatchSearchQuery, params?: ParamMap): string | null {
  if (query.team.length < 3 || query.team.length > 100 || query.opponents.length < 3 || query.opponents.length > 100) {
    return 'Enter a team and opponent name with at least 3 characters each.';
  }
  if (!isBooleanParameterValid(params, 'teamExactMatch') || !isBooleanParameterValid(params, 'opponentsExactMatch')) {
    return 'Exact-match filters must be true or false.';
  }
  if (!isVenue(query.venue)) {
    return 'The venue filter is invalid.';
  }
  if (!isValidDate(query.startDate) || !isValidDate(query.endDate)) {
    return 'Dates must use the YYYY-MM-DD format.';
  }
  if (query.startDate && query.endDate && query.startDate > query.endDate) {
    return 'The start date must be on or before the end date.';
  }
  if (!isMatchType(query.matchType)) {
    return 'The match type filter is invalid.';
  }
  if (!isMatchResult(query.matchResult)) {
    return 'The match result filter is invalid.';
  }
  if (!Number.isInteger(query.page) || query.page < 1 || query.page > 10_000) {
    return 'The page must be between 1 and 10000.';
  }
  if (!Number.isInteger(query.pageSize) || query.pageSize < 1 || query.pageSize > 50) {
    return 'The page size must be between 1 and 50.';
  }
  return null;
}

function parseBoolean(value: string | null): boolean | null {
  if (value === null) {
    return null;
  }
  return value === 'true' ? true : value === 'false' ? false : null;
}

function isBooleanParameterValid(params: ParamMap | undefined, name: string): boolean {
  return !params?.has(name) || parseBoolean(params.get(name)) !== null;
}

function parseNumber(value: string | null, fallback: number): number {
  if (value === null) {
    return fallback;
  }
  if (value.trim() === '') {
    return Number.NaN;
  }
  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : Number.NaN;
}

function isVenue(value: number): value is Venue {
  return VENUE_OPTIONS.some((option) => option.value === value);
}

function isMatchType(value: string): value is MatchType {
  return MATCH_TYPE_OPTIONS.some((option) => option.value === value);
}

function isMatchResult(value: number): value is MatchResult {
  return MATCH_RESULT_OPTIONS.some((option) => option.value === value);
}

function isValidDate(value: string): boolean {
  if (!value) {
    return true;
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}