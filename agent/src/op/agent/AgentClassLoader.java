package op.agent;

import java.net.*;

import java.io.*;
import java.lang.reflect.*;
import javax.management.*;


import org.objectweb.asm.*;


/**
 *  AgentClassLoader must extend ClassLoader because it uses protected method defineClass() of ClassLoader
 */
public class AgentClassLoader {



    private ClassLoader mClassLoaderDelegate;

 
    public void setClassLoaderDelegate(ClassLoader cl) {
        mClassLoaderDelegate = cl;
    }

    private Class loadClassImpl(String name) throws ClassNotFoundException {

        if (name.startsWith("java.") || name.startsWith("op.agent.")) {
            return mClassLoaderDelegate.loadClass(name);
        }
        String cname = (name.charAt(name.length() - 1) == ';')? name.substring(1, name.length() - 1) : name;
        cname = cname.replace('.', '/') + ".class";
        InputStream is = mClassLoaderDelegate.getResourceAsStream(cname);
        System.out.println("getResourceAsStream returned " + is + " for the class " + cname);
        if(is == null) {
            return null;
        }

        byte[] b;
        try {
            ClassWriter cw = new ClassWriter(0);
            ClassReader cr = new ClassReader(is);
            ClassVisitor cv = (name.equals("Lop/agent/AgentClassLoader;") || name.equals("op.agent.FieldSetLogger"))? new ClassAdapter(cw) : new ReplaceFieldClassAdapter(cw);
            cr.accept(cv, 0);
            b = cw.toByteArray();
        } catch (Exception e) {
            System.err.println("exception " + e.toString() + " cause " + e.getCause() + " class " + cname);
            throw new ClassNotFoundException(name, e);
        }

        try {
            System.err.println("!!writing " + name + " adapted!!");
            FileOutputStream fos = new FileOutputStream("" + System.currentTimeMillis() + ".class");
            fos.write(b);
            fos.close();
        } catch (Exception e) {
        }
        
        try {
            Method dcm = Class.forName("java.lang.ClassLoader").getDeclaredMethod("defineClass", new Class[] { String.class, byte[].class, int.class, int.class });
            dcm.setAccessible(true);
            // Method dcm = mClassLoaderDelegate.getClass().getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            return (Class)dcm.invoke(mClassLoaderDelegate, new Object[] {name, b, 0, b.length});
        }
        catch(Exception ex) {
            System.err.println("invoking defineClass exception " + ex);
        }
        return null;
        // return mClassLoaderDelegate.defineClass(name, b, 0, b.length);
    }

    public Class loadClass(String name) throws ClassNotFoundException {
        return loadClassImpl(name);
    }


    public static void main(final String args[]) throws Exception {
        AgentClassLoader loader = new AgentClassLoader();
        loader.setClassLoaderDelegate(ClassLoader.getSystemClassLoader());
        Class c = loader.loadClass(args[0]);
        if(c == null) {
            return;
        }
        Method m = c.getMethod("main", new Class[] { String[].class });
        String[] applicationArgs = new String[args.length - 1];
        System.arraycopy(args, 1, applicationArgs, 0, applicationArgs.length);
        m.invoke(null, new Object[] { applicationArgs });
    }



    /**
     *  ReplaceFieldClassAdapter calls FieldCodeAdapter for each method
     */
    class ReplaceFieldClassAdapter extends ClassAdapter implements Opcodes {

        public ReplaceFieldClassAdapter(final ClassVisitor cv) {
            super(cv);
        }

        public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions)  {
            System.out.println("Visit method  " + name + " desc " + desc + " sig " + signature);
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return mv == null ? null : new FieldCodeAdapter(mv);
        }
    };


    /**
     * FieldCodeAdapter replaces Field.set with FieldSetLogger.set
     */
    class FieldCodeAdapter extends MethodAdapter implements Opcodes {

        MethodVisitor mvOwner;

        public FieldCodeAdapter(final MethodVisitor mv) {
            super(mv);
            mvOwner = mv;
        }

        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if(owner.equals("java/lang/reflect/Field") && name.equals("set")) {
                System.out.println("    opcode  " + opcode + " owner " + owner + " name " + name + " desc "  + desc);
                mvOwner.visitMethodInsn(INVOKESTATIC, "op/agent/FieldSetLogger", "set", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
                mvOwner.visitEnd();
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc);
        }
    };


};






