/**
 * Throughput analysis of a flow model (the server's PerformanceModel): an open network of
 * stations whose edges carry the mean number of visits to the target per visit to the source.
 *
 * An edge weight is a probability (a command handler that produces an event only sometimes, a
 * branch taken with some frequency) or, above 1, a mean count (a command whose decision holds
 * several events of one type, 3.0): the throughput a target receives is the source's throughput
 * times the weight, whichever it is. A station with a finite number of servers (`fcr`) serves at
 * most numServers / meanServiceTime requests per ms; past that it is a bottleneck and passes on
 * only what it serves.
 *
 * Kept free of Angular so the arithmetic can be tested on its own (flow-analysis.spec.ts).
 */

export interface FlowNode {
  id: number | string;
  type?: string;
  bundle?: string;
  component?: string;
  action?: string;
  target: Record<string, number>;
  meanServiceTime?: number;
  numServers?: number;
  fcr?: boolean;
  // computed
  throughtput?: number;
  workload?: number;
  flow?: number | string;
  mf?: number;
  isBottleneck?: boolean;
  mainBottleneck?: number | string | null;
  customers?: number;
}

export interface FlowAnalysis {
  bundleActiveThreads: Record<string, number>;
  maxFlowThroughput: Record<string, FlowNode>;
  bundles: string[];
}

/**
 * Propagates each source's throughput through the network, marks bottlenecks, and spreads each
 * component's servers over its stations in proportion to their load (a fixed point, at most 10
 * rounds). Mutates the nodes' computed fields, as the flows page reads them.
 */
export function analyzeFlows(nodes: FlowNode[], sources: FlowNode[]): FlowAnalysis {
  const nodesRef: Record<string, FlowNode> = {};
  for (const node of nodes) {
    nodesRef[String(node.id)] = node;
    if (node.type !== 'Source') {
      node.throughtput = 0;
    }
    node.workload = node.throughtput;
    node.mf = 1;
    node.isBottleneck = false;
    node.mainBottleneck = null;
  }

  let i = 0;
  const bundles: string[] = [];
  while (true) {
    const bundleActiveThreads: Record<string, number> = {};
    const maxFlowThroughput: Record<string, FlowNode> = {};

    const q: FlowNode[] = [];
    for (const s of sources) {
      s.meanServiceTime = 1 / s.throughtput;
      maxFlowThroughput[String(s.id)] = s;
      s.flow = s.id;
      q.push(s);
    }
    while (q.length > 0) {
      const n = q.shift();
      for (const t of Object.keys(n.target)) {
        const target = nodesRef[t];
        if (!target) {
          continue;
        }
        const weight = edgeWeight(n.target[t]);
        target.workload = n.throughtput * weight;
        target.throughtput = target.workload;
        if (target.fcr) {
          const tt = target.numServers / target.meanServiceTime;
          if (tt < target.workload) {
            target.throughtput = tt;
            target.isBottleneck = true;
          } else {
            target.isBottleneck = false;
          }
        }
        target.flow = n.flow;
        target.mf = n.mf * weight;
        target.mainBottleneck = n.mainBottleneck;
        if (n.isBottleneck && !target.mainBottleneck) {
          target.mainBottleneck = n.id;
        }
        q.push(target);
      }
    }

    i++;
    const nsSum: Record<string, number> = {};
    for (const node of nodes) {
      if (node.fcr) {
        if (!nsSum[node.component]) {
          nsSum[node.component] = 0;
        }
        nsSum[node.component] += node.workload * node.meanServiceTime;
      }
    }
    let diff = 0;
    for (const node of nodes) {
      if (node.fcr) {
        const o = node.numServers;
        node.numServers = (node.workload * node.meanServiceTime) / Math.max(1, nsSum[node.component]);
        diff += Math.abs(o - node.numServers);
      }
    }
    if (i > 10 || diff < 0.0001) {
      for (const node of nodes) {
        const nc = node.workload * node.meanServiceTime;
        node.customers = (node.fcr ? Math.min(node.numServers, nc) : nc);
        if (node.bundle) {
          if (!bundleActiveThreads[node.bundle]) {
            bundleActiveThreads[node.bundle] = 0;
          }
          bundleActiveThreads[node.bundle] += node.customers;
          if (!bundles.includes(node.bundle)) {
            bundles.push(node.bundle);
          }
        }
        if (node.isBottleneck && !node.mainBottleneck) {
          const source = nodesRef[String(node.flow)];
          if (maxFlowThroughput[String(node.flow)].id === source.id ||
            maxFlowThroughput[String(node.flow)].numServers < node.numServers
          ) {
            maxFlowThroughput[String(node.flow)] = node;
          }
        }
      }
      return {bundleActiveThreads, maxFlowThroughput, bundles};
    }
  }
}

/** An edge with no measurement (null) is visited once per visit to its source. */
export function edgeWeight(w: number | null | undefined): number {
  return w === null || w === undefined || Number.isNaN(w) ? 1 : w;
}

/**
 * How the flows page labels an edge's weight: a probability as a percentage, a mean count
 * above 1 (several events of one type per command) as a multiplier.
 */
export function edgeWeightLabel(w: number | null | undefined): string {
  const weight = edgeWeight(w);
  return weight > 1 ? '× ' + weight.toFixed(2) + ' per request' : (weight * 100).toFixed(1) + ' %';
}
