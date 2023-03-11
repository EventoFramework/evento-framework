import { Component, OnInit } from '@angular/core';
import {CatalogService} from "../../../services/catalog.service";

@Component({
  selector: 'app-library-page',
  templateUrl: './library-page.page.html',
  styleUrls: ['./library-page.page.scss'],
})
export class LibraryPagePage implements OnInit {
  elements = [];
  element: any;

  constructor(private libraryService: CatalogService) { }

  async ngOnInit() {
    this.elements = await this.libraryService.findAllPayload();
  }

}
