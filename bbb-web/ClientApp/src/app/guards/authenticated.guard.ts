import {inject} from '@angular/core';
import {CanActivateFn} from '@angular/router';
import {map} from 'rxjs';
import {AuthenticationService} from '../services/authentication.service';

export const authenticatedGuard: CanActivateFn = () => {
  const authenticationService = inject(AuthenticationService);

  return authenticationService.getSession().pipe(
    map((session) => {
      if (session) {
        return true;
      }

      window.location.assign('/bff/login');
      return false;
    })
  );
};