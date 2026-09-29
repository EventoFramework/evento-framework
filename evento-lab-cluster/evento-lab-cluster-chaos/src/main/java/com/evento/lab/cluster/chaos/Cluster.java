package com.evento.lab.cluster.chaos;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Controls the compose stack through the docker CLI. Compose sets
 * {@code container_name} equal to the service name for every lab service, so
 * container operations address services by name directly.
 *
 * <p>Every mutating call is appended to an action log the scenario report
 * includes, so a result always says exactly what was done to the stack.
 */
public class Cluster {

    public record Exec(int exit, String out, String err) {
        public boolean ok() {
            return exit == 0;
        }

        public String combined() {
            return out + (err.isBlank() ? "" : "\n" + err);
        }
    }

    /** What a network disconnect removed, enough to put it back exactly. */
    public record Detachment(String container, String network, List<String> aliases, String ipv4) {
    }

    private final LabConfig config;
    private final List<String> actions = new ArrayList<>();

    public Cluster(LabConfig config) {
        this.config = config;
    }

    public List<String> actions() {
        synchronized (actions) {
            return List.copyOf(actions);
        }
    }

    private void action(String a) {
        synchronized (actions) {
            actions.add(Instant.now() + " " + a);
        }
        System.out.println("[cluster] " + a);
    }

    // ---- process plumbing ------------------------------------------------------------------

    public Exec run(Duration timeout, List<String> cmd) {
        try {
            var pb = new ProcessBuilder(cmd);
            pb.environment().putIfAbsent("MSYS_NO_PATHCONV", "1");
            var p = pb.start();
            var out = readAsync(p.getInputStream());
            var err = readAsync(p.getErrorStream());
            if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return new Exec(-1, out.getNow(""), "timeout after " + timeout + ": " + String.join(" ", cmd));
            }
            return new Exec(p.exitValue(), out.get(10, TimeUnit.SECONDS), err.get(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            return new Exec(-2, "", e.toString());
        }
    }

    private static CompletableFuture<String> readAsync(InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try (in) {
                var bos = new ByteArrayOutputStream();
                in.transferTo(bos);
                return bos.toString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        }, r -> Thread.ofVirtual().start(r));
    }

    public Exec docker(Duration timeout, String... args) {
        var cmd = new ArrayList<String>();
        cmd.add("docker");
        cmd.addAll(List.of(args));
        return run(timeout, cmd);
    }

    public Exec docker(String... args) {
        return docker(Duration.ofSeconds(120), args);
    }

    public Exec compose(Duration timeout, List<Path> extraFiles, List<String> profiles, String... args) {
        var cmd = new ArrayList<String>(List.of("docker", "compose", "-f", config.composeFile().toString()));
        for (var f : extraFiles) {
            cmd.add("-f");
            cmd.add(f.toString());
        }
        for (var p : profiles) {
            cmd.add("--profile");
            cmd.add(p);
        }
        cmd.addAll(List.of(args));
        return run(timeout, cmd);
    }

    public Exec compose(String... args) {
        return compose(Duration.ofSeconds(600), List.of(), List.of(), args);
    }

    private Exec must(Exec e, String what) {
        if (!e.ok()) {
            throw new HarnessException(what + " failed (exit " + e.exit() + "): " + e.combined().trim());
        }
        return e;
    }

    // ---- container lifecycle ----------------------------------------------------------------

    public void kill(String service) {
        action("docker kill " + service);
        must(docker("kill", service), "docker kill " + service);
    }

    public void stop(String service) {
        action("docker stop " + service);
        must(docker(Duration.ofSeconds(90), "stop", service), "docker stop " + service);
    }

    public void start(String service) {
        action("docker start " + service);
        must(docker(Duration.ofSeconds(90), "start", service), "docker start " + service);
    }

    public void restart(String service) {
        action("docker restart " + service);
        must(docker(Duration.ofSeconds(120), "restart", service), "docker restart " + service);
    }

    public void pause(String service) {
        action("docker pause " + service);
        must(docker("pause", service), "docker pause " + service);
    }

    public void unpause(String service) {
        action("docker unpause " + service);
        must(docker("unpause", service), "docker unpause " + service);
    }

    /** Start a container if it exists and is not running; unpause a paused one. Never throws. */
    public void ensureRunning(String service) {
        var st = state(service);
        if (st.equals("paused")) {
            action("docker unpause " + service + " (restore)");
            docker("unpause", service);
        } else if (st.equals("exited") || st.equals("created") || st.equals("dead")) {
            action("docker start " + service + " (restore)");
            docker(Duration.ofSeconds(90), "start", service);
        } else if (st.equals("missing")) {
            action("docker compose up -d --no-deps " + service + " (restore)");
            compose("up", "-d", "--no-deps", service);
        }
    }

