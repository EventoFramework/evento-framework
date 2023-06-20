package org.evento.common.modeling.messaging.message.internal.discovery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class ClusterNodeApplicationDiscoveryResponse implements Serializable {

	private String bundleId;
	private long bundleVersion;
	private ArrayList<RegisteredHandler> registeredHandlers;

	private HashMap<String, String> schemas;

	public ClusterNodeApplicationDiscoveryResponse(
			String bundleId,
			long bundleVersion,
			ArrayList<RegisteredHandler> registeredHandlers,
			HashMap<String, String> schemas) {
		this.bundleId = bundleId;
		this.registeredHandlers = registeredHandlers;
		this.bundleVersion = bundleVersion;
		this.schemas = schemas;
	}

	public ClusterNodeApplicationDiscoveryResponse() {
	}

	public String getBundleId() {
		return bundleId;
	}

	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}

	public ArrayList<RegisteredHandler> getHandlers() {
		return registeredHandlers;
	}

	public void setHandlers(ArrayList<RegisteredHandler> registeredHandlers) {
		this.registeredHandlers = registeredHandlers;
	}

	public long getBundleVersion() {
		return bundleVersion;
	}

	public void setBundleVersion(long bundleVersion) {
		this.bundleVersion = bundleVersion;
	}


	public HashMap<String, String> getSchemas() {
		return schemas;
	}

	public void setSchemas(HashMap<String, String> schemas) {
		this.schemas = schemas;
	}
}
