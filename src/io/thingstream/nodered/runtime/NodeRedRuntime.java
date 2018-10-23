package io.thingstream.nodered.runtime;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import com.eclipsesource.v8.JavaVoidCallback;
import com.eclipsesource.v8.NodeJS;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.utils.MemoryManager;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.thingstream.nodered.service.NodeComponent;
import io.thingstream.nodered.service.NodeRedConstants;
import io.thingstream.nodered.service.NodeRedService;
import io.thingstream.nodered.types.NodeSet;
import io.thingstream.osgi.DictionaryHelper;

@Component(
		name = "NodeRedRuntime",
		enabled = true,
		immediate = true,
		service = NodeRedService.class,
		property = {
			"osgi.command.scope:String=red",
        	"osgi.command.function:String=nodes",
        	"osgi.command.function:String=install",
        	"osgi.command.function:String=lock"
        	}
		)
public class NodeRedRuntime implements NodeRedService {
	
	private static final String COMPONENT_GEN_ROOT = "generated";
	private static final String NODE_RED_PATH = "node.red.path";
		
	private LogService logger;
	private ServiceTracker<NodeComponent, NodeComponent> componentTracker;
	private BundleContext bc;
	
	private File generatedRoot;	
	private NodeRedRunner runner;
	private Object lock;
	
	private Map<String, DeferredCall> deferredCalls;;
	
	private static ObjectMapper jsonMapper = new ObjectMapper(new JsonFactory());
	private RedClient client = new RedClient();
	
	/********************* Lifecycle *********************/
	
	@Activate
	public void activate(ComponentContext context) throws IOException {
		
		logger.log(LogService.LOG_INFO, "Starting Node-red service");
		bc = context.getBundleContext();
		
		lock = new Object();
		deferredCalls = Collections.synchronizedMap(new HashMap<String, DeferredCall>());
				
		// get a handle to the scratch space where we can create 'temp' packages
		
		generatedRoot = bc.getBundle().getDataFile(COMPONENT_GEN_ROOT);
		if(!generatedRoot.exists()) {
			
			if(!generatedRoot.mkdirs()) {
				
				logger.log(LogService.LOG_ERROR, "Unable to create scratch space for node-red modules");
				throw new IOException("Unable to create scratch space for node-red modules");
				
			}
			
		}
		
		// Set up node-red
		
		String extRedPath = DictionaryHelper.getAndCheckEnv(NODE_RED_PATH, context);
		File redScript = null;
		
		if(extRedPath == null || extRedPath.equals("")) {
		
			File nrFile = bc.getBundle().getDataFile("node-red");
			if(!nrFile.exists()) {
				
				logger.log(LogService.LOG_INFO, "Installing node-red");
				nrFile.mkdirs();
										
				URL src = context.getBundleContext().getBundle().getEntry("/node-red.zip");
				UnzipUtility.unzip(src, nrFile);
				
				logger.log(LogService.LOG_INFO, "node-red installed");
				
			}
					
			redScript = new File(nrFile, "red-embed.js");
			logger.log(LogService.LOG_INFO, "RED: " + redScript.getAbsolutePath());
			
		} else {
			
			redScript = new File(extRedPath);
			
		}

		if(!redScript.exists()) {
			
			throw new FileNotFoundException("Node-red script cannot be found");
			
		}
		
		runner = new NodeRedRunner(redScript);
		runner.start();
		
		// wait for node-red to startup
		
		logger.log(LogService.LOG_INFO, "Activator waiting on node-red startup");
		synchronized(lock) {
			
			try {
				
				lock.wait();
				
			} catch(InterruptedException ix) {
				
				
			}
			
		}
		
		// start watching for NodeComponent services
		
		logger.log(LogService.LOG_INFO, "Starting node-red component tracker");
		
		componentTracker = new ServiceTracker<NodeComponent, NodeComponent>(context.getBundleContext(), NodeComponent.class, new ComponentTrackerCustomizer());
		componentTracker.open();
		
		logger.log(LogService.LOG_INFO, "Node-red active");
				
	}
	
	@Deactivate
	public void deactivate(ComponentContext context) {
		
		logger.log(LogService.LOG_INFO, "Stopping Node-red service");
		
		componentTracker.close();
		componentTracker = null;
		
		if(runner != null && runner.running()) {
			
			runner.shutdown();
			
			logger.log(LogService.LOG_INFO, "Waiting on Node-red shutdown");
			
			synchronized(lock) {
				
				try {
					
					lock.wait(60 * 1000);
					
				} catch (InterruptedException e) {
					
					// do nothing
					
				}
				
			}
			
		}
		
		logger.log(LogService.LOG_INFO, "Node-red shutdown complete - service stopped");
		logger = null;
		
	}
	
	// Service tracker for node-red components
	
	class ComponentTrackerCustomizer implements ServiceTrackerCustomizer<NodeComponent, NodeComponent> {
		
		private Object dbLock = new Object();

