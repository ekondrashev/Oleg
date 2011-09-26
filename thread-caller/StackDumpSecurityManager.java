public class StackDumpSecurityManager extends SecurityManager  {

    private String[] includeCreateThread = new String[0];
    private String[] excludeCreateThread = new String[0];

    private String[] includeWriteFile = new String[0];
    private String[] excludeWriteFile = new String[0];

    private int logLevel = 0;
    private String traceElementSeparator = ";; ";

    public StackDumpSecurityManager() {
        super();
        {
        String log = System.getProperty("StackDumpSecurityManager.logLevel");
        if(log != null && log.trim().length() > 0) {
            logLevel = Integer.valueOf(log);
            if(logLevel > 0) { System.out.println("----------INFO StackDumpSecurityManager init. logLevel=" + logLevel); }
        }
        }
        {
        String sep = System.getProperty("StackDumpSecurityManager.traceElementSeparator");
        if(sep != null && sep.trim().length() > 0) {
            traceElementSeparator = sep;
            if(logLevel > 0) { System.out.println("----------INFO StackDumpSecurityManager init. traceElementSeparator=" + traceElementSeparator); }
        }
        }
        {
        String includeCT = System.getProperty("StackDumpSecurityManager.includeCreateThread");
        if(includeCT != null && includeCT.trim().length() > 0) {
            includeCreateThread = includeCT.split(";");
            if(logLevel > 0) {
                System.out.print("----------INFO StackDumpSecurityManager init. includeCreateThread:");
                for(String ict : includeCreateThread) { System.out.print(ict + ";"); } System.out.println("");
            }
        }
        }
        {
        String excludeCT = System.getProperty("StackDumpSecurityManager.excludeCreateThread");
        if(excludeCT != null && excludeCT.trim().length() > 0) {
            excludeCreateThread = excludeCT.split(";");
            if(logLevel > 0) {
                System.out.print("----------INFO StackDumpSecurityManager init. excludeCreateThread:");
                for(String ect : excludeCreateThread) { System.out.print(ect + ";"); } System.out.println("");
            }
        }
        }
        {
        String includeWF = System.getProperty("StackDumpSecurityManager.includeWriteFile");
        if(includeWF != null && includeWF.trim().length() > 0) {
            includeWriteFile = includeWF.split(";");
            if(logLevel > 0) {
                System.out.print("----------INFO StackDumpSecurityManager init. includeWriteFile:");
                for(String iwf : includeWriteFile) { System.out.print(iwf + ";"); } System.out.println("");
            }
        }
        }
        {
        String excludeWF = System.getProperty("StackDumpSecurityManager.excludeWriteFile");
        if(excludeWF != null && excludeWF.trim().length() > 0) {
            excludeWriteFile = excludeWF.split(";");
            if(logLevel > 0) {
                System.out.print("----------INFO StackDumpSecurityManager init. excludeWriteFile:");
                for(String ewf : excludeWriteFile) { System.out.print(ewf + ";"); } System.out.println("");
            }
        }
        }
    }

    private void dumpIfMatch(String[] includeRegex, String[] excludeRegex, String param, String prefix) {
        Thread t = Thread.currentThread();
        StackTraceElement[] listSTE = t.getStackTrace();

        if(includeRegex.length == 0) {
            return;
        }

        if(param.length() > 0) {
            // test if the parameter is excluded, so we don't need to do other tests
            for(String ere : excludeRegex) {
                if(logLevel > 1) {
                    System.out.println("----------DEBUG StackDumpSecurityManager.dumpIfMatch " + prefix + " check exclude first'" + 
                                       param + "' matches '" + ere + "'" + param.matches(ere));
                }
                if(param.matches(ere)) {
                    return;
                }
            }
        }

        boolean matches = false;
        StringBuffer dump = new StringBuffer("--------StackDumpSecurityManager " + prefix + " ------{{");
        dump.append(param).append(traceElementSeparator);
        for(String ire : includeRegex) {
            if(param.matches(ire)) {
                matches = true;
            }
            for(StackTraceElement ste : listSTE) {
                StringBuffer elem = new StringBuffer(ste.getClassName()).append('#').append(ste.getMethodName()).
                    append('(').append(ste.getFileName()).append(':').append(ste.getLineNumber()).append(")").append(traceElementSeparator);
                dump.append(elem);

                if(logLevel > 1) {
                    System.out.println("----------DEBUG StackDumpSecurityManager.dumpIfMatch  " + prefix + " check include'" 
                                       + elem + "' matches '" + ire + "' result " + (new String(elem).matches(ire)));
                }
                if(matches || new String(elem).matches(ire)) {
                    matches = true;
                }
            }
            if(matches) {
                dump.append("--------StackDumpSecurityManager------}}");
                break;
            }
        }
        if(matches) {
            String sdump = new String(dump);
            for(String ere : excludeRegex) {
                if(logLevel > 1) {
                    System.out.println("----------DEBUG StackDumpSecurityManager.dumpIfMatch  " + prefix + " check exclude '" + 
                                       sdump + "' matches '" + ere + "' result " + sdump.matches(ere));
                }
                if(sdump.matches(ere)) {
                    matches = false;
                    break;
                }
            }
            if(matches) {
                System.out.println("\n\n" + dump + "\n\n");
            }
        }
    }

    public ThreadGroup getThreadGroup() {

        dumpIfMatch(includeCreateThread, excludeCreateThread, "", "getThreadGroup");
        return super.getThreadGroup();
    }


    public void checkWrite(String file) {
        
        try {
            dumpIfMatch(includeWriteFile, excludeWriteFile, file.replace('\\', '/'), "checkWrite");
        } catch(Exception ex) {
            System.err.println(ex);
        }
        
        super.checkWrite(file);
    }
  
};


