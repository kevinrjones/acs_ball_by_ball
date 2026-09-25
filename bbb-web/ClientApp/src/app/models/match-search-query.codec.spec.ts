import {convertToParamMap} from '@angular/router';
import {
  parseMatchSearchQuery,
  serializeMatchSearchQuery
} from './match-search-query.codec';

describe('match search query codec', () => {
  const validParams = {
    team: 'India',
    teamExactMatch: 'true',
    opponents: 'Pakistan',
    opponentsExactMatch: 'false',
    venue: '2',
    startDate: '2023-01-01',
    endDate: '2023-12-31',
    matchType: 'itt',
    matchResult: '3',
    page: '2',
    pageSize: '10'
  };

  it('should parse and serialize every structured filter without loss', () => {
    const parsed = parseMatchSearchQuery(convertToParamMap(validParams));

    expect(parsed.error).toBeNull();
    expect(parsed.query).toEqual({
      team: 'India', teamExactMatch: true, opponents: 'Pakistan', opponentsExactMatch: false,
      venue: 2, startDate: '2023-01-01', endDate: '2023-12-31', matchType: 'itt', matchResult: 3,
      page: 2, pageSize: 10
    });
    expect(serializeMatchSearchQuery(parsed.query!)).toEqual(validParams);
  });

  it('should reject invalid calendar dates and reversed ranges', () => {
    expect(parseMatchSearchQuery(convertToParamMap({...validParams, startDate: '2023-02-29'})).error)
      .toBe('Dates must use the YYYY-MM-DD format.');
    expect(parseMatchSearchQuery(convertToParamMap({...validParams, startDate: '2024-12-31', endDate: '2024-01-01'})).error)
      .toBe('The start date must be on or before the end date.');
  });

  it('should reject malformed finite options and booleans', () => {
    expect(parseMatchSearchQuery(convertToParamMap({...validParams, venue: '3'})).error)
      .toBe('The venue filter is invalid.');
    expect(parseMatchSearchQuery(convertToParamMap({...validParams, venue: '4'})).error)
      .toBe('The venue filter is invalid.');
    expect(parseMatchSearchQuery(convertToParamMap({...validParams, matchType: 'unknown'})).error)
      .toBe('The match type filter is invalid.');
    expect(parseMatchSearchQuery(convertToParamMap({...validParams, teamExactMatch: 'yes'})).error)
      .toBe('Exact-match filters must be true or false.');
  });
});