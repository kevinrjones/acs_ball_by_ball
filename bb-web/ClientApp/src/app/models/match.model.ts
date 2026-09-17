export interface MatchSummary {
  matchKey: number;
  sourceMatchId: number;
  fileName: string;
  matchType: string;
  season: string;
}

export interface RecentMatchesResponse {
  matches: MatchSummary[];
}

export interface ApiError {
  code: string;
  message: string;
}
