import {inject} from '@angular/core';
import {CanActivateFn} from '@angular/router';
import {map} from 'rxjs';
import {AuthenticationService} from '../services/authentication.service';

export const authenticatedGuard: CanActivateFn = () => {
  const authenticationService = inject(AuthenticationService);

  return authenticationService.getSessionState().pipe(
    map((state) => {
      if (state.session) {
        return true;
      }

      if (state.unavailable) {
        return false;
      }

      window.location.assign('/bff/login');
      return false;
    })
  );
};