		public NodeComponent addingService(ServiceReference<NodeComponent> reference) {
			
			NodeComponent nc = bc.getService(reference);
			
			String description = (String) reference.getProperty(NodeComponent.COMPONENT_DESCRIPTION);
			if(description == null) {
				
				description = new String();
				
			}
			
			String nodeType = getNodeType(reference, nc);
			if(nodeType == null) {
				
				return null;
				
			}
			
			logger.log(LogService.LOG_INFO, "addingService: " + nodeType);
			
			// check to see if component is in map at the correct version & take appropriate steps
			// then do the job
									
			synchronized(dbLock) {
						
				for(NodeSet n : client.getNodes()) {
										
					if(n.getName().equals(nodeType)) {
							
						logger.log(LogService.LOG_INFO, "Uninstalling node type: " + n.getName() + " version: " + n.getVersion());
						client.uninstall(NodeRedConstants.NAME_PREFIX + nodeType);
						break;
						
					}
					
				}
				
				
			}
			
			File packageLocation = null;
			
			try {
				
				logger.log(LogService.LOG_INFO, "Creating NPM package for: " + nodeType);
				packageLocation = Packager.createPackage(nodeType, nc, description, generatedRoot);
				
			} catch(IOException ix) {
				
				logger.log(LogService.LOG_ERROR, "Unable to create NPM package for service: " + nc.getClass().getName(), ix);
				bc.ungetService(reference);
				return null;
				
			}
			
			// register the package with the runtime
			
			logger.log(LogService.LOG_INFO, "Installing node module for: " + nodeType);
			client.install(packageLocation.getAbsolutePath());
							
			DeferredCall dc = deferredCalls.remove(nodeType);
			if(dc != null) {
				
				logger.log(LogService.LOG_INFO, "Executing deferred call for: " + nodeType);
				nc.onCreate(dc.getNodeId(), dc.getConfig());
				
			}
			
			return nc;
			
		}

		public void modifiedService(ServiceReference<NodeComponent> reference, NodeComponent service) {
			
			// do nothing
			
		}

		public void removedService(ServiceReference<NodeComponent> reference, NodeComponent nc) {
			
			String nodeType = getNodeType(reference, nc);
			logger.log(LogService.LOG_INFO, "removedService: " + nodeType);
 			bc.ungetService(reference);
			
		}
		
		private String getNodeType(ServiceReference<NodeComponent> ref, NodeComponent nc) {
			
			String nodeType = (String) ref.getProperty(ComponentConstants.COMPONENT_NAME);
			if(nodeType == null || nodeType.equals("")) {
				
				nodeType = null;
				logger.log(LogService.LOG_WARNING, "NodeComponent located with no name - using class: " + nc.getClass().getName());
				
			}
			
			return nodeType;
			
		}
		
		
	}
	
	public boolean isActive() {
		
		return runner.running();
		
	}
	
	/********************* Node-red *********************/
	
	class NodeRedRunner extends Thread {
		
		private File f;
		private boolean run = false;
		private NodeJS nodeRed;
		private V8Object RED;
		
		public NodeRedRunner(File f) {
			
			super("node-red runner");
			this.f = f;
			
		}
		
		public void run() {
			
			logger.log(LogService.LOG_INFO, "Locating library: " + System.getProperty("os.name") + "_" + System.getProperty("os.arch"));
			
			// create node.js runtime and register java callback once node-red is running
			// this callback is called from the red-embed.js script that is 'required' below
			
			nodeRed = NodeJS.createNodeJS();
			V8 runtime = nodeRed.getRuntime();
			runtime.registerJavaMethod(new JavaVoidCallback() {

				public void invoke(V8Object receiver, V8Array parameters) {
					
					logger.log(LogService.LOG_INFO, "Node-red up (initNodeRed complete)");
					
					synchronized(lock) {
						
						lock.notify();
						
					}
					
				}
				
			}, "nodeRedUp");
			
			MemoryManager scope = new MemoryManager(nodeRed.getRuntime());
			
			// require the red-embed.js script and then call its function initNodeRed()
			// once completed, get a handle to the exported RED object
			
			V8Object module = nodeRed.require(f);
			module.executeVoidFunction("initNodeRed", null);
			RED = module.getObject("RED");
			
			// register generic callback functions
			
			registerGenericCallbackMethods(runtime);
			
			// and run the node-red event loop
			
			run = true;
			
			while(run && nodeRed.isRunning()) {

				nodeRed.handleMessage();
				
			}
			
			logger.log(LogService.LOG_INFO, "Node-red runtime terminated (run = " + run + ") releasing resources");
			
			RED.release();
			module.release();
			nodeRed.getRuntime().terminateExecution();
			
			scope.release();
			nodeRed.release();
			
			synchronized(lock) {
				
				lock.notify();
				
			}
			
			run = false;
			
		}
				
		public void shutdown() {
			
			run = false;
			
		}
		
		public synchronized boolean running() {
			
			return run;
			
		}
				
	}
	
