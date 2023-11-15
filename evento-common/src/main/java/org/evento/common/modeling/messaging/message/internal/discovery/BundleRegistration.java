package org.evento.common.modeling.messaging.message.internal.discovery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class BundleRegistration implements Serializable {

	private String bundleId;
	private long bundleVersion;
	private String instanceId;
	private ArrayList<RegisteredHandler> registeredHandlers;

	private HashMap<String, String[]> payloadInfo;


	public BundleRegistration(
			String bundleId,
			long bundleVersion,
			String instanceId,
			ArrayList<RegisteredHandler> registeredHandlers,
			HashMap<String, String[]> payloadInfo
			) {
		this.bundleId = bundleId;
		this.registeredHandlers = registeredHandlers;
		this.bundleVersion = bundleVersion;
		this.payloadInfo = payloadInfo;
		this.instanceId = instanceId;
	}

	public BundleRegistration() {
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


	public HashMap<String, String[]> getPayloadInfo() {
		return payloadInfo;
	}

	public void setPayloadInfo(HashMap<String, String[]> payloadInfo) {
		this.payloadInfo = payloadInfo;
	}

	public String getInstanceId() {
		return instanceId;
	}

	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

	public ArrayList<RegisteredHandler> getRegisteredHandlers() {
		return registeredHandlers;
	}

	public void setRegisteredHandlers(ArrayList<RegisteredHandler> registeredHandlers) {
		this.registeredHandlers = registeredHandlers;
	}
}
