package io.thingstream.nodered.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/*

{
  "id": "node-module-name/node-set-name",
  "name": "node-set-name",
  "types": [ ... ],
  "enabled": true,
  "module": "node-module-name",
  "version": "0.0.6"
}

*/

@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeSet {
	
	private String id;
	private String name;
	private String[] types;
	private boolean enabled;
	private boolean local;
	private String module;
	private String version;
	
	public NodeSet() {}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String[] getTypes() {
		return types;
	}

	public void setTypes(String[] types) {
		this.types = types;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = module;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public boolean isLocal() {
		return local;
	}

	public void setLocal(boolean local) {
		this.local = local;
	}
	
}