    /** {@code running}, {@code exited}, {@code paused}, ... or {@code missing}. */
    public String state(String service) {
        var e = docker(Duration.ofSeconds(20), "inspect", "-f", "{{.State.Status}}", service);
        return e.ok() ? e.out().trim() : "missing";
    }

    /** {@code healthy}, {@code unhealthy}, {@code starting}, {@code none} (no healthcheck) or {@code missing}. */
    public String health(String service) {
        var e = docker(Duration.ofSeconds(20), "inspect", "-f",
                "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}", service);
        return e.ok() ? e.out().trim() : "missing";
    }

    public Instant startedAt(String service) {
        var e = docker(Duration.ofSeconds(20), "inspect", "-f", "{{.State.StartedAt}}", service);
        try {
            return e.ok() ? Instant.parse(e.out().trim()) : Instant.EPOCH;
        } catch (Exception ex) {
            return Instant.EPOCH;
        }
    }

    /**
     * Ready = compose healthcheck healthy, or, for app JVMs without a
     * healthcheck, the {@code event=lab_ready} log line since the last start.
     */
    public boolean isReady(String service) {
        if (!state(service).equals("running")) return false;
        var h = health(service);
        if (h.equals("healthy")) return true;
        if (!h.equals("none")) return false;
        if (service.startsWith("evento-") || service.startsWith("es-pg") || service.startsWith("app-pg")
                || service.equals("lb") || service.startsWith("toxiproxy")) {
            return true;
        }
        return logsSince(service, startedAt(service)).contains("event=lab_ready");
    }

    public boolean waitReady(String service, Duration timeout) {
        return waitUntil(timeout, Duration.ofSeconds(2), () -> isReady(service));
    }

    public Map<String, Boolean> waitAllReady(List<String> services, Duration timeout) {
        var deadline = Instant.now().plus(timeout);
        var out = new LinkedHashMap<String, Boolean>();
        for (var s : services) {
            var left = Duration.between(Instant.now(), deadline);
            out.put(s, waitReady(s, left.isNegative() ? Duration.ofSeconds(1) : left));
        }
        return out;
    }

    public static boolean waitUntil(Duration timeout, Duration poll, BooleanSupplier condition) {
        var deadline = Instant.now().plus(timeout);
        while (true) {
            try {
                if (condition.getAsBoolean()) return true;
            } catch (RuntimeException ignored) {
                // treat as not yet
            }
            if (Instant.now().isAfter(deadline)) return false;
            sleep(poll);
        }
    }

