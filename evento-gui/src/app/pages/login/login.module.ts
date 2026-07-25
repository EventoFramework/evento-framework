import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

import {IonicModule} from '@ionic/angular';

import {LoginPageRoutingModule} from './login-routing.module';

import {LoginPage} from './login.page';
import {TranslatePipe, TranslateDirective} from "@ngx-translate/core";

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    LoginPageRoutingModule,
    TranslatePipe, TranslateDirective
  ],
  declarations: [LoginPage]
})
export class LoginPageModule {}
