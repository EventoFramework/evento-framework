import { Component, OnInit } from '@angular/core';
import {LibraryService} from "../../../services/library.service";

@Component({
  selector: 'app-library-page',
  templateUrl: './library-page.page.html',
  styleUrls: ['./library-page.page.scss'],
})
export class LibraryPagePage implements OnInit {
  elements = [];
  element: any;

  constructor(private libraryService: LibraryService) { }

  async ngOnInit() {
    this.elements = await this.libraryService.findAll();
  }

}