	private NodeComponent getComponentByType(String componentType) {
		
		Map<ServiceReference<NodeComponent>, NodeComponent> map = componentTracker.getTracked();
		for(ServiceReference<NodeComponent> ref : map.keySet()) {
			
			String name = (String) ref.getProperty(ComponentConstants.COMPONENT_NAME);
			if(name.equals(componentType)) {
				
				return map.get(ref);
				
			}
			
		}
		
		return null;
		
	}
		
	private void registerGenericCallbackMethods(V8 runtime) {
		
		// create callbacks
		
		JavaVoidCallback onCreate = new JavaVoidCallback() {

			// called via OSGi_onCreate(%2$s, this, config)
			
			public void invoke(V8Object receiver, V8Array parameters) {
									
				MemoryManager scope = new MemoryManager(receiver.getRuntime());
				
				String type = parameters.getString(0);
				String nodeId = parameters.getObject(1).getString("id");
				JsonNode config = jsonify(parameters.getObject(2));
				
				NodeComponent nc = getComponentByType(type);
				if(nc != null) {
				
					nc.onCreate(nodeId, config);
					
				} else {
						
					logger.log(LogService.LOG_WARNING, "Deferring call to onCreate() for type: " + type);
					deferredCalls.put(type, new DeferredCall(nodeId, config));
									
				}
				
				scope.release();
				
			}
			
		};
		
		JavaVoidCallback onClose = new JavaVoidCallback() {

			// called via OSGi_onClose(%2$s, this)
			
			public void invoke(V8Object receiver, V8Array parameters) {
				
				MemoryManager scope = new MemoryManager(receiver.getRuntime());
				
				String type = parameters.getString(0);
				V8Object self = parameters.getObject(1);
				String id = self.getString("id");
				
				NodeComponent nc = getComponentByType(type);
				if(nc != null) {
				
					nc.onClose(id);
					
				} else {
					
					logger.log(LogService.LOG_WARNING, "Unable to dispatch onClose() - component not found: " + type);
					
				}
				
				scope.release();
				
			}
			
		};
		
		JavaVoidCallback onMessage = new JavaVoidCallback() {

			// called via OSGi_onMessage(%2$s, this, msg)
			
			public void invoke(V8Object receiver, V8Array parameters) {
									
				MemoryManager scope = new MemoryManager(receiver.getRuntime());
				
				String type = parameters.getString(0);
				V8Object self = parameters.getObject(1);
				JsonNode msg = jsonify(parameters.getObject(2));
				
				NodeComponent nc = getComponentByType(type);
				if(nc != null) {
				
					JsonNode[] vals = nc.onMessage(self.getString("id"), msg);
					if(vals != null && vals.length != 0) {
						
						V8Array array = parseJson(receiver.getRuntime(), vals);
						self.executeVoidFunction("send", array);
					
					}
					
				} else {
					
					logger.log(LogService.LOG_WARNING, "Unable to dispatch onClose() - component not found: " + type);
					
				}
				
				scope.release();
				
			}
			
		};
		
		logger.log(LogService.LOG_INFO, "Registering generic callback methods for nodes");
		
		runtime.registerJavaMethod(onCreate, "OSGi_onCreate");
		runtime.registerJavaMethod(onClose, "OSGi_onClose");
		runtime.registerJavaMethod(onMessage, "OSGi_onMessage");
		
	}
	
	private V8Array parseJson(V8 v8, JsonNode[] nodes) {
		
		V8Array values = new V8Array(v8);
		V8Object json = v8.getObject("JSON");
		
		for(JsonNode n : nodes) {
			
			V8Array parameters = new V8Array(v8).push(n.toString());
	        V8Object result = json.executeObjectFunction("parse", parameters);
	        values.push(result);
	        result.release();
	        parameters.release();
			
		}
		
		json.release();
		return values;
		
	}
	
	private JsonNode jsonify(V8Object v8Object) {
		
		V8 v8 = v8Object.getRuntime();
        V8Object json = v8.getObject("JSON");

        V8Array parameters = new V8Array(v8).push(v8Object);
        String result = json.executeStringFunction("stringify", parameters);
        parameters.release();

        json.release();
        v8Object.release();
        
        JsonNode rootNode = null;
        try {
        	
        	rootNode = jsonMapper.readTree(result);  
        	
        } catch(IOException ix) {
        	
        	logger.log(LogService.LOG_INFO, "Error mapping JSON", ix);
        	rootNode = null;
        	
        }
        
        return rootNode;
        
    }
	
	/******************************* environment bindings ********************************/
	
	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	public void bindLogger(LogService logger) {
		
		this.logger = logger;
		
	}
	
	public void unbindLogger(LogService logger) {
		
		this.logger = null;
		
	}
	
	/******************************* shell commands ********************************/
	
	public void nodes() {
		
		NodeSet[] nodes = client.getNodes();
		for(NodeSet ns : nodes) {
			
			System.out.println(ns.getName());
			
		}
		
	}
	
	public void install(String s) {
		
		client.install(s);
		
	}
	
	public void lock() {
		
		
		
	}


}
