import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class BundleColorService {

  private colors = [
    "#4c8dff","#50c8ff","#6370ff","#42d77d",
    "#ffca22","#ed576b",
    "#9BF6FF", "#A0C4FF", "#BDB2FF", "#FFC6FF",
    "#FFADAD", "#FFD6A5", "#FDFFB6", "#CAFFBF",
    "#18ffb1", "#ffd493"]
  private usedColors = 0;

  private bundleColor = {};

  constructor() { }

  public getColorForBundle(bundle: string) {
    if(bundle === 'server'){
      return 'transparent';
    }
    if(bundle === 'event-store'){
      return 'transparent';
    }
    let c = this.bundleColor[bundle];
    if(c){
      return c;
    }else{
      c = this.colors[this.usedColors++];
      this.bundleColor[bundle] = c;
      return c;
    }
  }
}
