import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, Signal } from '@angular/core';
import { catchError, defer, Observable, of, shareReplay } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { Envelope } from '../models/envelope.model';

export interface Claim {
  type: string;
  value: string;
  valueType?: string;
}

export type Session = { claims: Claim[]; csrfToken?: string } | null;

export interface UserProfileResponse {
  subject: string;
  name?: string | null;
  email?: string | null;
  roles?: string[];
}

const ANONYMOUS: Session = null;
const CACHE_SIZE = 1;

@Injectable({
  providedIn: 'root'
})
export class AuthenticationService {
  private session$: Observable<Session> | null = null;
  private readonly http = inject(HttpClient);

  public getSession(ignoreCache = false): Observable<Session> {
    if (!this.session$ || ignoreCache) {
      this.session$ = this.http.get<Session>('/bff/user').pipe(
        catchError(() => of(ANONYMOUS)),
        shareReplay(CACHE_SIZE)
      );
    }
    return this.session$;
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

  public getUserProfile(): Observable<Envelope<UserProfileResponse>> {
    return this.http.get<Envelope<UserProfileResponse>>('/api/user/profile');
  }
}
