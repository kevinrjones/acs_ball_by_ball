import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, Signal } from '@angular/core';
import { catchError, defer, map, Observable, of, shareReplay } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';

export interface Claim {
  type: string;
  value: string;
  valueType?: string;
}

export type Session = { claims: Claim[]; csrfToken?: string } | null;
export type AuthenticationState = { session: Session; unavailable: boolean };

const ANONYMOUS: Session = null;
const CACHE_SIZE = 1;

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {
  private sessionState$: Observable<AuthenticationState> | null = null;
  private readonly http = inject(HttpClient);

  public getSession(ignoreCache = false): Observable<Session> {
    return this.getSessionState(ignoreCache).pipe(map((state) => state.session));
  }

  public getSessionState(ignoreCache = false): Observable<AuthenticationState> {
    if (!this.sessionState$ || ignoreCache) {
      this.sessionState$ = this.http.get<Session>('/bff/user').pipe(
        map((session) => ({session, unavailable: false})),
        catchError((error: {status?: number}) => of({
          session: ANONYMOUS,
          unavailable: error.status !== 401
        })),
        shareReplay(CACHE_SIZE)
      );
    }
    return this.sessionState$;
  }

  public readonly session: Signal<Session> = toSignal(
    defer(() => this.getSession()),
    { initialValue: ANONYMOUS }
  );

  public readonly isAnonymous = computed(() => this.session() === null);
  public readonly isAuthenticated = computed(() => this.session() !== null);

  public readonly userName = computed(() => {
    const s = this.session();
    return s ? s.claims.find((c) => c.type === 'name')?.value || null : null;
  });

  public readonly email = computed(() => {
    const s = this.session();
    return s ? s.claims.find((c) => c.type === 'email')?.value || null : null;
  });

  public readonly logoutUrl = computed(() => {
    const s = this.session();
    return s ? s.claims.find((c) => c.type === 'bff:logout_url')?.value || undefined : undefined;
  });

}
