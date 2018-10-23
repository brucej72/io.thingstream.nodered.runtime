package io.thingstream.nodered.types;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.thingstream.nodered.service.NodeDefinition;
import io.thingstream.nodered.service.NodeRedConstants;

public class NodePackage {
	
	/*

	{
	    "name"         : "node-red-contrib-samplenode",
	    "version"      : "0.0.1",
	    "description"  : "A sample node for node-red",
	    "dependencies": {
	    },
	    "keywords": [ "node-red" ],
	    "node-red"     : {
	        "nodes": {
	            "sample": "sample/sample.js"
	        }
	    }
	}

	 */
	private static final String KEYWORDS[] = {"node-red"};
	
	private String name;
	private String version;
	private String description;
	private Map<String, String> dependencies;
	private String[] keywords;
	@JsonProperty("node-red")
	private Nodes nodes;
	
	public NodePackage() {}
	
	public NodePackage(String componentType, NodeDefinition nd) {
		
		name = NodeRedConstants.NAME_PREFIX + componentType;
		version = nd.getVersion();
		keywords = KEYWORDS;
		dependencies = new HashMap<String, String>();
		nodes = new Nodes(componentType, componentType + "/" + componentType + ".js");
		
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Map<String, String> getDependencies() {
		return dependencies;
	}

	public void setDependencies(Map<String, String> dependencies) {
		this.dependencies = dependencies;
	}
	
	public void addDependency(String pkgName, String version) {
		this.dependencies.put(pkgName, version);
	}

	public String[] getKeywords() {
		return keywords;
	}

//	public void setKeywords(String[] keywords) {
//		this.keywords = keywords;
//	}

	public Nodes getNodes() {
		return nodes;
	}

	public void setNodes(Nodes nodes) {
		this.nodes = nodes;
	}

	class Nodes {
		
		private Map<String, String> nodes;
		
		public Nodes() {
			
			nodes = new HashMap<String, String>();
			
		}
		
		public Nodes(String name, String path) {
			this();
			nodes.put(name, path);
		}
		
		public void addNode(String name, String path) {
			nodes.put(name, path);
		}

		public Map<String, String> getNodes() {
			return nodes;
		}

		public void setNodes(Map<String, String> nodes) {
			this.nodes = nodes;
		}
		
	}
	
}
