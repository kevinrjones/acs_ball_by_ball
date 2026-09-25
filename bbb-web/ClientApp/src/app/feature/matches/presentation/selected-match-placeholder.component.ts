import {Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {parseMatchSearchQuery, serializeMatchSearchQuery} from '../../../models/match-search-query.codec';

@Component({
  selector: 'app-selected-match-placeholder',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './selected-match-placeholder.component.html'
})
export class SelectedMatchPlaceholderComponent implements OnInit {
  readonly matchKey = signal<string | null>(null);
  readonly isValidMatchKey = signal(false);
  readonly returnQueryParams = signal<Record<string, string>>({});

  private readonly activatedRoute = inject(ActivatedRoute);

  ngOnInit(): void {
    const rawMatchKey = this.activatedRoute.snapshot.paramMap.get('matchKey');
    const query = this.activatedRoute.snapshot.queryParamMap;
    const parsedQuery = parseMatchSearchQuery(query);
    this.returnQueryParams.set(parsedQuery.query ? serializeMatchSearchQuery(parsedQuery.query) : {});
    this.matchKey.set(rawMatchKey);
    this.isValidMatchKey.set(rawMatchKey !== null && /^[1-9]\d*$/.test(rawMatchKey));
  }
}