    public static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HarnessException("interrupted", e);
        }
    }

    // ---- logs ---------------------------------------------------------------------------------

    public String logsSince(String service, Instant since) {
        var e = docker(Duration.ofSeconds(60), "logs", "--since",
                DateTimeFormatter.ISO_INSTANT.format(since), service);
        return e.out() + e.err();
    }

    /** Occurrences of each marker in the service's logs since {@code since}. */
    public Map<String, Integer> countMarkers(String service, Instant since, List<String> markers) {
        return countMarkers(logsSince(service, since), markers);
    }

    public static Map<String, Integer> countMarkers(String logs, List<String> markers) {
        var out = new LinkedHashMap<String, Integer>();
        for (var m : markers) {
            int count = 0;
            int idx = 0;
            while ((idx = logs.indexOf(m, idx)) >= 0) {
                count++;
                idx += m.length();
            }
            out.put(m, count);
        }
        return out;
    }

    /** First lines containing {@code marker}, for the report. */
    public static List<String> grep(String logs, String marker, int max) {
        var out = new ArrayList<String>();
        for (var line : logs.split("\\R")) {
            if (line.contains(marker)) {
                out.add(line.length() > 400 ? line.substring(0, 400) + "..." : line);
                if (out.size() >= max) break;
            }
        }
        return out;
    }

    // ---- networks -----------------------------------------------------------------------------

    public List<String> networksOf(String container) {
        var e = docker(Duration.ofSeconds(20), "inspect", "-f", "{{json .NetworkSettings.Networks}}", container);
        if (!e.ok()) return List.of();
        var node = Json.read(e.out());
        var out = new ArrayList<String>();
        node.fieldNames().forEachRemaining(out::add);
        return out;
    }

    public Detachment disconnect(String network, String container) {
        var e = must(docker(Duration.ofSeconds(20), "inspect", "-f", "{{json .NetworkSettings.Networks}}", container),
                "inspect " + container);
        JsonNode n = Json.read(e.out()).path(network);
        if (n.isMissingNode()) {
            throw new HarnessException(container + " is not attached to " + network);
        }
        var aliases = new ArrayList<String>();
        n.path("Aliases").forEach(a -> {
            var s = a.asText();
            // Docker adds the container id / name itself; only keep declared aliases.
            if (!s.equals(container) && !s.matches("[0-9a-f]{12}")) aliases.add(s);
        });
        var ip = n.path("IPAMConfig").path("IPv4Address").asText("");
        action("docker network disconnect " + network + " " + container + " (aliases=" + aliases
                + (ip.isBlank() ? "" : " ip=" + ip) + ")");
        must(docker("network", "disconnect", network, container), "network disconnect");
        return new Detachment(container, network, aliases, ip.isBlank() ? null : ip);
    }

    public void reconnect(Detachment d) {
        var args = new ArrayList<String>(List.of("network", "connect"));
        for (var a : d.aliases()) {
            args.add("--alias");
            args.add(a);
        }
        if (d.ipv4() != null) {
            args.add("--ip");
            args.add(d.ipv4());
        }
        args.add(d.network());
        args.add(d.container());
        action("docker " + String.join(" ", args));
        var e = docker(args.toArray(String[]::new));
        if (!e.ok() && !e.combined().contains("already exists")) {
            throw new HarnessException("network connect failed: " + e.combined());
        }
    }

    // ---- exec ---------------------------------------------------------------------------------

    public Exec psql(String pgService, String db, String sql) {
        return docker(Duration.ofSeconds(60), "exec", pgService, "psql", "-U", "postgres", "-d", db,
                "-v", "ON_ERROR_STOP=1", "-tAc", sql);
    }

    public Exec psqlMust(String pgService, String db, String sql) {
        action("psql " + pgService + "/" + db + ": " + sql);
        return must(psql(pgService, db, sql), "psql on " + pgService);
    }

    /** {@code cpu%|mem usage} as reported by {@code docker stats}. */
    public String stats(String service) {
        var e = docker(Duration.ofSeconds(30), "stats", "--no-stream", "--format", "{{.CPUPerc}}|{{.MemUsage}}", service);
        return e.ok() ? e.out().trim() : "n/a";
    }

    // ---- compose-level --------------------------------------------------------------------------

    /**
     * Recreate one service with extra environment variables, through a
     * temporary compose override file (the base compose file is never edited).
     */
    public void recreateWithEnv(String service, Map<String, String> env) {
        try {
            var sb = new StringBuilder("services:\n  ").append(service).append(":\n    environment:\n");
            for (var e : env.entrySet()) {
                sb.append("      ").append(e.getKey()).append(": \"")
                        .append(e.getValue().replace("\"", "\\\"")).append("\"\n");
            }
            var tmp = Files.createTempFile("evento-lab-override-", ".yaml");
            Files.writeString(tmp, sb.toString());
            action("compose up -d --no-deps --force-recreate " + service + " with env " + env);
            must(compose(Duration.ofSeconds(300), List.of(tmp), List.of(),
                    "up", "-d", "--no-deps", "--force-recreate", service), "recreate " + service);
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            throw new HarnessException("override file", e);
        }
    }

    /** Put a service back to exactly what compose.yaml says. */
    public void recreateDefault(String service) {
        action("compose up -d --no-deps --force-recreate " + service + " (restore default)");
        must(compose(Duration.ofSeconds(300), List.of(), List.of(),
                "up", "-d", "--no-deps", "--force-recreate", service), "recreate " + service);
    }

    public void upProfileService(String profile, String service) {
        action("compose --profile " + profile + " up -d --no-deps " + service);
        must(compose(Duration.ofSeconds(300), List.of(), List.of(profile), "up", "-d", "--no-deps", service),
                "up " + service);
    }

    public void removeService(String profile, String service) {
        action("compose rm -sf " + service);
        compose(Duration.ofSeconds(120), List.of(), profile == null ? List.of() : List.of(profile),
                "rm", "-s", "-f", service);
    }

    /**
     * Throw the whole stack away (volumes included) and bring it back up. Used
     * after the scenarios that break Postgres replication (S8, S9), where no
     * lighter reset restores the primary/standby topology.
     */
    public void recreateStack() {
        if (LabConfig.envBool("EVENTO_LAB_SKIP_RECREATE", false)) {
            action("stack recreate SKIPPED (EVENTO_LAB_SKIP_RECREATE=true)");
            return;
        }
        action("compose down -v");
        compose(Duration.ofSeconds(300), List.of(), List.of(), "down", "-v", "--remove-orphans");
        action("compose up -d --wait");
        must(compose(Duration.ofSeconds(1200), List.of(), List.of(), "up", "-d", "--wait", "--wait-timeout", "1100"),
                "compose up --wait");
    }

    public boolean composeFileExists() {
        return Files.isRegularFile(config.composeFile());
    }

    public LabConfig config() {
        return config;
    }
}
