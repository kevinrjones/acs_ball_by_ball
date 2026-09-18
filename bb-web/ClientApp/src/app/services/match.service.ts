import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RecentMatchesResponse } from '../models/match.model';
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
}
