import {routes} from './app.routes';
import {HomeComponent} from './feature/home/presentation/home.component';
import {MatchSearchComponent} from './feature/matches/presentation/match-search.component';
import {MatchResultsComponent} from './feature/matches/presentation/match-results.component';
import {SelectedMatchPlaceholderComponent} from './feature/matches/presentation/selected-match-placeholder.component';
import {authenticatedGuard} from './guards/authenticated.guard';

describe('application routes', () => {
  it('should expose the match search and results routes', () => {
    expect(routes).toEqual(jasmine.arrayContaining([
      jasmine.objectContaining({path: 'matches/search', component: MatchSearchComponent}),
      jasmine.objectContaining({path: 'matches/results', component: MatchResultsComponent}),
      jasmine.objectContaining({path: 'matches/:matchKey', component: SelectedMatchPlaceholderComponent})
    ]));
  });

  it('should route the empty path to the home dashboard', () => {
    expect(routes.find((route) => route.path === '')?.component).toBe(HomeComponent);
  });

  it('should protect non-list match features with the authenticated guard', () => {
    expect(routes.find((route) => route.path === 'matches/search')?.canActivate).toContain(authenticatedGuard);
    expect(routes.find((route) => route.path === 'matches/results')?.canActivate).toContain(authenticatedGuard);
    expect(routes.find((route) => route.path === 'matches/:matchKey')?.canActivate).toContain(authenticatedGuard);
  });
});