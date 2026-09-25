import {Component, HostListener, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink, RouterOutlet} from '@angular/router';
import {AuthenticationService} from './services/authentication.service';
import {ApplicationMetadataService} from './services/application-metadata.service';
import {ApplicationMetadata} from './models/application-metadata.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  private readonly applicationMetadataService = inject(ApplicationMetadataService);
  public readonly authService = inject(AuthenticationService);

  readonly applicationMetadata = signal<ApplicationMetadata | null>(null);
  readonly isUserMenuOpen = signal<boolean>(false);

  ngOnInit(): void {
    this.loadApplicationMetadata();
  }

  loadApplicationMetadata(): void {
    this.applicationMetadataService.getMetadata().subscribe({
      next: (response) => this.applicationMetadata.set(response.result),
      error: () => this.applicationMetadata.set(null)
    });
  }


  toggleUserMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.isUserMenuOpen.update((isOpen) => !isOpen);
  }

  closeUserMenu(): void {
    this.isUserMenuOpen.set(false);
  }

  @HostListener('document:click')
  handleDocumentClick(): void {
    this.closeUserMenu();
  }

  @HostListener('document:keydown.escape')
  handleEscapeKey(): void {
    this.closeUserMenu();
  }
}
