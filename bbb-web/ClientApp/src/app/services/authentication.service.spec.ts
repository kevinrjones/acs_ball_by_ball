import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthenticationService, Session, UserProfileResponse } from './authentication.service';
import { Envelope } from '../models/envelope.model';

describe('AuthenticationService', () => {
  let service: AuthenticationService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthenticationService
      ]
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created and compute anonymous state when /bff/user returns 401', () => {
    service = TestBed.inject(AuthenticationService);
    const req = httpTesting.expectOne('/bff/user');
    expect(req.request.method).toBe('GET');
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(service).toBeTruthy();
    expect(service.isAnonymous()).toBeTrue();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.userName()).toBeNull();
    expect(service.email()).toBeNull();
    expect(service.logoutUrl()).toBeUndefined();
  });

  it('should compute authenticated state and claims when /bff/user returns session claims', () => {
    service = TestBed.inject(AuthenticationService);
    const mockSession: Session = {
      claims: [
        { type: 'sub', value: 'user-guid-999' },
        { type: 'name', value: 'Kevin Jones' },
        { type: 'email', value: 'kevin@knowledgespike.com' },
        { type: 'bff:logout_url', value: '/bff/logout?sid=test-csrf' }
      ],
      csrfToken: 'test-csrf'
    };

    const req = httpTesting.expectOne('/bff/user');
    expect(req.request.method).toBe('GET');
    req.flush(mockSession);

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.isAnonymous()).toBeFalse();
    expect(service.userName()).toBe('Kevin Jones');
    expect(service.email()).toBe('kevin@knowledgespike.com');
    expect(service.logoutUrl()).toBe('/bff/logout?sid=test-csrf');
  });

  it('should request /api/user/profile via proxy', () => {
    service = TestBed.inject(AuthenticationService);
    const initReq = httpTesting.expectOne('/bff/user');
    initReq.flush(null);

    const mockProfile: Envelope<UserProfileResponse> = {
      result: {
        subject: 'user-guid-999',
        name: 'Kevin Jones',
        email: 'kevin@knowledgespike.com',
        roles: ['BB.User']
      },
      errorMessage: '',
      timeGenerated: new Date().toISOString()
    };

    service.getUserProfile().subscribe((envelope) => {
      expect(envelope.result).toEqual(mockProfile.result);
      expect(envelope.result.roles).toContain('BB.User');
    });

    const req = httpTesting.expectOne('/api/user/profile');
    expect(req.request.method).toBe('GET');
    req.flush(mockProfile);
  });
});
