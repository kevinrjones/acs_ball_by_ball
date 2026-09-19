export interface SampleMatch {
  competition: string;
  badgeClass: string;
  date: string;
  team1: string;
  score1: string;
  overs1?: string;
  isTeam1Winner?: boolean;
  team2: string;
  score2: string;
  overs2?: string;
  isTeam2Winner?: boolean;
  result: string;
  format: string;
}

export const SAMPLE_MATCHES: SampleMatch[] = [
  {
    competition: "Women's Twenty20 Asia Cup 2026",
    badgeClass: 'text-emerald-800 bg-emerald-50 border-emerald-100',
    date: '10 Sept 2026',
    team1: 'Bangladesh Women',
    score1: '109-7',
    overs1: '(20ov)',
    team2: 'India Women',
    score2: '149-6',
    overs2: '(20ov)',
    isTeam2Winner: true,
    result: 'India Women won by 40 runs',
    format: "women's intt20"
  },
  {
    competition: 'European T20 Premier League 2026',
    badgeClass: 'text-slate-700 bg-slate-100 border-slate-200',
    date: '10 Sept 2026',
    team1: 'Belfast Wolves',
    score1: '144-5',
    overs1: '(19ov)',
    isTeam1Winner: true,
    team2: 'Amsterdam Flames',
    score2: '141ao',
    overs2: '(20ov)',
    result: 'Belfast Wolves won by 5 wickets',
    format: 't20'
  },
  {
    competition: 'Africa Continental Cup 2026/27',
    badgeClass: 'text-amber-900 bg-amber-50 border-amber-200/70',
    date: '10 Sept 2026',
    team1: 'Botswana',
    score1: '101-7',
    overs1: '(20ov)',
    team2: 'Uganda',
    score2: '105-3',
    overs2: '(13.2ov)',
    isTeam2Winner: true,
    result: 'Uganda won by 7 wickets',
    format: 't20'
  },
  {
    competition: "Women's Caribbean Premier League 2026",
    badgeClass: 'text-rose-900 bg-rose-50 border-rose-200/70',
    date: '10 Sept 2026',
    team1: 'Guyana Amazon Warriors Women',
    score1: '103ao',
    overs1: '(18.5ov)',
    team2: 'Trinbago Knight Riders Women',
    score2: '104-1',
    overs2: '(11.1ov)',
    isTeam2Winner: true,
    result: 'Trinbago Knight Riders Women won by 9 wickets',
    format: "women's t20"
  },
  {
    competition: 'Rothesay County Championship 2026',
    badgeClass: 'text-indigo-900 bg-indigo-50 border-indigo-200',
    date: '10 Sept 2026',
    team1: 'Leicestershire',
    score1: '157ao & 145ao',
    team2: 'Somerset',
    score2: '363ao & 316-5d',
    isTeam2Winner: true,
    result: 'Somerset won by 377 runs',
    format: 'fc'
  },
  {
    competition: "Asian Cricket Council Men's Premier Cup 2026",
    badgeClass: 'text-blue-900 bg-blue-50 border-blue-200',
    date: '10 Sept 2026',
    team1: 'Nepal',
    score1: '147ao',
    overs1: '(45.4ov)',
    isTeam1Winner: true,
    team2: 'Oman',
    score2: '60ao',
    overs2: '(28.1ov)',
    result: 'Nepal won by 87 runs',
    format: 'lista'
  }
];
