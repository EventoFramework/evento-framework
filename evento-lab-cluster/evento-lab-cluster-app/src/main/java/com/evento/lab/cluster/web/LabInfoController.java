package com.evento.lab.cluster.web;

import com.evento.lab.cluster.config.LabBundles;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/** Identity and cheap resource numbers, available in every role. */
@RestController
public class LabInfoController {

    private final LabBundles bundles;

    public LabInfoController(LabBundles bundles) {
        this.bundles = bundles;
    }

    @GetMapping("/whoami")
    public Map<String, Object> whoami() {
        var s = bundles.settings();
        var out = new LinkedHashMap<String, Object>();
        out.put("role", s.role().envValue());
        out.put("region", s.region());
        out.put("cell", s.cell());
        out.put("instance", s.instanceId());
        // At HEAD a bundle only ever dials the first address (G2), so that is
        // the server it talks to; busStates says whether that session is up.
        out.put("server", s.serverAddresses().getFirst().toString());
        out.put("addresses", s.serverAddresses().stream().map(Object::toString).toList());
        out.put("expectedServerInstanceId", s.expectedServerInstanceId());
        out.put("checkpointMode", s.checkpointMode());
        out.put("bundles", bundles.started().keySet());
        out.put("busStates", bundles.busStates());
        out.put("ready", bundles.isReady());
        return out;
    }

    /** Heap and CPU of this JVM, sampled by S12 to see the WATERMARK consumer cost. */
    @GetMapping("/lab/stats")
    public Map<String, Object> stats() {
        var out = new LinkedHashMap<String, Object>();
        var mem = ManagementFactory.getMemoryMXBean();
        out.put("heapUsedBytes", mem.getHeapMemoryUsage().getUsed());
        out.put("heapCommittedBytes", mem.getHeapMemoryUsage().getCommitted());
        out.put("nonHeapUsedBytes", mem.getNonHeapMemoryUsage().getUsed());
        out.put("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os) {
            out.put("processCpuTimeNanos", os.getProcessCpuTime());
            out.put("processCpuLoad", os.getProcessCpuLoad());
        }
        long gcCount = 0;
        long gcMillis = 0;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcMillis += Math.max(0, gc.getCollectionTime());
        }
        out.put("gcCount", gcCount);
        out.put("gcMillis", gcMillis);
        out.put("uptimeMillis", ManagementFactory.getRuntimeMXBean().getUptime());
        return out;
    }
}
