import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MatchSearchQuery, MatchSearchResponse, RecentMatchesResponse } from '../models/match.model';
import { Envelope } from '../models/envelope.model';

@Injectable({
  providedIn: 'root'
})
export class MatchService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/matches';

  getRecentMatches(limit?: number): Observable<Envelope<RecentMatchesResponse>> {
    let params = new HttpParams();
    if (limit !== undefined && limit !== null) {
      params = params.set('limit', limit.toString());
    }
    return this.http.get<Envelope<RecentMatchesResponse>>(this.apiUrl, { params });
  }

  searchMatches(query: MatchSearchQuery): Observable<Envelope<MatchSearchResponse>> {
    const params = new HttpParams()
      .set('team', query.team)
      .set('teamExactMatch', query.teamExactMatch.toString())
      .set('opponents', query.opponents)
      .set('opponentsExactMatch', query.opponentsExactMatch.toString())
      .set('venue', query.venue.toString())
      .set('startDate', query.startDate)
      .set('endDate', query.endDate)
      .set('matchType', query.matchType)
      .set('matchResult', query.matchResult.toString())
      .set('page', query.page.toString())
      .set('pageSize', query.pageSize.toString());

    return this.http.get<Envelope<MatchSearchResponse>>(`${this.apiUrl}/search`, { params });
  }
}
