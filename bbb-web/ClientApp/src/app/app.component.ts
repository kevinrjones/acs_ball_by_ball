import {Component, HostListener, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {AuthenticationService} from './services/authentication.service';
import {ApplicationMetadataService} from './services/application-metadata.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  private readonly applicationMetadataService = inject(ApplicationMetadataService);
  public readonly authService = inject(AuthenticationService);

  readonly applicationMetadata = this.applicationMetadataService.metadata;
  readonly isUserMenuOpen = signal<boolean>(false);

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
