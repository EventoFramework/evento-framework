let glob = require('glob'),
  fs = require('fs');
const jsdom = require("jsdom");


var trads = JSON.parse(fs.readFileSync("src/assets/i18n/en.json", 'utf8').toString());

glob('src/**/*.html', function (err, files) {

  if (err) {
    console.log(err);
  } else {
    files.forEach(function (file) {
      var data = fs.readFileSync(file, 'utf8');
      const dom = new jsdom.JSDOM(data);
      for (const el of dom.window.document.querySelectorAll('[translate]')) {
        var k =el.getAttribute("translate");
        if(! (k in trads)){
          trads[k] = k
        }
      }

    });

    fs.writeFileSync("src/assets/i18n/en.json", JSON.stringify(trads).replaceAll('",','",\n\t')
      .replace('{"','{\n\t"').replace('"}','"\n}'))

  }

});

