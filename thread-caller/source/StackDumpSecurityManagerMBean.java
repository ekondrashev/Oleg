public interface StackDumpSecurityManagerMBean {

    String getIncludeAccept();
    void setIncludeAccept(String include);
    String getExcludeAccept();
    void setExcludeAccept(String exclude);

    String getIncludeConnect();
    void setIncludeConnect(String include);
    String getExcludeConnect();
    void setExcludeConnect(String exclude);

    String getIncludeListen();
    void setIncludeListen(String include);
    String getExcludeListen();
    void setExcludeListen(String exclude);

    String getIncludeMulticast();
    void setIncludeMulticast(String include);
    String getExcludeMulticast();
    void setExcludeMulticast(String exclude);

    String getIncludeSetFactory();
    void setIncludeSetFactory(String include);
    String getExcludeSetFactory();
    void setExcludeSetFactory(String exclude);

    String getIncludeCreateThread();
    void setIncludeCreateThread(String include);
    String getExcludeCreateThread();
    void setExcludeCreateThread(String exclude);

    String getIncludeExec();
    void setIncludeExec(String include);
    String getExcludeExec();
    void setExcludeExec(String exclude);

    String getIncludeLink();
    void setIncludeLink(String include);
    String getExcludeLink();
    void setExcludeLink(String exclude);

    String getIncludeWriteFile();
    void setIncludeWriteFile(String include);
    String getExcludeWriteFile();
    void setExcludeWriteFile(String exclude);

    String getIncludeDeleteFile();
    void setIncludeDeleteFile(String include);
    String getExcludeDeleteFile();
    void setExcludeDeleteFile(String exclude);

}

