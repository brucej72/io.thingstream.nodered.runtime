package io.thingstream.nodered.runtime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import io.thingstream.nodered.service.NodeComponent;
import io.thingstream.nodered.service.NodeDefinition;
import io.thingstream.nodered.types.NodePackage;

public class Packager {
	
	private static final String PACKAGE_FILE = "package.json";
	private static final String JS_EXTENSION = ".js";
	private static final String HTML_EXTENSION = ".html";
	
	/**
	 * Create an NPM package for the NodeComponent under the directory specified and return
	 * a File object pointing to the top level of the package
	 * @param nc - the NodeComponent
	 * @param pkgDescription - a simple text description of the package
	 * @param rootDir - the location on the file system where we wish to build the package
	 * @return a File object pointing to the top of the generated package
	 * @throws IOException
	 */
	public static File createPackage(String componentType, NodeComponent nc, String pkgDescription, File rootDir) throws IOException {
		
		NodeDefinition nd = nc.getDefinition();
								
		// create the top level dir for the package under generatedRoot
		
		File dir = new File(rootDir, componentType);
		if(!dir.exists()) {
		
			if(!dir.mkdir()) throw new IOException("Failed to create directory: " + dir.getAbsolutePath());
			
		}
		
		// write the package.json
		
		File pkgFile = new File(dir, PACKAGE_FILE);
		
		try(FileOutputStream fos = new FileOutputStream(pkgFile)) {
			
			NodePackage pkg = new NodePackage(componentType, nd);
			pkg.setDescription(pkgDescription);
			NodeComponentProcessor.generatePackage(pkg, fos);
			
		} catch(IOException ix) {
			
			throw new IOException("Error writing package.json at: " +pkgFile.getAbsolutePath(), ix);
			
		}
		
		// create the subdirs
		
		File scriptDir = new File(dir, componentType);
		if(!scriptDir.exists()) {
		
			if(!scriptDir.mkdir()) throw new IOException("Failed to create directory: " + dir.getAbsolutePath());
			
		}
		
		// write the js file
		
		File jsFile = new File(scriptDir, componentType + JS_EXTENSION);
		
		try(FileOutputStream fos = new FileOutputStream(jsFile)) {

			NodeComponentProcessor.generateJS(componentType, nc, fos);
			
		} catch(IOException ix) {
			
			throw new IOException("Unable to create package.json for " + componentType + " at: " + jsFile.getAbsolutePath(), ix);
			
		}
		
		// write the html file
		
		File htmlFile = new File(scriptDir, componentType + HTML_EXTENSION);
		
		try(FileOutputStream fos = new FileOutputStream(htmlFile)) {

			NodeComponentProcessor.generateHtml(componentType, nc, fos);
			
		} catch(IOException ix) {
			
			throw new IOException("Unable to create package.json for " + componentType + " at: " + htmlFile.getAbsolutePath(), ix);
			
		}
		
		return dir;
		
	}

}
