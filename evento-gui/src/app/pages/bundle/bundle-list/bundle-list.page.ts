import {Component, OnInit} from '@angular/core';
import {BundleService} from "../../../services/bundle.service";
import {ToastController} from "@ionic/angular";

@Component({
  selector: 'app-bundle-list',
  templateUrl: './bundle-list.page.html',
  styleUrls: ['./bundle-list.page.scss'],
})
export class BundleListPage implements OnInit {
  elements: any[] = [];
  loading: boolean = false; // Flag variable
  file: File = null; // Variable to store file

  constructor(private bundleService: BundleService,
              private toastController: ToastController) {
  }

  ngOnInit() {
  }

  async ionViewWillEnter(){
    this.elements = await this.bundleService.findAll();
  }

  async unregister(bundle: any) {
    await this.bundleService.unregister(bundle.name);
    this.elements = this.elements.filter(r => r.name != bundle.name)
  }


  // On file Select
  onChange(event) {
    this.file = event.target.files[0];
  }

  // OnClick of button Upload
  onUpload() {
    this.loading = !this.loading;
    this.bundleService.register(this.file).then(
      (event: any) => {
        this.loading = false; // Flag variable
        this.toastController.create({
          message: "Done!",
          duration: 1500
        }).then(t => t.present());
        this.bundleService.findAll().then(resp => {
          this.elements = resp;
        })
      }
    );
  }
}