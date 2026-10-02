import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatchService } from './match.service';
import { MatchSearchResponse, RecentMatchesResponse } from '../domain/match.model';
import { Envelope } from '../../../models/envelope.model';

describe('MatchService', () => {
  let service: MatchService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MatchService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(MatchService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call /api/matches with limit parameter and return envelope', () => {
    const mockResponse: Envelope<RecentMatchesResponse> = {
      result: {
        matches: [
          {
            publicMatchId: 1_000_000_001,
            matchType: 'T20',
            season: '2026',
            fileName: 'match1.json',
            competition: 'T20 Blast',
            date: '1 Sept 2026',
            team1: 'Surrey',
            score1: '160-4',
            overs1: '(20ov)',
            isTeam1Winner: true,
            team2: 'Somerset',
            score2: '150-8',
            overs2: '(20ov)',
            isTeam2Winner: false,
            result: 'Surrey won by 10 runs',
            format: 't20'
          }
        ]
      },
      errorMessage: '',
      timeGenerated: new Date().toISOString()
    };

    service.getRecentMatches(8).subscribe((response) => {
      expect(response.result.matches.length).toBe(1);
      expect(response.result.matches[0].publicMatchId).toBe(1_000_000_001);
      expect(response.errorMessage).toBe('');
    });

    const req = httpTesting.expectOne('/api/matches?limit=8');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should call the search endpoint with the card filters and bounded paging', () => {
    const mockResponse: Envelope<MatchSearchResponse> = {
      result: {
        matches: [],
        pagination: {
          page: 1,
          pageSize: 20,
          totalResults: 0,
          hasNext: false,
          nextPage: null
        }
      },
      errorMessage: '',
      timeGenerated: new Date().toISOString()
    };

    service.searchMatches({
      team: 'South Africa',
      teamExactMatch: true,
      opponents: 'India',
      opponentsExactMatch: false,
      venue: 0,
      startDate: '2024-01-01',
      endDate: '2024-12-31',
      matchType: 't',
      matchResult: 0,
      page: 1,
      pageSize: 20
    }).subscribe((response) => {
      expect(response.result.matches).toEqual([]);
    });

    const req = httpTesting.expectOne((request) =>
      request.url === '/api/matches/search' &&
      request.params.get('team') === 'South Africa' &&
      request.params.get('teamExactMatch') === 'true' &&
      request.params.get('opponents') === 'India' &&
      request.params.get('opponentsExactMatch') === 'false' &&
      request.params.get('venue') === '0' &&
      request.params.get('startDate') === '2024-01-01' &&
      request.params.get('endDate') === '2024-12-31' &&
      request.params.get('matchType') === 't' &&
      request.params.get('matchResult') === '0' &&
      request.params.get('page') === '1' &&
      request.params.get('pageSize') === '20'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should call the scoresheet endpoint with the public match ID', () => {
    service.getScoresheet(1_000_000_001).subscribe();

    const req = httpTesting.expectOne('/api/matches/1000000001/scoresheet');
    expect(req.request.method).toBe('GET');
    req.flush({result: {context: {publicMatchId: 1_000_000_001}, completeness: 'EMPTY', missingData: [], innings: []}});
  });
});
