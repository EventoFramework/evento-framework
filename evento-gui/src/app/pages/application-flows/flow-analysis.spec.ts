// Run with: node --test src/app/pages/application-flows/flow-analysis.spec.ts (Node 22.18+ strips the types)
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {analyzeFlows, edgeWeight, edgeWeightLabel} from './flow-analysis.ts';
import type {FlowNode} from './flow-analysis.ts';

/**
 * A check-out flow: an invoker sends CheckOut, the aggregate's decision holds on average 3
 * ItemReserved (one per item) and a CouponUsed one time in four; a stock projector consumes
 * ItemReserved, a loyalty projector CouponUsed. Each projector is one server (fcr).
 */
function checkOutFlow(lambda: number, stockServiceTime: number): Record<string, FlowNode> {
  const n: Record<string, FlowNode> = {
    source: {id: 0, type: 'Source', target: {1: 1}, throughtput: lambda},
    invoker: {id: 1, bundle: 'api', component: 'Api', action: 'CheckOutRequest', target: {2: 1}, meanServiceTime: 1},
    request: {id: 2, bundle: 'server', component: 'Gateway', action: 'CheckOut', target: {3: 1}},
    aggregate: {id: 3, bundle: 'shop', component: 'Basket', action: 'CheckOut', target: {4: 1}, meanServiceTime: 2},
    response: {id: 4, bundle: 'server', component: 'Gateway', action: 'ItemReserved', target: {5: 3, 7: 0.25}},
    esItems: {id: 5, bundle: 'event-store', component: 'EventStore', action: 'ItemReserved', target: {6: 1}},
    stock: {id: 6, bundle: 'shop', component: 'Stock', action: 'ItemReserved', target: {},
      meanServiceTime: stockServiceTime, numServers: 1, fcr: true},
    esCoupon: {id: 7, bundle: 'event-store', component: 'EventStore', action: 'CouponUsed', target: {8: 1}},
    loyalty: {id: 8, bundle: 'shop', component: 'Loyalty', action: 'CouponUsed', target: {},
      meanServiceTime: 10, numServers: 1, fcr: true},
  };
  return n;
}

test('a mean count above 1 multiplies the throughput an event branch receives', () => {
  const n = checkOutFlow(0.001, 100);
  analyzeFlows(Object.values(n), [n.source]);

  assert.equal(n.aggregate.throughtput, 0.001);
  assert.ok(Math.abs(n.esItems.throughtput - 0.003) < 1e-12, 'three reservations per check-out');
  assert.ok(Math.abs(n.stock.throughtput - 0.003) < 1e-12);
  assert.equal(n.stock.isBottleneck, false);
});

test('a probability below 1 thins the branch of an event produced only sometimes', () => {
  const n = checkOutFlow(0.001, 100);
  analyzeFlows(Object.values(n), [n.source]);

  assert.ok(Math.abs(n.esCoupon.throughtput - 0.00025) < 1e-12);
  assert.ok(Math.abs(n.loyalty.throughtput - 0.00025) < 1e-12);
});

test('the multiplied load can make a consumer the bottleneck the single-event model hid', () => {
  // 0.005 check-outs/ms: one ItemReserved each would be 0.005/ms, under the stock projector's
  // 0.01/ms (100 ms per event); three each are 0.015/ms, over it.
  const n = checkOutFlow(0.005, 100);
  const result = analyzeFlows(Object.values(n), [n.source]);

  assert.equal(n.stock.isBottleneck, true);
  assert.ok(Math.abs(n.stock.throughtput - 0.01) < 1e-12, 'capped at its capacity');
  assert.equal(result.maxFlowThroughput['0'].id, 6, 'the flow is limited by the stock projector');

  const single = checkOutFlow(0.005, 100);
  single.response.target = {5: 1, 7: 0.25};
  analyzeFlows(Object.values(single), [single.source]);
  assert.equal(single.stock.isBottleneck, false);
});

test('a consumer counts the customers its multiplied arrivals keep busy', () => {
  const n = checkOutFlow(0.001, 100);
  const result = analyzeFlows(Object.values(n), [n.source]);

  // Little's law on the consumer: 0.003 events/ms x 100 ms
  assert.ok(Math.abs(n.stock.customers - 0.3) < 1e-9);
  assert.ok(result.bundles.includes('shop'));
  assert.ok(result.bundleActiveThreads.shop > 0.3);
});

test('an edge without a measurement is visited once per visit to its source', () => {
  assert.equal(edgeWeight(null), 1);
  assert.equal(edgeWeight(undefined), 1);
  assert.equal(edgeWeight(0), 0);
  const n = checkOutFlow(0.001, 100);
  n.esItems.target = {6: null};
  analyzeFlows(Object.values(n), [n.source]);
  assert.ok(Math.abs(n.stock.throughtput - 0.003) < 1e-12);
});

test('edge labels: a probability as a percentage, a mean count as a multiplier', () => {
  assert.equal(edgeWeightLabel(0.25), '25.0 %');
  assert.equal(edgeWeightLabel(1), '100.0 %');
  assert.equal(edgeWeightLabel(3), '× 3.00 per request');
});
