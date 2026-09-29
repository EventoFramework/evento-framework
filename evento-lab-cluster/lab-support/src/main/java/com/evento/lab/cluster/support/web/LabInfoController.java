package com.evento.lab.cluster.support.web;

import com.evento.lab.cluster.support.BundleRunner;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/** Identity, deployment plan and cheap resource numbers, served by every bundle. */
@RestController
public class LabInfoController {

    private final BundleRunner runner;

    public LabInfoController(BundleRunner runner) {
        this.runner = runner;
    }

    /**
     * Who this JVM is: bundle, instance, zone, contexts and the consumer engines
     * they turn into, the bus addresses and which framework features were
     * available. At HEAD before G2 a bundle only ever dials the first address,
     * so {@code server} is the one it talks to; {@code busState} says whether
     * that session is up.
     */
    @GetMapping("/whoami")
    public Map<String, Object> whoami() {
        return runner.describe();
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
