package op.agent;

import java.lang.reflect.*;



public class FieldSetLogger {

    static public void set(Object thisObj, Object obj, Object value)  throws IllegalArgumentException, IllegalAccessException{
        try {
            System.out.println("---------------- Field.set called. Object " + String.valueOf(obj) + " value " + String.valueOf(value));
            System.out.flush();
        }
        catch(Exception ex) {
            System.err.println("Exception while calling String.valueOf " + ex);
        }
        StackTraceElement[]	els = Thread.currentThread().getStackTrace();
        for(int i = 2; i < els.length; ++i) { // els[0] is java.lang.Thread.getStackTrace, els[1] is op.agent.FieldSetLogger.set
            System.out.println("-----------+ " + els[i]);
        }
        System.out.flush();
        
        try {
            Method mSet = Field.class.getDeclaredMethod("set", Object.class, Object.class);
            ((Field)thisObj).setAccessible(true);
            mSet.invoke(thisObj, obj, value);
        }
        catch(InvocationTargetException ex) {
            System.err.println(ex.toString() + ex.getCause().toString() + ((InvocationTargetException)ex).getTargetException().toString());
        }
        catch(NoSuchMethodException ex) {
            System.err.println(ex);
        }
    }
};





