public class StackDumpSecurityManager extends SecurityManager  {

    private static final String[] EMPTY_ARRAY = new String[0];
    private static final String[] EMPTY_ELEM_ARRAY = new String[]{""};

    private class PropertyTuple {
        final String name;
        String[] values;
        boolean regex;
        
        PropertyTuple(String name, String[] values, boolean regex) {
            this.name = name;
            this.values = values;
            this.regex = regex;
        }
    };

    private PropertyTuple includeCreateThread = new PropertyTuple("includeCreateThread", null, false);
    private PropertyTuple excludeCreateThread = new PropertyTuple("excludeCreateThread", null, false);
    private PropertyTuple includeWriteFile = new PropertyTuple("includeWriteFile", null, false);
    private PropertyTuple excludeWriteFile = new PropertyTuple("excludeWriteFile", null, false);

    private int logLevel = 0;
    private String traceElementSeparator = ";; ";


    /**
     *  read, parse and interpret property
     *    - nonexisting property is parsed as null
     *    - empty property "" is parsed as {""} for include and as {} for exclude
     *    - semicolon property ";" is parsed as {}
     * 
     *  for include properties
     *    - null means no match
     *    - {} is interpreted as {""} and means match. 
     *         It is done to facilitate the code in dumpIfMatch for(String ire : include) {...}
     *         Otherwise a separate 'if' would be necessary to treat a case when include is empty
     * 
     *  for exclude properties
     *    - null or {} means no exclusion
     *         null is interpreted as {} to facilitate the code in dumpIfMatch for(String ere : exclude)
     */
    private void readProperty(PropertyTuple tuple, boolean nullAsEmpty) {
        String prop = System.getProperty("StackDumpSM." + tuple.name);
        if(prop != null) {
            if(prop.length() > 0 && prop.charAt(0) == '{' && prop.charAt(prop.length()-1) == '}') {
                prop = prop.substring(1, prop.length() - 1);
                tuple.regex = true;
            }
            tuple.values = prop.split(";");
        }
        if(nullAsEmpty && (prop == null || prop.length() == 0)) {
            tuple.values = EMPTY_ARRAY;  // for exclude property parse empty as {}
        }
        if(!nullAsEmpty && (prop != null && prop.length() == 0)) { 
            tuple.values = EMPTY_ELEM_ARRAY;  // for include property 
        }
        // log interpretation result
        if(logLevel > 0) {
            System.out.print("----------INFO StackDumpSecurityManager ctor. " + tuple.name + " regex=" + tuple.regex + " value:");
            if(tuple.values != null) {
                for(String r : tuple.values) { 
                    System.out.print(r + ";"); 
                } 
                System.out.println("");
            }
            else {
                System.out.println("null");
            }
        }
    }


    public StackDumpSecurityManager() {
        super();
        String log = System.getProperty("StackDumpSM.logLevel");
        if(log != null && log.trim().length() > 0) {
            logLevel = Integer.valueOf(log);
            if(logLevel > 0) { System.out.println("----------INFO StackDumpSecurityManager init. logLevel=" + logLevel); }
        }
        String sep = System.getProperty("StackDumpSM.traceElementSeparator");
        if(sep != null && sep.trim().length() > 0) {
            traceElementSeparator = sep;
            if(logLevel > 0) { System.out.println("----------INFO StackDumpSecurityManager init. traceElementSeparator=" + traceElementSeparator); }
        }
        readProperty(includeCreateThread, false);
        readProperty(excludeCreateThread, true);
        readProperty(includeWriteFile, false);
        readProperty(excludeWriteFile, true);
    }


    private void dumpIfMatch(PropertyTuple inc, PropertyTuple exc, String param, String prefix) {

        if(inc.values == null) {
            if(logLevel > 1) {
                System.out.println("----------DEBUG StackDumpSecurityManager.dumpIfMatch  " + prefix + " include is null. No dump will be performed.");
            }
            return;
        }

        if(param.length() > 0) {
            // test if the parameter is excluded, so we don't need to do other tests
            for(String ere : exc.values) {
                if(logLevel > 1) {
                    System.out.println("----------DEBUG StackDumpSecurityManager.dumpIfMatch " + prefix + " check exclude first'" + 
                                       param + "' matches '" + ere + "'" + param.matches(ere));
                }
                if(exc.regex && param.matches(ere) || !exc.regex && param.indexOf(ere) >= 0) {
                    return;
                }
            }
        }

        Thread t = Thread.currentThread();
        StackTraceElement[] listSTE = t.getStackTrace();

        boolean matches = false;
        StringBuilder dump = new StringBuilder("--------StackDumpSecurityManager " + prefix + " ------{{");
        dump.append(param).append(traceElementSeparator);
        for(String ire : inc.values) {
            // check if parameter matches
            if(inc.regex && param.matches(ire) || !inc.regex && param.indexOf(ire) >= 0) {
                matches = true;
            }
            for(StackTraceElement ste : listSTE) {
                StringBuilder elem = new StringBuilder(ste.getClassName()).append('#').append(ste.getMethodName()).
                    append('(').append(ste.getFileName()).append(':').append(ste.getLineNumber()).append(")").append(traceElementSeparator);
                dump.append(elem);

                if(logLevel > 1) {
                    System.out.println("----------DEBUG StackDumpSecurityManager.dumpIfMatch  " + prefix + " check include'" 
                                       + elem + "' matches '" + ire + "' result " + (new String(elem).matches(ire)));
                }
                // check if stack elem matches
                if(matches || inc.regex && new String(elem).matches(ire) || !inc.regex && new String(elem).indexOf(ire) >= 0) {
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
            for(String ere : exc.values) {
                if(logLevel > 1) {
                    System.out.println("----------DEBUG StackDumpSecurityManager.dumpIfMatch  " + prefix + " check exclude '" + 
                                       sdump + "' matches '" + ere + "' result " + sdump.matches(ere));
                }
                if(exc.regex && sdump.matches(ere) || !exc.regex && sdump.indexOf(ere) >= 0) {
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


