package io.thingstream.nodered.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.thingstream.nodered.service.NodeComponent;
import io.thingstream.nodered.service.NodeDefaults;
import io.thingstream.nodered.service.NodeDefaults.EditDialogType;
import io.thingstream.nodered.service.NodeDefinition;
import io.thingstream.nodered.types.NodePackage;

public class NodeComponentProcessor {
	
	private static ObjectMapper mapper;
	
	static {
		
		mapper = new ObjectMapper();
		
	}
	
	private static final String REGISTER_TEMPLATE = 
			"<script type=\"text/javascript\">\n" +
			"RED.nodes.registerType('%s',%s" +
			");\n" +
			"</script>";
	
	private static final String DEFAULT_ROW = 
			"<div class=\"form-row\">\n" +
			"\t<label for=\"node-input-%1$s\"><i class=\"icon-tag\"></i> %2$s</label>\n" +
			"\t<input type=\"%3$s\" id=\"node-input-%1$s\" placeholder=\"%2$s\">\n" +
			"</div>";
	
	private static final String SELECT_ROW_PREFIX = 
			"<div class=\"form-row\">\n" +
			"\t<label for=\"node-input-%1$s\"><i class=\"icon-tag\"></i> %2$s</label>\n" +
			"\t<select id=\"node-input-%1$s\">";
	
	private static final String SELECT_ROW_BODY = 
			"\t\t<option value=\"%1$s\">%1$s</option>";
	
	private static final String SELECT_ROW_SUFFIX = 
			"\t</select>" +
			"</div>";
	
	private static final String JS_TEMPLATE = 
			"module.exports = function(RED) {\n" +
			"\tfunction %1$s(config) {\n\n" +
			"\t\tRED.nodes.createNode(this, config);\n" +
			"\t\tvar node = this;\n" +
			"\t\tOSGi_onCreate(\"%2$s\", this, config);\n\n" +
			"\t\tthis.on('input', function(msg) {\n" +
			"\t\t\tOSGi_onMessage(\"%2$s\", this, msg);\n" +
			"\t\t});\n\n" +
			"\t\tthis.on('close', function() {\n" +
			"\t\t\tOSGi_onClose(\"%2$s\", this);\n" +
			"\t\t});\n\n" +
			"\t}\n" +
			"\tRED.nodes.registerType(\"%1$s\", %2$s);\n" +
			"%3$s" +
			"}\n";
	
	private static final String JS_NO_INPUTS = 
			"\n" +
			"\tRED.httpAdmin.post(\"/%1$s/:id\", RED.auth.needsPermission(\"%1$s.write\"), function(req,res) {\n" +
			"\t\tvar node = RED.nodes.getNode(req.params.id);\n" +
			"\t\tif (node != null) {\n" +
            "\t\t\ttry {\n" +
            "\t\t\t\tnode.receive(req.body);\n"+
            "\t\t\t\tres.sendStatus(200);\n" +
            "\t\t\t} catch(err) {\n"+
            "\t\t\t\tres.sendStatus(500);\n" +
            "\t\t\t\tnode.error(RED._(\"inject.failed\",{error:err.toString()}));\n" +
            "\t\t\t}\n" +
        	"\t\t} else {\n" +
            "\t\t\tres.sendStatus(404);\n" +
        	"\t\t}\n" +
    		"\t});\n\n";
	
	/*
	
	module.exports = function(RED) {
	
	    function ThingstreamPublish(config) {
	    	
	        RED.nodes.createNode(this,config);
	        var node = this;
	        thingstream_onCreate(this, config);    
	        
	        this.on('input', function(msg) {
	            thingstream_onMessage();
	        });
	        
	        this.on('close', function() {
    			thingstream_onClose();
			});
	
	    }
    	RED.nodes.registerType("thingstream-publish",ThingstreamPublish);
	}
	
	*/
	
	public static void main(String args[]) {
		
		System.out.println(JS_NO_INPUTS);
		
	}
	
	public static void generatePackage(NodePackage pkg, OutputStream out) throws IOException {
		
		mapper.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, true);
				
		try {
			
			mapper.writerWithDefaultPrettyPrinter().writeValue(out, pkg);
			out.flush();
			
		} catch(Exception ix) {
			
			throw new IOException("Error generating package JSON", ix);
			
		}
		
	}
	
	public static void generateJS(String componentType, NodeComponent nc, OutputStream out) throws IOException {
		
		mapper.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);

		NodeDefinition nd = nc.getDefinition();
				
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
		try {
			
			String injectJS = "";
			
			if(nd.getInputs() == 0 && nd.getOutputs() > 0 && !nd.getCategory().equals(NodeDefinition.CATEGORY_CONFIG)) {
				
				injectJS = String.format(JS_NO_INPUTS, componentType);
				
			}
			
			pw.println(String.format(JS_TEMPLATE, componentType, componentType, injectJS));
			pw.flush();
			
		} catch(Exception ix) {
			
			throw new IOException("Error generating HTML", ix);
			
		}
	
		
	}
	
	public static void generateHtml(String componentType, NodeComponent nc, OutputStream out) throws IOException {
		
		mapper.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
		
		NodeDefinition nd = nc.getDefinition();
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(out)); 
		
		try {
			
			String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(nd);
			pw.println(String.format(REGISTER_TEMPLATE, componentType, s));
			pw.println();
			
			pw.println(String.format("<script type=\"text/x-red\" data-template-name=\"%s\">", componentType));
			
			Map<String, NodeDefaults> defaults = nd.getDefaults();
			for(String label : defaults.keySet()) {
				
				NodeDefaults row = defaults.get(label);
				NodeDefaults.EditDialogType editType = row.getEditDialogType();
				if(editType == EditDialogType.select) {
					
					pw.println(String.format(SELECT_ROW_PREFIX, label.toLowerCase(), row.getEditDialogLabel(), row.getEditDialogType().toString()));	// TODO: check this
					
					for(String option : row.getSelectOptions()) {
						
						pw.println(String.format(SELECT_ROW_BODY, option));
						
					}
					
					pw.println(SELECT_ROW_SUFFIX);
					
				} else {
					
					pw.println(String.format(DEFAULT_ROW, label.toLowerCase(), row.getEditDialogLabel(), row.getEditDialogType().toString()));	// TODO: check this
				
				}
								
			}
			
			pw.println("</script>");
			pw.println();
			
			pw.println(String.format("<script type=\"text/x-red\" data-help-name=\"%s\">", componentType));
			pw.println(nd.getHelpText());
			pw.println("</script>");
			
			pw.flush();
			
		} catch(Exception ix) {
			
			throw new IOException("Error generating Javascript", ix);
			
		}
		
	}

}
