public class ThreadCaller {


    public ThreadCaller() {

    }

    void a(String from) {
        System.out.println("Calling a from " + from);
        new Thread(new Runnable() {
                public void run() {
                    System.out.println("Running thread in method a");
                    try { Thread.sleep(1000); } catch(Exception ex) {}
                }
            }, "A").start();
    }


    void b(String from) {
        System.out.println("Calling b from " + from);
        new Thread(new Runnable() {
                public void run() {
                    System.out.println("Running thread in method b");
                    try { Thread.sleep(1000); } catch(Exception ex) {}
                }
            }, "B").start();
        a(from + " b");
    }


    void c(String from) {
        System.out.println("Calling c from " + from);
        new Thread(new Runnable() {
                public void run() {
                    System.out.println("Running thread in method c");
                    try { Thread.sleep(1000); } catch(Exception ex) {}
                }
            }, "C").start();
        a(from + " c");
        b(from + " c");
    }


    public static void main(String args []) {
        // System.setSecurityManager(new MySecurityManager());
        ThreadCaller tc = new ThreadCaller();
        tc.a("main");
        tc.b("main");
        tc.c("main");
    }

}