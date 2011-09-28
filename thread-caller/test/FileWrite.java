import java.io.*;

class FileWrite {

    Writer out1, out2;

    public FileWrite() throws Exception {
        out1 = new BufferedWriter(new FileWriter("output1.txt"));
        out2 = new BufferedWriter(new FileWriter("output2.txt"));
    }

    public void a() throws Exception {
        out1.write("method a");
        out2.write("method a");
    }

    public void b() throws Exception {
        a();
        out1.write("method b");
        out2.write("method b");
    }

    public void c() throws Exception {
        a();
        b();
        out1.write("method c");
        out2.write("method c");
    }


    public void finish() throws Exception {
        out1.close();
        out2.close();
    }

    public static void main(String args[]) {
        try {

        FileWrite fw = new FileWrite();
        fw.a();
        fw.b();
        fw.c();
        fw.finish();

        } catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
    }
}