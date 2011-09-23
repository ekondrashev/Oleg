package op.agent;

import java.net.*;

import java.io.*;
import java.lang.reflect.*;
import javax.management.*;


import org.objectweb.asm.*;

import org.jboss.mx.loading.*;
// import org.jboss.util.loading.DelegatingClassLoader;
import org.jboss.logging.*;


/**
 *  JBossClassLoader
 */
public class JBossClassLoader extends UnifiedClassLoader3 {


    private Logger mLog = org.jboss.logging.Logger.getLogger(getClass().getName());
    private AgentClassLoader mAgentClassLoader = new AgentClassLoader();

    public JBossClassLoader(URL url) {
        super(url);
        mAgentClassLoader.setClassLoaderDelegate(this);
    }

    public JBossClassLoader(URL url, URL origURL)   {
        super(url, origURL);
        mAgentClassLoader.setClassLoaderDelegate(this);
    }

    public JBossClassLoader(URL url, URL origURL, LoaderRepository repository)   {
        super(url, origURL, repository);
        mAgentClassLoader.setClassLoaderDelegate(this);
    }

    public JBossClassLoader(URL url, URL origURL, ClassLoader parent, LoaderRepository repository)   {
        super(url, origURL, parent, repository);
        mAgentClassLoader.setClassLoaderDelegate(this);
    }



    /*
      public ObjectName getObjectName() throws MalformedObjectNameException {
      return new ObjectName("AgentClassLoader");
      }
    */
 

    public Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if(name.startsWith("java.") || name.startsWith("Ljava/") || name.startsWith("Lsun/") || name.startsWith("Lorg/objectweb/") || name.startsWith("Lop/agent/")) {
            return super.loadClass(name, resolve);
        }
        System.err.println("++++++++++++++++++++++++++++++++++++++++++++++JBossClassLoader: loading class '" + name);
        mLog.info("++++++++++++++++++++++++++++++++++++++++++++++AgentClassLoader: loading class '" + name + "' with on the fly adaptation");
        // Thread.currentThread().setContextClassLoader(this);
        return mAgentClassLoader.loadClass(name);
    }

    /*
      public URL getResourceLocally(String name) {
      mLog.info("getting resource locally: "+name);
      return (mParent == null)? getURL() : mParent.getResourceLocally(name);
      // return getURL();
      }

 
      public Package[] getPackages() {
      mLog.info("return packages: "+getPackage(name));
      return new Package[]{getPackage(name)};
      }
    */

    public synchronized Class loadClassLocally(final String name, final boolean resolve) throws ClassNotFoundException  {
        if(name.startsWith("java.") || name.startsWith("Ljava/") || name.startsWith("Lsun/") || name.startsWith("Lorg/objectweb/") || name.startsWith("Lop/agent/")) {
            return super.loadClassLocally(name, resolve);
        }
        System.err.println("++++++++++++++++++++++++++++++++++++++++++++++JBossClassLoader: loading class '" + name);
        mLog.info("++++++++++++++++++++++++++++++++++++++++++++++AgentClassLoader: loading class '" + name + "' with on the fly adaptation");
        // Thread.currentThread().setContextClassLoader(this);
        return mAgentClassLoader.loadClass(name);
    }
};






