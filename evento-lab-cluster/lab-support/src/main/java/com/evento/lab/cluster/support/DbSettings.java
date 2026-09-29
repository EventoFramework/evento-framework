package com.evento.lab.cluster.support;

import java.util.function.Function;

/**
 * One JDBC database a deployment talks to.
 *
 * @param url      JDBC URL; a multi-host URL with {@code targetServerType=primary}
 *                 follows a promoted replica
 * @param user     user
 * @param password password
 * @param poolSize maximum Hikari pool size
 */
public record DbSettings(String url, String user, String password, int poolSize) {

    /** The same database: same URL and user (then one pool serves both roles). */
    public boolean sameAs(DbSettings other) {
        return other != null && url.equals(other.url()) && user.equals(other.user());
    }

    /**
     * {@code <PREFIX>_URL / _USER / _PASSWORD / _POOL_SIZE}. Missing URL means
     * {@code fallback}; user and password default to the fallback's (or
     * {@code postgres} / {@code secret}).
     */
    static DbSettings from(Function<String, String> env, String prefix, DbSettings fallback, int defaultPool) {
        var url = BundleDeployment.blankToNull(env.apply(prefix + "_URL"));
        if (url == null) return fallback;
        var user = BundleDeployment.orDefault(env.apply(prefix + "_USER"), fallback == null ? "postgres" : fallback.user());
        var pass = BundleDeployment.orDefault(env.apply(prefix + "_PASSWORD"),
                fallback == null ? "secret" : fallback.password());
        var pool = Integer.parseInt(BundleDeployment.orDefault(env.apply(prefix + "_POOL_SIZE"), String.valueOf(defaultPool)));
        return new DbSettings(url.trim(), user, pass, pool);
    }

    @Override
    public String toString() {
        // never print the password (the whoami endpoint shows this)
        return url + " (user " + user + ", pool " + poolSize + ")";
    }
}
