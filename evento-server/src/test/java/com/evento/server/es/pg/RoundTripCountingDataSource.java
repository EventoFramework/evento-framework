package com.evento.server.es.pg;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts the statements executed through a {@link DataSource}: with autocommit, each execution
 * is one request/response exchange with the database, i.e. one round trip. Commits and
 * rollbacks are counted too (an explicit transaction pays one more). Pool housekeeping
 * (validation, reset) happens below the proxy and is not counted; it is the same for every
 * command anyway.
 */
final class RoundTripCountingDataSource {

    private static final Set<String> EXECUTIONS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeBatch", "executeLargeUpdate", "executeLargeBatch");

    private final AtomicInteger roundTrips = new AtomicInteger();
    private final List<String> statements = new CopyOnWriteArrayList<>();
    private final DataSource proxy;

    RoundTripCountingDataSource(DataSource target) {
        this.proxy = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DataSource.class},
                (p, m, args) -> {
                    Object r = invoke(target, m, args);
                    return r instanceof Connection c ? connection(c) : r;
                });
    }

    DataSource dataSource() {
        return proxy;
    }

    int count() {
        return roundTrips.get();
    }

    /** The SQL of every counted execution, in order (for diagnostics). */
    List<String> statements() {
        return List.copyOf(statements);
    }

    void reset() {
        roundTrips.set(0);
        statements.clear();
    }

    private Connection connection(Connection target) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, args) -> {
                    if (m.getName().equals("commit") || m.getName().equals("rollback")) {
                        roundTrips.incrementAndGet();
                        statements.add(m.getName());
                    }
                    Object r = invoke(target, m, args);
                    if (r instanceof Statement s) {
                        String sql = args != null && args.length > 0 && args[0] instanceof String q ? q : null;
                        return statement(s, sql);
                    }
                    return r;
                });
    }

    private Statement statement(Statement target, String preparedSql) {
        var interfaces = target instanceof java.sql.CallableStatement ? new Class<?>[]{java.sql.CallableStatement.class}
                : target instanceof java.sql.PreparedStatement ? new Class<?>[]{java.sql.PreparedStatement.class}
                : new Class<?>[]{Statement.class};
        InvocationHandler handler = (p, m, args) -> {
            if (EXECUTIONS.contains(m.getName())) {
                roundTrips.incrementAndGet();
                statements.add(args != null && args.length > 0 && args[0] instanceof String q ? q : preparedSql);
            }
            return invoke(target, m, args);
        };
        return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), interfaces, handler);
    }

    private static Object invoke(Object target, java.lang.reflect.Method m, Object[] args) throws Throwable {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
