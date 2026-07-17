import {Injectable, computed, effect, signal} from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'evento-theme';
const DARK_CLASS = 'ion-palette-dark';

/**
 * Manages the app theme (light / dark / follow-system).
 *
 * Dark mode is applied by toggling the `ion-palette-dark` class on <html>
 * (Ionic's class-based dark palette, imported in angular.json). The brand
 * palette + teal-tinted surfaces are re-asserted in theme/variables.scss.
 *
 * The initial class is set by an inline no-flash script in index.html; this
 * service takes over reactively once Angular bootstraps and persists the
 * user's explicit choice to localStorage.
 */
@Injectable({providedIn: 'root'})
export class ThemeService {
  private readonly media = window.matchMedia('(prefers-color-scheme: dark)');

  /** The user's chosen mode. `system` follows the OS preference. */
  readonly mode = signal<ThemeMode>(this.readStoredMode());
  /** Live OS preference. */
  readonly systemDark = signal<boolean>(this.media.matches);
  /** The effective theme after resolving `system`. */
  readonly isDark = computed(
    () => this.mode() === 'dark' || (this.mode() === 'system' && this.systemDark()),
  );

  constructor() {
    this.media.addEventListener('change', (e) => this.systemDark.set(e.matches));
    effect(() => this.applyDark(this.isDark()));
  }

  /** Set an explicit mode (or `system`) and persist it. */
  setMode(mode: ThemeMode): void {
    this.mode.set(mode);
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      /* storage unavailable — in-memory only */
    }
  }

  /** Flip between light and dark (drops `system`). */
  toggle(): void {
    this.setMode(this.isDark() ? 'light' : 'dark');
  }

  private applyDark(dark: boolean): void {
    document.documentElement.classList.toggle(DARK_CLASS, dark);
  }

  private readStoredMode(): ThemeMode {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'light' || stored === 'dark' || stored === 'system') {
        return stored;
      }
    } catch {
      /* ignore */
    }
    return 'system';
  }
}
