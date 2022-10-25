import {Component, OnInit} from '@angular/core';
import {RanchService} from "../../../services/ranch.service";
import {ToastController} from "@ionic/angular";

@Component({
  selector: 'app-ranch-list',
  templateUrl: './ranch-list.page.html',
  styleUrls: ['./ranch-list.page.scss'],
})
export class RanchListPage implements OnInit {
  elements: any[] = [];
  loading: boolean = false; // Flag variable
  file: File = null; // Variable to store file

  constructor(private ranchService: RanchService,
              private toastController: ToastController) {
  }

  async ngOnInit() {
    this.elements = await this.ranchService.findAll();
    console.log(this.elements)
  }

  async unregister(ranch: any) {
    await this.ranchService.unregister(ranch.name);
    console.log(this.elements)
    this.elements = this.elements.filter(r => r.name != ranch.name)
    console.log(this.elements)
  }


  // On file Select
  onChange(event) {
    this.file = event.target.files[0];
  }

  // OnClick of button Upload
  onUpload() {
    this.loading = !this.loading;
    console.log(this.file);
    this.ranchService.register(this.file).then(
      (event: any) => {
        this.loading = false; // Flag variable
        this.toastController.create({
          message: "Done!",
          duration: 1500
        }).then(t => t.present());
        this.ranchService.findAll().then(resp => {
          this.elements = resp;
        })
      }
    );
  }
}
