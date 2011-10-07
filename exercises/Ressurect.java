import java.lang.ref.ReferenceQueue;
import java.lang.ref.PhantomReference;

// useful links 
// http://blog.code-cop.org/2006/01/object-life-time-monitoring.html
// http://www.kdgregory.com/index.php?page=java.refobj
// http://kenai.com/projects/dvm/sources/mercurial/show/src?rev=16


public class Ressurect {

    static class StrongRef<T> {
        T referent;
    }

    private Long[] buf = new Long[10000000];

    private StrongRef<Ressurect> reference;

    public Ressurect (StrongRef<Ressurect> ref) {
        reference = ref;
    }

    public void sayHello() {
        System.out.println("Hello!");
    }

    public void sayGoodbye() {
        System.out.println("Goodbye!");
    }

    protected void finalize(){
        System.out.println("finalize()");
        reference.referent = this; 
    }

    private static void printMemoryUsage() {
        Runtime rt = Runtime.getRuntime();
        System.out.println("Memory used : " + (rt.totalMemory() - rt.freeMemory())/1024);
    }

    public static void main(String[] args) {
        try {
            StrongRef<Ressurect> ref = new StrongRef<Ressurect>();
            ref.referent = new Ressurect(ref);
            
            ReferenceQueue<Ressurect> refQueue = new ReferenceQueue<Ressurect>();
            PhantomReference<Ressurect> phref = new PhantomReference<Ressurect>(ref.referent, refQueue);

            ref.referent.sayGoodbye();
            ref.referent = null;
            // after gc() object can be revived in finalize()
            // so it will be marked for removal but not removed
            System.gc();
            printMemoryUsage();
            try { Thread.currentThread().sleep(1000); } catch (InterruptedException ex) { }
            ref.referent.sayHello();
            System.out.println("Ref queue poll: " + refQueue.poll());

            System.out.println("Let's try the second time");
            ref.referent = null;
            // during the second gc() it will be finally removed
            // JavaDoc: The finalize method is never invoked more than once by a Java virtual machine for any given object
            System.gc();
            printMemoryUsage();
            System.out.println("Ref queue poll:" + refQueue.poll());
            try { Thread.currentThread().sleep(1000); } catch (InterruptedException ex) { }
            ref.referent.sayHello(); // NullPointerException
        }
        catch (Exception ex) {
            System.out.println(ex);
        }
    }


}