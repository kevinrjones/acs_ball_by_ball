import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatchService } from './match.service';
import { RecentMatchesResponse } from '../models/match.model';

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

  it('should call /api/matches with limit parameter', () => {
    const mockResponse: RecentMatchesResponse = {
      matches: [
        {
          matchKey: 1,
          sourceMatchId: 101,
          matchType: 'T20',
          season: '2026',
          fileName: 'match1.json'
        }
      ]
    };

    service.getRecentMatches(8).subscribe((response) => {
      expect(response.matches.length).toBe(1);
      expect(response.matches[0].matchKey).toBe(1);
    });

    const req = httpTesting.expectOne('/api/matches?limit=8');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });
});
