import java.util.regex.*;

public class StackDumpSecurityManager extends SecurityManager  {

    private static final String[] EMPTY_ARRAY = new String[0];
    private static final String[] EMPTY_ELEM_ARRAY = new String[]{""};

    private class PropertyTuple {
        final String name;
        Object[] values;
        boolean regex;
        
        PropertyTuple(String name, Object[] values, boolean regex) {
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
     * test if system property represents a regex
     * ie {regex} string
     */
    private boolean isPropRegex(String prop) {
        return prop.length() > 0 && prop.charAt(0) == '{' && prop.charAt(prop.length()-1) == '}';
    }

    private void compileRegex(PropertyTuple tuple, String prop) {
        tuple.regex = true;

        String[] exprs = prop.split(";");
        Pattern[] patterns = new Pattern[exprs.length];
        int i = 0;
        for(String e : exprs) {
            try {
                patterns[i++] = Pattern.compile(e);
            }
            catch(PatternSyntaxException pse) {
                System.err.println("Incorrect regex:" + pse);
            }
        }
        tuple.values = patterns;
    }

    private void logTuple(PropertyTuple tuple) {
        if(logLevel > 0) {
            System.out.print("----------INFO StackDumpSecurityManager ctor. " + tuple.name + " regex=" + tuple.regex + " value:");
            if(tuple.values != null) {
                for(Object r : tuple.values) { 
                    System.out.print(r + ";"); 
                } 
                System.out.println("");
            }
            else {
                System.out.println("null");
            }
        }
    }

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
            if(isPropRegex(prop)) {
                compileRegex(tuple, prop.substring(1, prop.length() - 1));
            }
            else {
                tuple.values = prop.split(";");
            }
        }
        if(!tuple.regex) {
            if(nullAsEmpty && (prop == null || prop.length() == 0)) {
                tuple.values = EMPTY_ARRAY;  // for exclude property parse empty as {}
            }
            if(!nullAsEmpty && (prop != null && prop.length() == 0)) { 
                tuple.values = EMPTY_ELEM_ARRAY;  // for include property 
            }
        }
        logTuple(tuple);  // log interpretation result
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


    /**
     * return true if param is excluded
     */
    private boolean checkExcludeParam(PropertyTuple exc, String param, String prefix) {
        if(param.length() > 0) {
            for(Object ere : exc.values) {
                Pattern pe = exc.regex? (Pattern)ere : null;
                String se = (!exc.regex)? (String)ere : null;
                if(pe != null && pe.matcher(param).matches() || se != null && param.indexOf(se) >= 0) {
                    if(logLevel > 1) {
                        System.out.println("----------DEBUG StackDumpSecurityManager.checkExcludeParam " + prefix + " " + param + "matches");
                    }
                    return true;
                }
            }
        }
        return false;
    }


    private StringBuilder dumpHeader(String prefix) {
        return new StringBuilder("--------StackDumpSM " + prefix + " ------{{");
    }

    private StringBuilder dumpStackTraceElem(StackTraceElement ste) {
        return new StringBuilder(ste.getClassName()).append('#').append(ste.getMethodName()).
                    append('(').append(ste.getFileName()).append(':').append(ste.getLineNumber()).append(")").append(traceElementSeparator);
    }

    private StringBuilder dumpFooter() {
        return new StringBuilder("--------StackDumpSecurityManager------}}");
    }


    private void dumpIfMatch(PropertyTuple inc, PropertyTuple exc, String param, String prefix) {

        if(inc.values == null) {
            if(logLevel > 1) {
                System.out.println("----------DEBUG StackDump.dumpIfMatch  " + prefix + " include is null. No dump will be performed.");
            }
            return;
        }

        // test if the parameter is excluded, so we don't need to do other tests
        if(checkExcludeParam(exc, param, prefix)) {
            return;
        }

        Thread t = Thread.currentThread();
        StackTraceElement[] listSTE = t.getStackTrace();

        boolean matches = false;
        StringBuilder dump = dumpHeader(prefix);
        dump.append(param).append(traceElementSeparator);
        for(Object ire : inc.values) {
            Pattern pi = inc.regex? (Pattern)ire : null;
            String si = (!inc.regex)? (String)ire : null;
            // check if parameter matches
            if(pi != null && pi.matcher(param).matches() || si != null && param.indexOf(si) >= 0) {
                matches = true;
            }
            for(StackTraceElement ste : listSTE) {
                StringBuilder elem = dumpStackTraceElem(ste);
                dump.append(elem);

                // check if stack elem matches
                if(matches || pi != null && pi.matcher(new String(elem)).matches() || si != null && new String(elem).indexOf(si) >= 0) {
                    matches = true;
                }
                if(logLevel > 1) {
                    System.out.println("----------DEBUG StackDumpSecurityManager.dumpIfMatch  " + prefix + " check include" + matches);
                }
            }
            if(matches) {
                dump.append(dumpFooter());
                break;
            }
        }
        if(matches) {
            String sdump = new String(dump);
            for(Object ere : exc.values) {
                Pattern pe = exc.regex? (Pattern)ere : null;
                String se = (!exc.regex)? (String)ere : null;
                if(pe != null && pe.matcher(sdump).matches() || se != null && sdump.indexOf(se) >= 0) {
                    matches = false;
                    if(logLevel > 1) {
                        System.out.println("----------DEBUG StackDumpSecurityManager.dumpIfMatch  " + prefix + " check exclude " + (!matches));
                    }
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


