import {ChangeDetectionStrategy, Component, Input} from '@angular/core';

/**
 * Reusable loading placeholder for card grids. Renders `count` skeleton cards
 * that mirror the real catalog/dashboard card shape (title + line + chips), so
 * pages fade in gracefully instead of popping from blank to content.
 *
 * Usage: <app-card-skeleton [count]="6" size="6"></app-card-skeleton>
 */
@Component({
  selector: 'app-card-skeleton',
  template: `
    <ion-row aria-hidden="true">
      @for (i of items; track i) {
        <ion-col [attr.size]="size">
          <div class="skeleton-card">
            <ion-skeleton-text [animated]="true" class="sk-title"></ion-skeleton-text>
            <ion-skeleton-text [animated]="true" class="sk-line"></ion-skeleton-text>
            <div class="sk-chips">
              <ion-skeleton-text [animated]="true" class="sk-chip"></ion-skeleton-text>
              <ion-skeleton-text [animated]="true" class="sk-chip"></ion-skeleton-text>
            </div>
          </div>
        </ion-col>
      }
    </ion-row>
  `,
  styleUrls: ['./card-skeleton.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false,
})
export class CardSkeletonComponent {
  @Input() count = 6;
  @Input() size = '6';

  get items(): number[] {
    return Array.from({length: this.count}, (_, i) => i);
  }
}
