#!/usr/bin/env python3
"""Turns results.jsonl into a Markdown report (Italian): per scenario, one table of throughput,
one of latency and one of refusals, rows = commands in flight, columns = systems.

    python scripts/report.py results/results.jsonl > results/REPORT.md

The last line per (system, scenario, concurrency) wins, so a re-run replaces an old one.
results/ENV.txt (written by scripts/run-all.sh), when present, becomes the header.
"""
import json
import os
import sys
from collections import OrderedDict

SCENARIOS = OrderedDict([
    ("create", "Create: ogni comando crea un nuovo aggregato"),
    ("spread", "Spread: ogni comando aggiunge un item a uno di 1000 aggregati esistenti, a caso"),
    ("hot", "Hot: ogni comando aggiunge un item allo stesso aggregato"),
])

LEGEND = """## Come leggere le tabelle

- **in volo**: comandi inviati e non ancora risposti, sempre esattamente questi (N chiamanti, ognuno
  invia il successivo appena riceve la risposta).
- **throughput**, in **cmd/s** (comandi al secondo): comandi completati con successo, cioè con
  l'evento salvato in modo durevole e la risposta arrivata al chiamante, diviso i 60 s misurati.
  Più alto è meglio.
- **p50 / p99**, in **ms** (millisecondi): latenza vista dal chiamante, dall'invio alla risposta.
  p50 = metà dei comandi è più veloce di così; p99 = 99 comandi su 100 sono più veloci. Più basso è
  meglio. Solo i comandi riusciti.
- **rifiutati**, in **%**: comandi terminati con un errore (conflitto di concorrenza, connessioni
  esaurite, timeout di 30 s) sul totale dei comandi conclusi. Non entrano nel throughput.
- **0 cmd/s** con **100%** rifiutati: il sistema non ha completato nessun comando in quella
  configurazione (collasso).
"""


def load(paths):
    rows = {}
    for path in paths:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    r = json.loads(line)
                    rows[(r["system"], r["scenario"], r["concurrency"])] = r
    return rows


def refused_share(r):
    refused = sum(r["errors"].values())
    total = r["ok"] + refused
    return 100.0 * refused / total if total else 0.0


def table(rows, systems, scenario, levels, cell):
    out = ["| in volo | " + " | ".join(systems) + " |", "|---:|" + "---:|" * len(systems)]
    for c in levels:
        cells = []
        for s in systems:
            r = rows.get((s, scenario, c))
            cells.append("–" if r is None else cell(r))
        out.append(f"| {c} | " + " | ".join(cells) + " |")
    return out


def main(paths):
    rows = load(paths)
    systems = sorted({k[0] for k in rows}, key=lambda s: (not s.startswith("evento"), s))
    out = ["# Risultati B1: throughput di scrittura dei comandi\n"]
    env = os.path.join(os.path.dirname(paths[0]), "ENV.txt")
    if os.path.exists(env):
        out.append("```")
        out.append(open(env, encoding="utf-8").read().strip())
        out.append("```\n")
    out.append(LEGEND)
    for scenario, title in SCENARIOS.items():
        levels = sorted({k[2] for k in rows if k[1] == scenario})
        if not levels:
            continue
        out.append(f"## {title}\n")
        out.append("**Throughput** (cmd/s, più alto è meglio)\n")
        out += table(rows, systems, scenario, levels, lambda r: f"{r['throughput']:.0f} cmd/s")
        out.append("\n**Latenza** (ms, p50 / p99, più basso è meglio)\n")
        out += table(rows, systems, scenario, levels,
                     lambda r: f"{r['p50']:.1f} / {r['p99']:.1f} ms" if r["ok"] else "–")
        out.append("\n**Rifiutati** (% dei comandi conclusi, più basso è meglio)\n")
        out += table(rows, systems, scenario, levels, lambda r: f"{refused_share(r):.0f}%")
        errors = sorted({e for k, r in rows.items() if k[1] == scenario for e in r["errors"]})
        if errors:
            out.append(f"\nErrori visti: {', '.join(errors)}.")
        out.append("")
    print("\n".join(out))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv[1:] or ["results/results.jsonl"])
