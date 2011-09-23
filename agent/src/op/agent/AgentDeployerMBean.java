package op.agent;
 
import java.net.*;

import javax.management.loading.*;


import org.jboss.deployment.*;
import org.jboss.logging.*;
import org.jboss.mx.loading.*;
 

public class AgentDeployerMBean extends EARDeployer {
 
    protected Logger mLog;
 
    static AgentClassLoader mAgentClassLoader;

    public static AgentClassLoader getClassLoader() {
        return mAgentClassLoader;
    }


    public AgentDeployerMBean() {
        mLog = org.jboss.logging.Logger.getLogger(getClass().getName());
        mLog.info("************************* AgentDeployerMBean ctor ****************************************");
    }
 
    /*
    public boolean accepts(DeploymentInfo di) {
        String urlStr = di.url.toString();
        String idtv_ear = "/idtv3_se.ear/";
        boolean res = urlStr.lastIndexOf(idtv_ear) == urlStr.length() - idtv_ear.length();
        // mLog.info("?? accepting " + urlStr + " res " + res + " ??");
        if (res) {
            mLog.info("++++++++++++++++++++++++++++++ accepted: "+di);
        }
        return res;
    }
    */


    public void init(DeploymentInfo di) throws DeploymentException {
        mLog.info("+++++++++++++++++++++++++++++++++++++++++initializing: " + di.localUrl);
        try {
            //            LoaderRepositoryFactory.createLoaderRepository(server, di.repositoryConfig);
            mAgentClassLoader = new AgentClassLoader(di.localUrl);
            ClassLoaderRepository clr = di.getServer().getClassLoaderRepository();
            if(clr instanceof UnifiedLoaderRepository3) {
                ((UnifiedLoaderRepository3)clr).addClassLoader(mAgentClassLoader);
            }

            // di.createClassLoaders();
            // mAgentClassLoader = new AgentClassLoader("op.agent.AgentClassLoader", new URL[]{di.localUrl}, di.ucl);
            // di.ucl.getLoaderRepository().addClassLoader(mAgentClassLoader);
            // ((org.jboss.mx.loading.UnifiedLoaderRepository3)di.ucl.getLoaderRepository()).registerClassLoader(mAgentClassLoader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.init(di);
    }


 
    // protected void deployUrl(DeploymentInfo di, URL url, String name) throws DeploymentException {
    //     mLog.info("************************* deployUrl ****************************************");
    //     mLog.info("di: "+di+", url:"+url+", name: "+name);
    //     super.deployUrl(di, url, name);
    // }


};