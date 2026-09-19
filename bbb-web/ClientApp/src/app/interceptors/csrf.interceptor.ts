import { HttpInterceptorFn } from '@angular/common/http';

export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  const isInternal = req.url.startsWith('/') ||
    (typeof window !== 'undefined' && req.url.startsWith(window.location.origin));

  if (!isInternal) {
    return next(req);
  }

  const cloned = req.clone({
    withCredentials: true,
    headers: req.headers.has('X-CSRF') ? req.headers : req.headers.set('X-CSRF', '1')
  });
  return next(cloned);
};
