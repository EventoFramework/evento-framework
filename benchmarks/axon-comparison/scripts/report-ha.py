#!/usr/bin/env python3
"""Turns results-ha.jsonl (B2, scripts/run-ha.sh) into a Markdown report (Italian).

    python scripts/report-ha.py results/results-ha.jsonl > results/REPORT-HA.md

Each system runs two loads, one per AZ (label "<system>@rtt<ms>#<load>"): their throughputs add
up, latency is the worse of the two, refusals add up. The last line per (label, scenario,
concurrency) wins.
"""
import json
import os
import re
import sys
from collections import OrderedDict, defaultdict

SCENARIOS = OrderedDict([
    ("create", "Create: ogni comando crea un nuovo aggregato"),
    ("spread", "Spread: ogni comando aggiunge un item a uno di 1000 aggregati condivisi dai due server"),
    ("hot", "Hot: ogni comando aggiunge un item allo stesso aggregato, attraverso entrambi i server"),
])

LEGEND = """## Come leggere le tabelle

- **Topologia**: due AZ. AZ a: PostgreSQL primario, un server (o un'istanza Axon), il suo bundle e il
  suo generatore di carico. AZ b: lo standby **sincrono** (ogni commit aspetta che sia anche lì), un
  secondo server con bundle e carico, che raggiunge il database attraverso l'AZ. **RTT** = round trip
  aggiunto tra le AZ (replica e connessioni al database dall'AZ b).
- **in volo**: comandi inviati e non ancora risposti, in totale (metà per AZ).
- **throughput**, in **cmd/s**: comandi completati con successo al secondo, somma delle due AZ, e tra
  parentesi la parte di ciascuna (AZ a / AZ b): un sistema che serve bene solo l'AZ del primario lo
  mostra lì. Più alto è meglio.
- **p50 / p99**, in **ms**: latenza vista dal chiamante; la peggiore delle due AZ. Più basso è meglio.
  Un'AZ che non ha completato nessun comando nella finestra è segnata **ferma**.
- **rifiutati**, in **%**: comandi terminati con un errore (conflitto, connessioni esaurite, timeout di
  30 s) sul totale. Non entrano nel throughput.
- Tutto su una macchina (stesse CPU di B1): misura il costo della replica sincrona e della latenza tra
  AZ per ciascun design, non il guadagno di più hardware.
"""

LABEL = re.compile(r"^(?P<system>.+)@rtt(?P<rtt>\d+)#(?P<load>\d+)$")


def load(path):
    rows = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.strip():
                r = json.loads(line)
                rows[(r["system"], r["scenario"], r["concurrency"])] = r
    merged = defaultdict(list)
    for (label, scenario, concurrency), r in rows.items():
        m = LABEL.match(label)
        if m:
            merged[(m["system"], int(m["rtt"]), scenario, concurrency * 2)].append(r)
    return merged


def by_az(rs):
    return sorted(rs, key=lambda r: LABEL.match(r["system"])["load"])


def cell_throughput(rs):
    total = f"{sum(r['throughput'] for r in rs):.0f} cmd/s"
    if len(rs) != 2:
        return total + " (1 AZ)"
    return total + " (" + " / ".join(f"{r['throughput']:.0f}" for r in by_az(rs)) + ")"


def cell_latency(rs):
    ok = [r for r in rs if r["ok"]]
    if not ok:
        return "ferma"
    cell = f"{max(r['p50'] for r in ok):.1f} / {max(r['p99'] for r in ok):.1f} ms"
    return cell + (" (un'AZ ferma)" if len(ok) < len(rs) else "")


def cell_refused(rs):
    refused = sum(sum(r["errors"].values()) for r in rs)
    total = refused + sum(r["ok"] for r in rs)
    return f"{100.0 * refused / total:.0f}%" if total else "0%"


def table(merged, systems, rtt, scenario, levels, cell):
    out = ["| in volo | " + " | ".join(systems) + " |", "|---:|" + "---:|" * len(systems)]
    for c in levels:
        cells = []
        for s in systems:
            rs = merged.get((s, rtt, scenario, c))
            cells.append("–" if not rs else cell(rs))
        out.append(f"| {c} | " + " | ".join(cells) + " |")
    return out


def main(path):
    merged = load(path)
    systems = sorted({k[0] for k in merged}, key=lambda s: (not s.startswith("evento"), s))
    rtts = sorted({k[1] for k in merged})
    out = ["# Risultati B2: alta affidabilità su due AZ\n"]
    env = os.path.join(os.path.dirname(path), "ENV-HA.txt")
    if os.path.exists(env):
        out += ["```", open(env, encoding="utf-8").read().strip(), "```\n"]
    out.append(LEGEND)
    for rtt in rtts:
        out.append(f"## RTT tra le AZ: {rtt} ms\n")
        for scenario, title in SCENARIOS.items():
            levels = sorted({k[3] for k in merged if k[1] == rtt and k[2] == scenario})
            if not levels:
                continue
            out.append(f"### {title}\n")
            out.append("**Throughput** (cmd/s, più alto è meglio)\n")
            out += table(merged, systems, rtt, scenario, levels, cell_throughput)
            out.append("\n**Latenza** (ms, p50 / p99, più basso è meglio)\n")
            out += table(merged, systems, rtt, scenario, levels, cell_latency)
            out.append("\n**Rifiutati** (% dei comandi conclusi, più basso è meglio)\n")
            out += table(merged, systems, rtt, scenario, levels, cell_refused)
            errors = sorted({e for k, rs in merged.items() if k[1] == rtt and k[2] == scenario
                             for r in rs for e in r["errors"]})
            if errors:
                out.append(f"\nErrori visti: {', '.join(errors)}.")
            out.append("")
    print("\n".join(out))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv[1] if len(sys.argv) > 1 else "results/results-ha.jsonl")
