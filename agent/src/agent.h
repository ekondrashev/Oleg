#ifndef AGENT_H_11035784
#define AGENT_H_11035784

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <stdarg.h>

#include "gcc.h"

#include <sys/types.h>
#include "jni.h"
#include "jvmti.h"


#define MAX_TOKEN_LEN 1024
#define MAX_TO_STRING_LEN 10240
#define MAX_LOG_LEN (1024 + MAX_TO_STRING_LEN*2)


#define TRACE_IMPORTANT  1
#define TRACE_INFO       2
#define TRACE_DEBUG      3
#define TRACE_FINEST     4


typedef struct {
  char        klass_name[MAX_TOKEN_LEN];
  jclass      klass;
  char        field_name[MAX_TOKEN_LEN];          // watch modifications of this field of class klass_name
  char        field_sig[MAX_TOKEN_LEN];
  jfieldID    field;        

  char        field_value[MAX_TOKEN_LEN];         // notify when new value is (not) equal to this value
  int         field_contains;                     // check (is not) equal

  char        object_value[MAX_TOKEN_LEN];        // notify when object (does not) contains
  int         object_contains;                    // check (does not) contain
} WatchField;


typedef struct {
  char        klass_name[MAX_TOKEN_LEN];
  char        method_name[MAX_TOKEN_LEN];
  char        method_sig[MAX_TOKEN_LEN];
  int         is_static;
  jmethodID   method;
} MethodCall;



typedef struct {
  jvmtiEnv      *jvmti;

  char           prefix[MAX_TOKEN_LEN];

  int            verbose;

  int            enable_method_call;
  int            enable_agent_class_loader;

  FILE*          out_file;

  jboolean       vm_is_dead;
  jboolean       vm_is_started;

  jrawMonitorID  lock;

  jclass         field_class;                   // java.lang.reflect.Field class
  jmethodID      field_getName_method;          // String  getName() method from java.lang.reflect.Field
  char           field_name_buffer[256];        // in this buffer a field name will be stored

  jmethodID      loadClass_method;
  jobject        agent_class_loader;


  jclass         thread_class;
  jmethodID      thread_dump_method;

  /* jmethodID      to_string; */
  jclass         string_class;

  jmethodID      valueOfZ;
  /* jmethodID      valueOfB; */
  jmethodID      valueOfC;
  /* jmethodID      valueOfS; */
  jmethodID      valueOfI;
  jmethodID      valueOfJ;
  jmethodID      valueOfF;
  jmethodID      valueOfD;
  jmethodID      valueOfL;

} GlobalAgentData;


void parse_agent_options(char *options);

void enter_critical_section(jvmtiEnv *jvmti);
void exit_critical_section(jvmtiEnv *jvmti);

void get_thread_name(jvmtiEnv *jvmti, jthread thread, char *tname, int maxlen);
/* char* toString(JNIEnv* jni, jobject obj, char* buf, int buf_size); */
char* valueOfObj(jvmtiEnv *jvmti, JNIEnv* jni, jobject obj, char* buf, int buf_size);
char* valueOf(jvmtiEnv *jvmti, JNIEnv* jni, jvalue val, char sig, char* buf, int buf_size);
char* get_field_name(JNIEnv* jni, jobject field);

void log_method_call(jvmtiEnv *jvmti, jthread thread, int idx);
void log_field_watch(jvmtiEnv *jvmti, jthread thread, int idx, char* obj, char *new_val);
void dump_stack(jvmtiEnv *jvmti, jthread thread);

jclass find_class(JNIEnv *jni, const char *klass);
jmethodID get_method_id(JNIEnv *jni, jclass klass, const char* method, const char* sig);
jmethodID get_static_method_id(JNIEnv *jni, jclass klass, const char* method, const char* sig);
jfieldID get_field_id(JNIEnv *jni, jclass klass, const char *field, const char *sig);
jfieldID get_static_field_id(JNIEnv *jni, jclass klass, const char *field, const char *sig);
jmethodID get_value_of_id(JNIEnv *jni, char sig);

void f_trace(JNIEnv *jni, int level, const char * format, ...);
void f_log(JNIEnv *jni, const char * format, ...);
void log_current_time(FILE* f);

char* get_jni_exception_descr(JNIEnv *jni);

#endif
