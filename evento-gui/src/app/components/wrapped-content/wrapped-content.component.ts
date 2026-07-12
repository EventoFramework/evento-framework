import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';

@Component({
    selector: 'app-wrapped-content',
    templateUrl: './wrapped-content.component.html',
    styleUrls: ['./wrapped-content.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class WrappedContentComponent implements OnInit {

  constructor() { }

  ngOnInit() {}

}
