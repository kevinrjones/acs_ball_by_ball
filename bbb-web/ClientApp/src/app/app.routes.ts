import { Routes } from '@angular/router';
import {HomeComponent} from './feature/home/presentation/home.component';
import {MatchSearchComponent} from './feature/matches/presentation/match-search.component';
import {MatchResultsComponent} from './feature/matches/presentation/match-results.component';
import {SelectedMatchPlaceholderComponent} from './feature/matches/presentation/selected-match-placeholder.component';
import {authenticatedGuard} from './guards/authenticated.guard';

export const routes: Routes = [
  {path: '', component: HomeComponent},
  {path: 'matches/search', component: MatchSearchComponent, canActivate: [authenticatedGuard]},
  {path: 'matches/results', component: MatchResultsComponent, canActivate: [authenticatedGuard]},
  {path: 'matches/:publicMatchId/scoresheet', component: SelectedMatchPlaceholderComponent, canActivate: [authenticatedGuard]}
];
