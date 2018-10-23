package io.thingstream.nodered.runtime;

import java.io.IOException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.thingstream.nodered.types.NodeSet;

public class RedClient {
	
	// BKJ: TODO: fix port from config
	
	private static final String REST_URI = "http://127.0.0.1:1880/";
	
	private Client redClient;
	private static ObjectMapper mapper = new ObjectMapper();
	
	public RedClient() {
		
		redClient = ClientBuilder.newClient();
		
	}
	
	public NodeSet[] getNodes() {
		
		String s = redClient.target(REST_URI + "nodes")
			.request()
			.header("Accept", MediaType.APPLICATION_JSON)
			.get(String.class);
				
		NodeSet[] set = null;
		
		try {
			
			set = mapper.readValue(s, NodeSet[].class);
		
		} catch (IOException e) {

			set = new NodeSet[0];
			
		}
		
		return set;
        
    }
	
	public boolean install(String path) {
		
		Module m = new Module(path);
		Response resp = redClient.target(REST_URI + "nodes")
    		.request(MediaType.APPLICATION_JSON)
    		.post(Entity.entity(m, MediaType.APPLICATION_JSON));
		return resp.getStatus() == 200;

	}
	
	public boolean uninstall(String nodeName) {
				
		Response resp = redClient.target(REST_URI + "nodes/" + nodeName)
	    	.request(MediaType.APPLICATION_JSON)
	    	.delete();
		return resp.getStatus() == 200;
		
	}
	
	class Module {
		
		private String module;
		
		public Module() {}
		
		public Module(String module) {
			this.module = module;
		}

		public String getModule() {
			return module;
		}

		public void setModule(String module) {
			this.module = module;
		}
		
	}

}
