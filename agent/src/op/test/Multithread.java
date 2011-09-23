package op.test;

import java.lang.reflect.*;
import java.io.*;
// import org.apache.log4j.*;

public class Multithread {

    long param = 10;
    Multithread obj;
    // Logger logger = Logger.getRootLogger();

    public void println(Object x) {}

    public String toString() {
        try { Thread.sleep(10); } catch(Exception e) {}
        return "my Multithread object " + param + " obj value is " + ((obj == null)? "null" : Long.toString(obj.param));
    }

    int f111(int p) {
        // param = p;
        System.out.println("calling f111 ");
        return p;
    }

    int g222(int p) {
        System.out.println("calling g222");
        try {
            Field field = Multithread.class.getDeclaredField("obj");
            field.set(this, this);
            try { Thread.sleep(10); } catch(Exception e) {}
            obj = null;
            try { Thread.sleep(10); } catch(Exception e) {}
            field.set(this, null);
            try { Thread.sleep(10); } catch(Exception e) {}
            obj = this;
            // field = Multithread.class.getDeclaredField("param");
            // field.set(this, new Integer(p));
        } catch(Exception e)  {
            System.out.println(" errro " + e);
        }

        return f111(p);
    }


    public static void main(String[] args) {
        System.out.println("main begin");
        for(int i = 0; i < 100; ++i) {
            Thread t = new Thread((new Runnable() {
                    public void run() {
                        Multithread mt = new Multithread();
                        try { mt.param = new Long(Thread.currentThread().getName()).longValue(); } catch(Exception e)  {}
                        for(int j = 0; j < 10000; ++j) {
                            try { Thread.sleep(10); } catch(Exception e) {}
                            mt.f111(2);
                            mt.g222(34);
                        }
                        
                    }
                }), Long.toString(i));
            t.start();
            // try { t.join(); } catch(Exception e) { }
        }
        try { Thread.sleep(100000); } catch(Exception e) {}
        System.out.println("main end");
    }

}