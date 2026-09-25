import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Envelope } from '../models/envelope.model';
import { ApplicationMetadata } from '../models/application-metadata.model';

@Injectable({
  providedIn: 'root'
})
export class ApplicationMetadataService {
  private readonly http = inject(HttpClient);

  readonly metadata = signal<ApplicationMetadata | null>(null);

  getMetadata(): Observable<Envelope<ApplicationMetadata>> {
    return this.http.get<Envelope<ApplicationMetadata>>('/api/metadata');
  }

  loadMetadata(): void {
    this.getMetadata().subscribe({
      next: (response) => this.metadata.set(response.result),
      error: () => this.metadata.set(null)
    });
  }
}