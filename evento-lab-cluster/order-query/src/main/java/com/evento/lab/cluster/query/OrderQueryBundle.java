package com.evento.lab.cluster.query;

import com.evento.application.EventoBundle;
import com.evento.lab.cluster.query.bundle.CatalogProjector;
import com.evento.lab.cluster.query.bundle.OrderProjector;
import com.evento.lab.cluster.support.BundleDefinition;
import com.evento.lab.cluster.support.BundleDeployment;
import com.evento.lab.cluster.support.ContextSelection;
import com.evento.lab.cluster.support.LabDatabases;
import com.evento.lab.cluster.support.store.CatalogFanOut;
import com.evento.lab.cluster.support.store.CatalogStore;
import com.evento.lab.cluster.support.store.OrderViewStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bundle {@code order-query}: one {@link OrderProjector}, one
 * {@link CatalogProjector} and the query handlers, deployed as many times as
 * the topology needs, each deployment choosing its contexts:
 *
 * <table>
 *   <caption>EVENTO_CONTEXTS to consumer engines</caption>
 *   <tr><th>EVENTO_CONTEXTS</th><th>OrderProjector engines</th><th>CatalogProjector engines</th><th>writes</th></tr>
 *   <tr><td>{@code EUROPA,AFRICA}</td><td>EUROPA, AFRICA</td><td>none</td><td>the cell's {@code order_view}</td></tr>
 *   <tr><td>{@code ALL}</td><td>none</td><td>ALL (the global lane)</td><td>{@code catalog_price} in every {@code DATA_TARGETS} database</td></tr>
 *   <tr><td>{@code *}</td><td>* (every context)</td><td>none</td><td>{@code order_view_global}</td></tr>
 * </table>
 *
 * <p>A component given no context runs no engine: the builder starts one
 * engine per entry of the component's context set, and an explicitly empty set
 * has none (only a component with no entry at all defaults to {@code *}). That
 * is how the wildcard deployment keeps its hands off the global lane even
 * though {@code *} matches {@code ALL} events: {@link ContextSelection#global()}
 * is empty for it.
 *
 * <p>Every deployment keeps the same bundle id ({@code order-query}): consumer
 * ids differ by context ({@code order-query_OrderProjector_1_CINA}), so regional
 * deployments never compete, while the deployments that list {@code ALL} and
 * share the consumer-state database do compete, for the single lane
 * {@code order-query_CatalogProjector_1_ALL}. A different bundle id would make
 * a second, independent global lane (G24) and fan every price out twice.
 */
public class OrderQueryBundle implements BundleDefinition {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryBundle.class);

    /** Component name -> contexts, for a selection. Pure, unit tested. */
    public static Map<String, List<String>> plan(ContextSelection contexts) {
        var out = new LinkedHashMap<String, List<String>>();
        out.put(OrderProjector.class.getSimpleName(), contexts.regional());
        out.put(CatalogProjector.class.getSimpleName(), contexts.global());
        return out;
    }

    /** The table {@link OrderProjector} writes: the global view for the wildcard deployment. */
    public static String writerTable(ContextSelection contexts) {
        return contexts.isWildcard() ? OrderViewStore.GLOBAL_TABLE : OrderViewStore.REGIONAL_TABLE;
    }

    private final BundleDeployment deployment;
    private final LabDatabases databases;

    public OrderQueryBundle(BundleDeployment deployment, LabDatabases databases) {
        this.deployment = deployment;
        this.databases = databases;
    }

    @Override
    public Class<?> anchor() {
        return OrderProjector.class;
    }

    @Override
    public boolean usesConsumers() {
        return true;
    }

    @Override
    public Map<String, List<String>> consumerPlan(BundleDeployment d) {
        return plan(d.requireContexts());
    }

    @Override
    public void configure(EventoBundle.Builder builder, BundleDeployment d) {
        var plan = plan(d.requireContexts());
        builder.setComponentContexts(OrderProjector.class,
                plan.get(OrderProjector.class.getSimpleName()).toArray(String[]::new));
        builder.setComponentContexts(CatalogProjector.class,
                plan.get(CatalogProjector.class.getSimpleName()).toArray(String[]::new));
    }

    @Override
    public Map<Class<?>, Object> injectables() {
        var contexts = deployment.requireContexts();
        var writer = deployment.instanceId();
        var app = databases.app();
        var views = new OrderViews(
                new OrderViewStore(app, writerTable(contexts), writer),
                new OrderViewStore(app, OrderViewStore.REGIONAL_TABLE, writer),
                new OrderViewStore(databases.globalView(), OrderViewStore.GLOBAL_TABLE, writer));
        var localName = deployment.cell() == null ? "local" : deployment.cell().toLowerCase(Locale.ROOT);
        var targets = new ArrayList<CatalogStore>();
        databases.targets().forEach((name, ds) -> targets.add(new CatalogStore(name, ds, writer)));
        if (targets.isEmpty()) {
            if (contexts.includesGlobal()) {
                log.warn("event=lab_fanout_no_targets bundle={} instance={}: DATA_TARGETS is empty, the global "
                        + "lane writes only this cell's database", deployment.bundleId(), deployment.instanceId());
            }
            targets.add(new CatalogStore(localName, app, writer));
        }
        return Map.of(
                OrderViews.class, views,
                LocalCatalog.class, new LocalCatalog(new CatalogStore(localName, app, writer)),
                CatalogFanOut.class, new CatalogFanOut(targets));
    }
}
