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
    private PropertyTuple includeReadFile = new PropertyTuple("includeReadFile", null, false);
    private PropertyTuple excludeReadFile = new PropertyTuple("excludeReadFile", null, false);
    private PropertyTuple includeWriteFile = new PropertyTuple("includeWriteFile", null, false);
    private PropertyTuple excludeWriteFile = new PropertyTuple("excludeWriteFile", null, false);

    private int logLevel = 0;
    private String traceElementSeparator = ";; ";


    public StackDumpSecurityManager() {
        super();
        String ll = System.getProperty("StackDumpSM.logLevel");
        if(ll != null && ll.trim().length() > 0) {
            logLevel = Integer.valueOf(ll);
            log(1, " ctor, logLevel=%d", logLevel);
        }
        String sep = System.getProperty("StackDumpSM.traceElementSeparator");
        if(sep != null && sep.trim().length() > 0) {
            traceElementSeparator = sep;
            log(1, " ctor, traceElementSeparator=%s", traceElementSeparator);
        }
        readProperty(includeCreateThread, false);
        readProperty(excludeCreateThread, true);
        readProperty(includeReadFile, false);
        readProperty(excludeReadFile, true);
        readProperty(includeWriteFile, false);
        readProperty(excludeWriteFile, true);
    }



    public ThreadGroup getThreadGroup() {

        dumpIfMatch(includeCreateThread, excludeCreateThread, "", "getThreadGroup");
        return super.getThreadGroup();
    }


    public void checkWrite(String file) {
        
        dumpIfMatch(includeWriteFile, excludeWriteFile, file.replace('\\', '/'), "checkWrite");
        super.checkWrite(file);
    }
  
    public void checkRead(String file){

        dumpIfMatch(includeReadFile, excludeReadFile, file.replace('\\', '/'), "checkRead");
        super.checkWrite(file);
    }


    //----------------------------------------------------------------------

    /**
     * return true if param is excluded
     */
    private boolean matchesExcludeParam(PropertyTuple exc, String param, String prefix) {
        if(param.length() > 0) {
            for(Object ere : exc.values) {
                Pattern pe = exc.regex? (Pattern)ere : null;
                String se = (!exc.regex)? (String)ere : null;
                if(pe != null && pe.matcher(param).matches() || se != null && param.indexOf(se) >= 0) {
                    log(2, "checkExcludeParam %s %s matches", prefix, param);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesExclude(PropertyTuple exc, String dump, String prefix) {
        for(Object ere : exc.values) {
            Pattern pe = exc.regex? (Pattern)ere : null;
            String se = (!exc.regex)? (String)ere : null;
            if(pe != null && pe.matcher(dump).matches() || se != null && dump.indexOf(se) >= 0) {
                log(2, "dumpIfMatch %s check exclude matches %s", prefix, dump);
                return true;
            }
        }
        return false;
    }

    //----------------------------------------------------------------------

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

    //----------------------------------------------------------------------
    
    private void dumpIfMatch(PropertyTuple inc, PropertyTuple exc, String param, String prefix) {

        try {

        if(inc.values == null) {
            log(2, "dumpIfMatch %s include is null. No match", prefix);
            return;
        }

        // test if the parameter is excluded, so we don't need to do other tests
        if(matchesExcludeParam(exc, param, prefix)) {
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
                if(!matches) {
                    if(pi != null && pi.matcher(new String(elem)).matches() || si != null && new String(elem).indexOf(si) >= 0) {
                        matches = true;
                        log(2, "dumpIfMatch %s check include matches %s", prefix, elem);
                    }
                }
            }
            if(matches) {
                dump.append(dumpFooter());
                break;
            }
        }
        if(matches && !matchesExclude(exc, new String(dump), prefix)) {
            System.out.println("\n\n" + dump + "\n\n");
        }
        } catch(Exception ex) {
            System.err.println(ex);
        }
    }

    //----------------------------------------------------------------------

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

    //----------------------------------------------------------------------

    private static final String PREFIX = "----------";
    private static final String LEVEL_INFO = "INFO";
    private static final String LEVEL_DEBUG = "DEBUG";

    private void log(int level, String format, Object ... args) {
        if(level > logLevel) {
            return;
        }

        String prefix = String.format("%s%s StackDumpSecurityManager ", PREFIX, ((level == 1)? LEVEL_INFO : LEVEL_DEBUG));
        String body = String.format(format, args);
        System.out.println(prefix+body);
    }

    private void logTuple(PropertyTuple tuple) {
        if(logLevel > 0) {
            String out = String.format("%s%s StackDumpSecurityManager logTuple %s regex=%b value=", PREFIX, LEVEL_INFO, tuple.name, tuple.regex);
            System.out.print(out);
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


};


