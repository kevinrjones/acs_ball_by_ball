import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { csrfInterceptor } from './csrf.interceptor';

describe('csrfInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([csrfInterceptor])),
        provideHttpClientTesting()
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('attaches X-CSRF and withCredentials to relative requests', () => {
    http.get('/api/matches').subscribe();

    const req = httpMock.expectOne('/api/matches');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.headers.get('X-CSRF')).toBe('1');
    req.flush({});
  });

  it('preserves existing X-CSRF header on relative requests', () => {
    http.get('/api/matches', { headers: { 'X-CSRF': 'custom-token' } }).subscribe();

    const req = httpMock.expectOne('/api/matches');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.headers.get('X-CSRF')).toBe('custom-token');
    req.flush({});
  });

  it('does not attach X-CSRF or withCredentials to external requests', () => {
    const externalUrl = 'https://fonts.googleapis.com/css2?family=Roboto';
    http.get(externalUrl).subscribe();

    const req = httpMock.expectOne(externalUrl);
    expect(req.request.withCredentials).toBeFalse();
    expect(req.request.headers.has('X-CSRF')).toBeFalse();
    req.flush('');
  });
});
