# Slide: Evento multi-regione

`index.html` è una presentazione di 27 slide in italiano sul laboratorio `evento-lab-cluster`:
problema, richiamo di Evento, verdetto di readiness ("celle, non mesh"), topologia del lab, bundle
e contesti, lane globale `ALL`, livelli di complessità L0–L5, flussi di guasto, gap e correzioni,
grafici prima/dopo, limiti residui e istruzioni per eseguire il lab.

È un unico file: CSS, JavaScript, diagrammi e grafici SVG sono tutti inline. Non carica niente
dalla rete, quindi funziona offline e non serve PowerPoint.

## Aprire

Fare doppio clic su `index.html`, oppure trascinarlo in un browser (Edge, Chrome o Firefox).
Da riga di comando:

```bash
start evento-lab-cluster/docs/slides/index.html      # Windows (Git Bash / cmd)
open  evento-lab-cluster/docs/slides/index.html      # macOS
```

| Tasto | Azione |
|---|---|
| `→`, `Spazio`, `PagGiù`, `Invio` | slide successiva |
| `←`, `PagSu`, `Backspace` | slide precedente |
| `Home` / `Fine` | prima / ultima slide |
| `T` | tema chiaro / scuro (la scelta resta memorizzata nel browser) |
| `F` | schermo intero |

Su touch si scorre con uno swipe orizzontale. Il numero della slide finisce nell'URL (`index.html#12`),
quindi si può aprire direttamente una slide. Il tema iniziale segue quello del sistema operativo. Le
slide si adattano alla finestra mantenendo il formato 16:9 (canvas 1280×720). Passando il mouse su barre
e punti dei grafici compare il valore esatto.

## Esportare in PDF

1. Aprire `index.html` in Edge o Chrome.
2. `Ctrl+P` (macOS: `Cmd+P`).
3. Destinazione **Salva come PDF**. Margini **Nessuno**, **Grafica di sfondo** attiva,
   intestazioni e piè di pagina disattivati.
4. Salvare. Ogni slide diventa una pagina 1280×720 (27 pagine). In stampa si usa sempre il tema
   chiaro, anche se a schermo è attivo quello scuro.

In alternativa, senza interfaccia grafica:

```bash
"/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" --headless=new --no-pdf-header-footer \
  --print-to-pdf="$PWD/evento-multiregione.pdf" \
  "file:///$PWD/evento-lab-cluster/docs/slides/index.html"
```

(Con Chrome basta sostituire il percorso dell'eseguibile.)

## Da dove vengono i numeri

Ogni numero viene dai risultati delle run del 29/09/2026:

- `results/<S>/{before,after}/summary.json` (verdetti, `metrics`);
- `results/COMPARISON.md`, `results/BEFORE-SUMMARY.md`, `results/AFTER-SUMMARY.md`;
- `results/_integration/after/checks.md` (id consumer, run di integrazione);
- `FINDINGS.md` e `.claude/CLUSTER-READINESS.md` (gap, stato delle correzioni).

I dati dei grafici stanno nell'oggetto `DATA` all'inizio dello `<script>` di `index.html`, con il
campo del `summary.json` da cui ciascuno è copiato. Dopo una nuova run basta aggiornare quei valori;
le etichette testuali delle slide vanno aggiornate a mano. Ogni slide riporta la fonte nel piè di
pagina.

Due avvertenze sui numeri:

- S08 (ripresa delle scritture 7,6 s contro 15,1 s, N17) e il fan-out di S16 (2,1 s contro 10,2 s,
  N4) vengono da una sola run per immagine.
- I livelli L0–L5 sono livelli di complessità del deployment introdotti per la presentazione. Non
  vanno confusi con gli id L1–L24 dei difetti del laboratorio in `FINDINGS.md`.
