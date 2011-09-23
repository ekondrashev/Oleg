package op.test;

import java.lang.reflect.*;
import java.io.*;
// import org.apache.log4j.*;

public class Test {

    private String name = "";
    private int param = 10;
    private Test obj;
    private Integer status = new Integer(8);
    // Logger logger = Logger.getRootLogger();

    public Test(String n) {
        name = n;
    }

    public void println(Object x) {}

    public String toString() {
        return "my test object " + name + " param=" + param + " obj=" + ((obj == null)? "null" : obj.name);
    }

    void modify_param(int p) {
        System.out.println("calling modify_param ");
        param = p;
        System.out.println("param value " + param);
    }

    void modify_param_reflect(int p) {
        System.out.println("calling modify_param_reflect");
        try {
            Field field = Test.class.getDeclaredField("param");
            field.set(this, new Integer(p));
            System.out.println("param value " + param);
        } catch(Exception e)  {
            System.out.println(" errro " + e);
        }
    }

    void modify_obj() {
        System.out.println("calling modify_obj");
        obj = null;
        System.out.println("object value " + obj);
    }


    void modify_obj_reflect() {
        System.out.println("calling modify_obj_reflect");
        try {
            Field field = Test.class.getDeclaredField("obj");
            field.set(this, this);
            System.out.println("object value " + obj);
        } catch(Exception e)  {
            System.out.println(" errro " + e);
        }
    }


    void modify_status(int s) {
        System.out.println("calling modify_status");
        status = new Integer(s);
        System.out.println("new status " + status);
    }


    public static void main(String[] args) {
        {
            Test test1 = new Test("asdf");
            test1.modify_param(111);
            test1.modify_param_reflect(222);
            test1.modify_obj();
            test1.modify_obj_reflect();
            test1.modify_status(88);
        }
        {
            Test test2 = new Test("qwert");
            test2.modify_param(111);
            test2.modify_param_reflect(222);
            test2.modify_obj();
            test2.modify_obj_reflect();
            test2.modify_status(88);
        }
    }

}