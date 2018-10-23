package io.thingstream.nodered.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public class DeferredCall {
	
	private String nodeId;
	private JsonNode config;
	
	public DeferredCall(String nodeId, JsonNode config) {
		
		this.nodeId = nodeId;
		this.config = config;
		
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public JsonNode getConfig() {
		return config;
	}

	public void setConfig(JsonNode config) {
		this.config = config;
	}

}
