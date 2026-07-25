package com.evento.common.modeling.messaging.message.internal.consumer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodic push of a bundle's parallel-consumption counters, sent on the same admin
 * notification channel as the performance metrics and consumer registration.
 *
 * <p>These numbers live in the bundle — the executors and their permits are bundle-side
 * objects the server has no view of. Pushing a snapshot on a timer is what lets the server
 * expose them as Micrometer meters: the alternative, having the server poll every consumer
 * on each Prometheus scrape, would turn one scrape into N round-trips across the cluster.
 *
 * <p>Bundles that use no {@code ConsumerExecutor} send nothing at all, so the channel stays
 * silent for applications that have not opted into parallel consumption.
 */
public class ConsumerStatsMessage implements Serializable {

    private List<ExecutorStats> executors = new ArrayList<>();
    private List<ConsumerStats> consumers = new ArrayList<>();

    public List<ExecutorStats> getExecutors() {
        return executors;
    }

    public void setExecutors(List<ExecutorStats> executors) {
        this.executors = executors == null ? new ArrayList<>() : executors;
    }

    public List<ConsumerStats> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<ConsumerStats> consumers) {
        this.consumers = consumers == null ? new ArrayList<>() : consumers;
    }

    /** One {@code ConsumerExecutor}, shared bundle-wide by every handler naming it. */
    public static class ExecutorStats implements Serializable {

        private String name;
        private int capacity;
        private int inFlight;
        private long admitted;
        private long rejected;
        private long completed;
        private long failed;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }

        public int getInFlight() { return inFlight; }
        public void setInFlight(int inFlight) { this.inFlight = inFlight; }

        public long getAdmitted() { return admitted; }
        public void setAdmitted(long admitted) { this.admitted = admitted; }

        public long getRejected() { return rejected; }
        public void setRejected(long rejected) { this.rejected = rejected; }

        public long getCompleted() { return completed; }
        public void setCompleted(long completed) { this.completed = completed; }

        public long getFailed() { return failed; }
        public void setFailed(long failed) { this.failed = failed; }
    }

    /** One consumer's view of its own async dispatch. */
    public static class ConsumerStats implements Serializable {

        private String consumerId;
        private String componentName;
        private int inFlight;
        private long submitTimeouts;
        private long transientFailures;

        public String getConsumerId() { return consumerId; }
        public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

        public String getComponentName() { return componentName; }
        public void setComponentName(String componentName) { this.componentName = componentName; }

        public int getInFlight() { return inFlight; }
        public void setInFlight(int inFlight) { this.inFlight = inFlight; }

        public long getSubmitTimeouts() { return submitTimeouts; }
        public void setSubmitTimeouts(long submitTimeouts) { this.submitTimeouts = submitTimeouts; }

        public long getTransientFailures() { return transientFailures; }
        public void setTransientFailures(long transientFailures) { this.transientFailures = transientFailures; }
    }
}
