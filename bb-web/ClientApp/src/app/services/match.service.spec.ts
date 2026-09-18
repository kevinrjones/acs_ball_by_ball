import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatchService } from './match.service';
import { RecentMatchesResponse } from '../models/match.model';
import { Envelope } from '../models/envelope.model';

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
            matchKey: 1,
            sourceMatchId: 101,
            matchType: 'T20',
            season: '2026',
            fileName: 'match1.json'
          }
        ]
      },
      errorMessage: '',
      timeGenerated: new Date().toISOString()
    };

    service.getRecentMatches(8).subscribe((response) => {
      expect(response.result.matches.length).toBe(1);
      expect(response.result.matches[0].matchKey).toBe(1);
      expect(response.errorMessage).toBe('');
    });

    const req = httpTesting.expectOne('/api/matches?limit=8');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });
});
