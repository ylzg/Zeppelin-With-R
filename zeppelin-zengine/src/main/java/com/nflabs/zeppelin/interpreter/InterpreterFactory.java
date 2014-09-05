package com.nflabs.zeppelin.interpreter;


import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;

public class InterpreterFactory {
	Logger logger = LoggerFactory.getLogger(InterpreterFactory.class);
	
	private Map<String, Object> share = Collections.synchronizedMap(new HashMap<String, Object>());
	private Map<String, ClassLoader> cleanCl = Collections.synchronizedMap(new HashMap<String, ClassLoader>());
	
	private ZeppelinConfiguration conf;
	Map<String, String> replNameClassMap = new HashMap<String, String>();
	String defaultReplName;

	public InterpreterFactory(ZeppelinConfiguration conf){
		this.conf = conf;
		String replsConf = conf.getString(ConfVars.ZEPPELIN_INTERPRETERS);
		String[] confs = replsConf.split(",");
		for(String c : confs) {
			String [] nameAndClass = c.split(":");
			replNameClassMap.put(nameAndClass[0], nameAndClass[1]);
			if(defaultReplName==null){
				defaultReplName = nameAndClass[0];
			}
		}
	}
	
	public String getDefaultReplName(){
		return defaultReplName;
	}
	
	public Interpreter createRepl(String replName, Properties properties) {
		String className = replNameClassMap.get(replName!=null ? replName : defaultReplName);
		logger.info("find repl class {} = {}", replName, className);
		if(className==null) {
			throw new RuntimeException("Configuration not found for "+replName);
		} 
		return createRepl(replName, className, properties);
	}
	
	public Interpreter createRepl(String dirName, String className, Properties property) {
		logger.info("Create repl {} from {}", className, dirName);
		
		ClassLoader oldcl = Thread.currentThread().getContextClassLoader();		
		try {

			ClassLoader ccl = cleanCl.get(dirName);			
			if(ccl==null) { // create
				File path = new File(conf.getInterpreterDir()+"/"+dirName);
				logger.info("Reading "+path.getAbsolutePath());
				URL [] urls = recursiveBuildLibList(path);
				ccl = new URLClassLoader(urls, oldcl);
				cleanCl.put(dirName, ccl);
			}
			
			boolean separateCL = true;
			try{ // check if server's classloader has driver already. 
				Class cls = this.getClass().forName(className);
				if(cls!=null) separateCL = false;				
			} catch(Exception e) {
				// nothing to do.
			}
			
			URLClassLoader cl;

			if(separateCL==true) {
				cl = URLClassLoader.newInstance(new URL[]{}, (URLClassLoader) ccl);
			} else {
				cl = (URLClassLoader) ccl;
			}
			Thread.currentThread().setContextClassLoader(cl);
			
			Class<Interpreter> replClass = (Class<Interpreter>) cl.loadClass(className);
			Constructor<Interpreter> constructor = replClass.getConstructor(new Class []{Properties.class});
			Interpreter repl = constructor.newInstance(property);
			property.put("share", share);
			return new ClassloaderInterpreter(repl, cl, property);
		} catch (SecurityException e) {
			throw new InterpreterException(e);
		} catch (NoSuchMethodException e) {
			throw new InterpreterException(e);
		} catch (IllegalArgumentException e) {
			throw new InterpreterException(e);
		} catch (InstantiationException e) {
			throw new InterpreterException(e);
		} catch (IllegalAccessException e) {
			throw new InterpreterException(e);
		} catch (InvocationTargetException e) {
			throw new InterpreterException(e);
		} catch (ClassNotFoundException e) {
			throw new InterpreterException(e);
		} catch (MalformedURLException e) {
			throw new InterpreterException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(oldcl);
		}
	}
	
	private URL [] recursiveBuildLibList(File path) throws MalformedURLException{
		URL [] urls = new URL[0];
		if (path==null || path.exists()==false){ 
			return urls;
		} else if (path.getName().startsWith(".")) {
			return urls;
		} else if (path.isDirectory()) {
			File[] files = path.listFiles();			
			if (files!=null) {				
				for (File f : files) {
					urls = (URL[]) ArrayUtils.addAll(urls, recursiveBuildLibList(f));
				}
			}
			return urls;
		} else {
			return new URL[]{path.toURI().toURL()};
		}
	}
}