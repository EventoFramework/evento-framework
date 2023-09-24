import {enableProdMode} from '@angular/core';
import {platformBrowserDynamic} from '@angular/platform-browser-dynamic';

import {AppModule} from './app/app.module';
import {environment} from './environments/environment';

if (environment.production) {
  enableProdMode();
}

var token = localStorage.getItem('evento_server_web_token');

window.fetch = new Proxy(window.fetch, {
  apply: function (target, that, args) {
    const params = {...args[1]}
    if (!token) {
      token = prompt('Access token:')
      localStorage.setItem('evento_server_web_token', token);
    }
    params.headers = {...params.headers}
    params.headers['Authorization'] = 'Bearer ' + token
    args[1] = params;
    let temp = target.apply(that, args);
    temp.then((res) => {
      // After completion of request
      if (res.status === 401 || res.status === 403) {
        localStorage.removeItem('evento_server_web_token');
        window.location.reload();
      }
    });
    return temp;
  },
});
platformBrowserDynamic().bootstrapModule(AppModule)
  .catch(err => console.log(err));
