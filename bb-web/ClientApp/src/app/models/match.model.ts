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

export interface ApiError {
  code: string;
  message: string;
}
