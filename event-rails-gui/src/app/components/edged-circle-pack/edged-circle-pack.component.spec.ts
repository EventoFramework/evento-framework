import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { IonicModule } from '@ionic/angular';

import { EdgedCirclePackComponent } from './edged-circle-pack.component';

describe('EdgedCirclePackComponent', () => {
  let component: EdgedCirclePackComponent;
  let fixture: ComponentFixture<EdgedCirclePackComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ EdgedCirclePackComponent ],
      imports: [IonicModule.forRoot()]
    }).compileComponents();

    fixture = TestBed.createComponent(EdgedCirclePackComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
