import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Envelope } from '../models/envelope.model';
import { ApplicationMetadata } from '../models/application-metadata.model';

@Injectable({
  providedIn: 'root'
})
export class ApplicationMetadataService {
  private readonly http = inject(HttpClient);

  getMetadata(): Observable<Envelope<ApplicationMetadata>> {
    return this.http.get<Envelope<ApplicationMetadata>>('/api/metadata');
  }
}