/* 
   This agent allows to dump stack during runtime if a condition has been met. 
   Currently the follwing conditions are supported
        - field value modifications
        - method calls

  TODO:
   - implement file command polling to allow enable/disable agent tracing and to modify options
   - finish agent class loader

*/


#include <time.h>
#include "gcc.h"
#include "util.h"
#include "agent.h"

static GlobalAgentData gdata;


static WatchField watch_field[10];
static int watch_field_cnt = 0;    // Total number of watched field
static int watch_field_init = 0;   // Already initialized watched field. If all necessary watched fields are initialized we can bypass cbClassPrepare


static MethodCall method_call[10];
static int method_call_cnt = 0;
static int method_call_init = 0;

// Logically it belongs to GlobalAgentData but I put it here to avoid performance penalty
static jmethodID field_set_method;              // void	set(Object obj, Object value) method from java.lang.reflect.Field



static void JNICALL cbVMStart(jvmtiEnv *jvmti, JNIEnv *jni)
{
  enter_critical_section(jvmti); {
    gdata.vm_is_started = JNI_TRUE;
  } exit_critical_section(jvmti);
}


// callback on init java VM. 
// Java system classes are already loaded, let's get pointers to some useful java methods
static void JNICALL cbVMInit(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread)
{
  enter_critical_section(jvmti); {
    f_trace(jni, TRACE_DEBUG, "initializing \n");

    gdata.field_class = find_class(jni, "Ljava/lang/reflect/Field;");
    field_set_method = get_method_id(jni, gdata.field_class, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V");
    gdata.field_getName_method = get_method_id(jni, gdata.field_class, "getName", "()Ljava/lang/String;");

    gdata.thread_class = find_class(jni, "Ljava/lang/Thread;");
    gdata.thread_dump_method = get_static_method_id(jni, gdata.thread_class, "dumpStack", "()V");
    
    gdata.string_class = find_class(jni, "Ljava/lang/String;");
    gdata.valueOfZ = get_value_of_id(jni, 'Z');
	gdata.valueOfC = get_value_of_id(jni, 'C');
	gdata.valueOfI = get_value_of_id(jni, 'I');
	gdata.valueOfJ = get_value_of_id(jni, 'J');
	gdata.valueOfF = get_value_of_id(jni, 'F');
	gdata.valueOfD = get_value_of_id(jni, 'D');
	gdata.valueOfL = get_static_method_id(jni, gdata.string_class, "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;");

    /* (*jvmti)->AddToBootstrapClassLoaderSearch(jvmti, "d:/Work/jvmti/agent/build"); */

    f_trace(jni, TRACE_DEBUG, "initializing done \n\n");
  } exit_critical_section(jvmti);
}


// callback on class prepare: class is already loaded
// let's initialize field
static void JNICALL cbClassPrepare(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jclass klass) 
{
  if(gdata.vm_is_dead) {
    return;
  }
  if((watch_field_init < watch_field_cnt) || (method_call_init < method_call_cnt)) {
    enter_critical_section(jvmti); {

      char *class_name;
      (*jvmti)->GetClassSignature(jvmti, klass, &class_name, NULL);
      f_trace(jni, TRACE_FINEST, "class prepare %s\n", class_name);

      if(gdata.enable_agent_class_loader && !gdata.loadClass_method){
        jclass acl = (*jni)->FindClass(jni, "op/agent/AgentClassLoader");
        (*jni)->ExceptionClear(jni);
        if(acl) {
          jmethodID get_instance = get_static_method_id(jni, acl, "getInstance", "()Lop/agent/AgentClassLoader;");
          f_trace(jni, TRACE_DEBUG, "get_instance %d\n", get_instance);
          gdata.agent_class_loader = (*jni)->CallStaticObjectMethod(jni, acl, get_instance);
          f_trace(jni, TRACE_DEBUG, "gdata.agent_class_loader %d\n", gdata.agent_class_loader);
          gdata.agent_class_loader = (*jni)->NewGlobalRef(jni, gdata.agent_class_loader);
          gdata.loadClass_method = get_method_id(jni, acl, "loadClass", "(Ljava/lang/String;Z)Ljava/lang/Class;");
          f_trace(jni, TRACE_DEBUG, "gdata.loadClass_method %d\n", gdata.loadClass_method);
          (*jni)->ExceptionClear(jni);
        }
      }

      if(gdata.loadClass_method) {
        f_trace(jni, TRACE_DEBUG, "calling load class method \n");
        (*jni)->CallObjectMethod(jni, gdata.agent_class_loader, gdata.loadClass_method, (*jni)->NewStringUTF(jni, class_name));
        (*jni)->ExceptionClear(jni);
      }

      if(watch_field_init < watch_field_cnt) {
        int i;
        for(i = 0; i < watch_field_cnt; ++i) {
          if(!watch_field[i].klass) {
			jclass wk = (*jni)->FindClass(jni, watch_field[i].klass_name);
            (*jni)->ExceptionClear(jni);  // otherwise jvmti remains in error state if the class was not found
            if (wk && !watch_field[i].klass) { // the check watch_field[i].klass was added to avoid multiple execution of the following code, which I cannot explain
              f_trace(jni, TRACE_DEBUG, "init watch field class %s found\n", watch_field[i].klass_name);
              watch_field[i].klass = (*jni)->NewGlobalRef(jni, wk);
              watch_field[i].field = get_field_id(jni, watch_field[i].klass, watch_field[i].field_name, watch_field[i].field_sig);
              (*jvmti)->SetFieldModificationWatch(jvmti, watch_field[i].klass, watch_field[i].field);
              ++watch_field_init;
              f_log(jni, "watch set for field %s of class %s\n", watch_field[i].field_name, watch_field[i].klass_name);
              break;
            }
          }
          else {
            f_trace(jni, TRACE_DEBUG, "calling ExceptionClear\n");
            (*jni)->ExceptionClear(jni);
          }
        }
      }

      if(method_call_init < method_call_cnt) {
        int i;
        for(i = 0; i < method_call_cnt; ++i) {
          if(!method_call[i].method) {
            jclass mk = (*jni)->FindClass(jni, method_call[i].klass_name);
            (*jni)->ExceptionClear(jni); // otherwise jvmti remains in error state if the class was not found
            if (mk) {
              method_call[i].method = method_call[i].is_static ?
                get_static_method_id(jni, mk, method_call[i].method_name, method_call[i].method_sig):
                get_method_id(jni, mk, method_call[i].method_name, method_call[i].method_sig);
              ++method_call_init;
              f_log(jni, "method call initialized for method %s of class %s\n", method_call[i].method_name, method_call[i].klass_name);
              break;
            }
          }
          else {
            f_trace(jni, TRACE_DEBUG, "calling ExceptionClear\n");
            (*jni)->ExceptionClear(jni);
          }
        }
      }

      deallocate(jvmti, class_name);
    } exit_critical_section(jvmti);
  }
}


// check if a new field's value (does not) equal(s) to a given configurable value
int check_field_value(char* value_buf, int idx) 
{
  int result = 0;
  if(watch_field[idx].field_value[0] == 0) {
    result = 1;
  }
  else {
    char* cmp_val = strstr(value_buf, watch_field[idx].field_value);
    result = ((cmp_val && watch_field[idx].field_contains) || (!cmp_val && !watch_field[idx].field_contains));
  }
  return result;
}



// check if an object value (does not) contain(s) a given configurable value
int check_object_value(char* object_buf, int idx) 
{
  int result = 0;
  if(watch_field[idx].object_value[0] == 0) {
    result = 1;
  }
  else {
    char* cmp_val = strstr(object_buf, watch_field[idx].object_value);
    result = ((cmp_val && watch_field[idx].object_contains) || (!cmp_val && !watch_field[idx].object_contains));
  }
  return result;
}


static void JNICALL cbEventFieldModification(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method, jlocation location, jclass field_klass, jobject object, jfieldID field, char signature_type, jvalue new_value) 
{
  static char obj_buf[MAX_TO_STRING_LEN], val_buf[MAX_TO_STRING_LEN];
  int i, do_log = 0, obj_cmp_ok = 2; // 2 is not yet initialized value
  if (gdata.vm_is_dead) {
    return;
  }

  obj_buf[0] = 0;

  // commented out the trace because it may have performance impact
  // f_trace(jni, TRACE_DEBUG, "cbEventFieldModification %c\n", signature_type); 

  enter_critical_section(jvmti); {
    for(i = 0; i < watch_field_cnt; ++i) {
      if(watch_field[i].field == field) {
        valueOf(jvmti, jni, new_value, signature_type, val_buf, MAX_TO_STRING_LEN - 1);
        do_log = check_field_value(val_buf, i);
        if(do_log) {
          if(obj_buf[0] == 0) {
            valueOfObj(jvmti, jni, object, obj_buf, MAX_TO_STRING_LEN - 1);
            if(obj_cmp_ok == 2) {
              obj_cmp_ok = check_object_value(obj_buf, i);
            }
          }
          if(obj_cmp_ok) {
            break;
          }
          else {
            do_log = 0;
          }
        }
      }
    }
    if(do_log) {
      log_field_watch(jvmti, thread, i, obj_buf, val_buf);
    }

  } exit_critical_section(jvmti);
}


void process_field_set(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread)
{
  static char obj_buf[MAX_TO_STRING_LEN], val_buf[MAX_TO_STRING_LEN];
  int do_log = 0, i, obj_cmp_ok = 2; // 2 is not yet initialized value
  jobject object, val, fld;
  jvmtiError error;

  enter_critical_section(jvmti); {
    if(watch_field_cnt > 0) {

      f_trace(jni, TRACE_DEBUG, "process_field_set GetlocalObject 1\n");
      error = (*jvmti)->GetLocalObject(jvmti, thread, 0, 1, &object);
      check_jvmti_error(jvmti, error, "GetLocalObject 1 param error");
      
      if(error == JVMTI_ERROR_NONE) {
        for(i = 0; i < watch_field_cnt; ++i) {
          f_trace(jni, TRACE_DEBUG, "process_field_set IsInstanceOf\n");
          if(watch_field[i].klass && (*jni)->IsInstanceOf(jni, object, watch_field[i].klass)) {

            f_trace(jni, TRACE_DEBUG, "process_field_set GetlocalObject 0\n");
            error = (*jvmti)->GetLocalObject(jvmti, thread, 0, 0, &fld);   
            check_jvmti_error(jvmti, error, "GetLocalObject 0 param error");
            
            if(error == JVMTI_ERROR_NONE) {
              if(strcmp(watch_field[i].field_name, get_field_name(jni, fld)) == 0) {

                f_trace(jni, TRACE_DEBUG, "process_field_set GetlocalObject 2\n");
                error = (*jvmti)->GetLocalObject(jvmti, thread, 0, 2, &val);   
                check_jvmti_error(jvmti, error, "GetLocalObject 2 param error");

                if(error == JVMTI_ERROR_NONE) {
                  valueOfObj(jvmti, jni, val, val_buf, MAX_TO_STRING_LEN - 1);
                  do_log = check_field_value(val_buf, i);
                  if(do_log) {
                    if(obj_buf[0] == 0) {
                      valueOfObj(jvmti, jni, object, obj_buf, MAX_TO_STRING_LEN - 1);
                      if(obj_cmp_ok == 2) {  
                        obj_cmp_ok = check_object_value(obj_buf, i);
                      }
                    }
                    if(obj_cmp_ok) {
                      break;
                    }
                    else {
                      do_log = 0;
                    }
                  }
                }
              }
            }
          }
        }
      }
      if(do_log) {
        log_field_watch(jvmti, thread, i, obj_buf, val_buf);
      }
    }
  } exit_critical_section(jvmti);
}



static void JNICALL cbMethodEntry(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread, jmethodID method) 
{
  /*
  if (gdata.vm_is_dead) {
    return;
  }
  */
  if(field_set_method == method) {
    process_field_set(jvmti, jni, thread);
  }
  /*
  else {
    enter_critical_section(jvmti); {
      int i;
      for(i = 0; i < method_call_cnt; ++i) {
        if(method_call[i].method == method) {
          log_method_call(jvmti, thread, i);
        }
      }
    } exit_critical_section(jvmti);
  }
  */
}


void log_method_call(jvmtiEnv *jvmti, jthread thread, int idx)
{
  f_log(0, "method call %s class name %s\n", method_call[idx].method_name, method_call[idx].klass_name);
  dump_stack(jvmti, thread);
}


static void JNICALL cbVMDeath(jvmtiEnv *jvmti, JNIEnv *jni)
{
  enter_critical_section(jvmti); {
    gdata.vm_is_dead = JNI_TRUE;
    fflush(gdata.out_file);
    fclose(gdata.out_file);
  } exit_critical_section(jvmti);
}  


/* This is the first code executed. */
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
  jvmtiEnv              *jvmti;
  jvmtiError             error;
  jint                   res;
  jvmtiCapabilities      capabilities;
  jvmtiEventCallbacks    callbacks;

  (void)memset((void*)&gdata, 0, sizeof(gdata));
   
  memset(watch_field, 0, sizeof(watch_field));
  memset(method_call, 0, sizeof(method_call));

  res = (*vm)->GetEnv(vm, (void **)&jvmti, JVMTI_VERSION_1);
  if (res != JNI_OK) {
    fatal_error("ERROR: Unable to access JVMTI Version 1 (0x%x), is your J2SE a 1.5 or newer version? JNIEnv's GetEnv() returned %d\n", JVMTI_VERSION_1, res);
  }

  gdata.jvmti = jvmti;

  parse_agent_options(options);
   
  (void)memset(&capabilities,0, sizeof(capabilities));
  //capabilities.can_generate_all_class_hook_events  = 1;
  capabilities.can_generate_field_modification_events = 1;
  //  capabilities.can_generate_field_access_events = 1;
  capabilities.can_generate_method_entry_events = 1;
  capabilities.can_access_local_variables = 1;
  error = (*jvmti)->AddCapabilities(jvmti, &capabilities);  
  check_jvmti_error(jvmti, error, "Unable to get necessary JVMTI capabilities.");
    
  (void)memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VMStart           = &cbVMStart;      
  callbacks.VMInit            = &cbVMInit;      
  callbacks.VMDeath           = &cbVMDeath;     
  callbacks.FieldModification = &cbEventFieldModification;
  callbacks.MethodEntry       = &cbMethodEntry;
  callbacks.ClassPrepare      = &cbClassPrepare;

  error = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, (jint)sizeof(callbacks));  
  check_jvmti_error(jvmti, error, "Cannot set jvmti callbacks");
   
  error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_START, NULL);  
  check_jvmti_error(jvmti, error, "Cannot set JVMTI_EVENT_VM_START");
  error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);   
  check_jvmti_error(jvmti, error, "Cannot set JVMTI_EVENT_VM_INIT");
  error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, NULL);  
  check_jvmti_error(jvmti, error, "Cannot set JVMTI_EVENT_VM_DEATH");
  error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_FIELD_MODIFICATION, NULL);
  check_jvmti_error(jvmti, error, "Cannot set JVMTI_EVENT_FIELD_MODIFICATION");
  if(gdata.enable_method_call) {
    error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_METHOD_ENTRY, NULL);
    check_jvmti_error(jvmti, error, "Cannot set JVMTI_EVENT_METHOD_ENTRY");
  }
  error = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
  check_jvmti_error(jvmti, error, "Cannot set JVMTI_EVENT_CLASS_PREPARE");
   

  error = (*jvmti)->CreateRawMonitor(jvmti, "agent data", &(gdata.lock));  
  check_jvmti_error(jvmti, error, "Cannot create raw monitor");

  return JNI_OK;
}


JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm)
{
}


void get_thread_name(jvmtiEnv *jvmti, jthread thread, char *tname, int maxlen)
{
  jvmtiThreadInfo info;
  jvmtiError      error;

  (void)memset(&info,0, sizeof(info));
  (void)strcpy(tname, "Unknown");
   
  error = (*jvmti)->GetThreadInfo(jvmti, thread, &info);     
  check_jvmti_error(jvmti, error, "Cannot get thread info");
  
  if ( info.name != NULL ) {
	if ( (int)strlen(info.name) < maxlen ) {
      (void)strcpy(tname, info.name);
	}
	deallocate(jvmti, (void*)info.name);
  }
}


void dump_stack(jvmtiEnv *jvmti, jthread thread)
{
  /* (*jni)->CallStaticVoidMethod(jni, gdata.thread_class, gdata.thread_dump_method); */
  jvmtiFrameInfo frames[100];
  jint count;

  jvmtiError error = (*jvmti)->GetStackTrace(jvmti, thread, 0, 100, frames, &count);
  check_jvmti_error(jvmti, error, "dump_stack GetStackTrace error\n");

  if(error == JVMTI_ERROR_NONE) {
    int i;
    f_log(0, "--------------- begin stack dump -------------------------\n");
    for(i = 0; i < count; ++i) {
      char *method_name, *method_sig;

      error = (*jvmti)->GetMethodName(jvmti, frames[i].method, &method_name, &method_sig, 0);
      check_jvmti_error(jvmti, error, "dump_stack GetMethodName error\n");

      if(error == JVMTI_ERROR_NONE) {
        jclass klass;
        error = (*jvmti)->GetMethodDeclaringClass(jvmti, frames[i].method, &klass);
        check_jvmti_error(jvmti, error, "dump_stack GetMethodDeclaringClass error\n");

        if(error == JVMTI_ERROR_NONE) {
          char *klass_sig;
          error = (*jvmti)->GetClassSignature(jvmti, klass, &klass_sig, 0);
          check_jvmti_error(jvmti, error, "dump_stack GetClassSignature error\n");

          if(error == JVMTI_ERROR_NONE) {
            f_log(0, "      %s %s %s\n", method_name, method_sig, klass_sig);
          }
          deallocate(jvmti, klass_sig);
        }
      }
      deallocate(jvmti, method_name);
      deallocate(jvmti, method_sig);
    }
    f_log(0, "--------------- end stack dump ---------------------------\n\n");
  }
}


void log_field_watch(jvmtiEnv *jvmti, jthread thread, int idx, char* obj, char *new_val)
{
  f_log(0, "Field modification detected. Field:'%s', object:'%s', class:'%s', new value:'%s'\n", watch_field[idx].field_name, obj, watch_field[idx].klass_name, new_val);
  dump_stack(jvmti, thread);
}



char* get_field_name(JNIEnv* jni, jobject field)
{
  jboolean iscopy;
  gdata.field_name_buffer[0] = 0;
  {
    jstring s = (jstring)(*jni)->CallObjectMethod(jni, field, gdata.field_getName_method);
    const char *c = (*jni)->GetStringUTFChars(jni, s, &iscopy);
    strncpy(gdata.field_name_buffer, c, 255);
    (*jni)->ReleaseStringUTFChars(jni, s, c);
  }
  return gdata.field_name_buffer;
}


char* valueOfObj(jvmtiEnv *jvmti, JNIEnv* jni, jobject obj, char* buf, int buf_size) 
{
  jvalue value;
  value.l = obj;
  f_trace(jni, TRACE_DEBUG, "valueOfObj\n");
  return valueOf(jvmti, jni, value, 'L', buf, buf_size);
}


char* valueOf(jvmtiEnv *jvmti, JNIEnv* jni, jvalue val, char sig, char* buf, int buf_size)
{
  jboolean iscopy;

  jmethodID vof = gdata.valueOfL;

  f_trace(jni, TRACE_DEBUG, "valueOf\n");

  switch(sig) {
  case 'Z' : vof = gdata.valueOfZ; break;
    /* case 'B' : vof = gdata.valueOfB; break; */
  case 'C' : vof = gdata.valueOfC; break;
    /* case 'S' : vof = gdata.valueOfS; break; */
  case 'I' : vof = gdata.valueOfI; break;
  case 'J' : vof = gdata.valueOfJ; break;
  case 'F' : vof = gdata.valueOfF; break;
  case 'D' : vof = gdata.valueOfD; break;
  }

  buf[0] = 0;
  {
    jstring s;
    f_trace(jni, TRACE_DEBUG, "CallStaticObjectMethod valueOf %c\n", sig);
    s = (jstring)(*jni)->CallStaticObjectMethod(jni, gdata.string_class, vof, val);

    /*
      f_trace(jni, "result of CallStaticObjectMethod %s\n", get_jni_exception_descr(jni));
      if(jvmti)
      {
      char *class_name;
      (*jni)->ExceptionClear(jni);
      jclass klass = (*jni)->GetObjectClass(jni, o);
      (*jni)->ExceptionClear(jni);
      (*jvmti)->GetClassSignature(jvmti, klass, &class_name, NULL);
      f_trace(jni, "valueOf class %s\n", class_name);
      deallocate(jvmti, class_name);
      s = (jstring)o;
      }
    */

    if ((*jni)->ExceptionCheck(jni)) {
      f_log(jni, "Exception %s happend while calculating valueOf\n", get_jni_exception_descr(jni));
    }
    else {
      const char *c = (*jni)->GetStringUTFChars(jni, s, &iscopy);
      strncpy(buf, c, buf_size);
      (*jni)->ReleaseStringUTFChars(jni, s, c);
    }
  }
  return buf;
}



void parse_agent_options(char *options)
{
  char token[MAX_TOKEN_LEN+1];
  char *next = get_token(options, ",=", token, MAX_TOKEN_LEN);
  char file[MAX_TOKEN_LEN], dir[MAX_TOKEN_LEN];
  time_t stamp;

  watch_field_cnt = 0;
  watch_field_init = 0;

  strcpy(gdata.prefix, "~~~~~~~~~~~");

  memset(&file, 0, sizeof(file));
  memset(&dir, 0, sizeof(dir));

  gdata.verbose = 1;
  gdata.enable_method_call = 1;
  gdata.enable_agent_class_loader = 0;

  for(;next; next = get_token(next, ",=~", token, MAX_TOKEN_LEN)) {
    if (!strcmp(token,"p")) {
      next = get_token(next, ",=", gdata.prefix, MAX_TOKEN_LEN);
    } else if (!strcmp(token,"out")) {
      next = get_token(next, ",=", dir, MAX_TOKEN_LEN);
      {
        char* slash = dir + strlen(dir) - 1;
        if(*slash != '/' && *slash != '\\') {
          *(slash + 1) = '/';
#ifdef WINDOWS
          *(slash + 1) = '\\';
#endif
        }
	  }
	} else if (!strcmp(token, "verbose")) {
      next = get_token(next, ",=", token, MAX_TOKEN_LEN);
      gdata.verbose = atoi(token);
	} else if (!strcmp(token, "wc")) {
      ++watch_field_cnt;
      next = get_token(next, ",=", watch_field[watch_field_cnt-1].klass_name, MAX_TOKEN_LEN);
	} else if (!strcmp(token, "wf")) {
      next = get_token(next, ",=", watch_field[watch_field_cnt-1].field_name, MAX_TOKEN_LEN);
	} else if (!strcmp(token, "wfs")) {
      next = get_token(next, ",=", watch_field[watch_field_cnt-1].field_sig, MAX_TOKEN_LEN);
	} else if (!strcmp(token, "wv")) {
      watch_field[watch_field_cnt-1].field_contains = 1;
      if(*next == '~') {
        watch_field[watch_field_cnt-1].field_contains = 0;
      }
      next = get_token(next, ",=~", watch_field[watch_field_cnt-1].field_value, MAX_TOKEN_LEN);
	} else if (!strcmp(token, "wov")) {
      watch_field[watch_field_cnt-1].object_contains = 1;
      if(*next == '~') {
        watch_field[watch_field_cnt-1].object_contains = 0;
      }
      next = get_token(next, ",=~", watch_field[watch_field_cnt-1].object_value, MAX_TOKEN_LEN);
    } else if(!strcmp(token, "mc")) {
      if(!gdata.enable_method_call) {
        f_log(0, "ERROR: method call is not enabled. mc option is ignored.\n");
        next = get_token(next, ",=", token, MAX_TOKEN_LEN);
      }
      else {
        ++method_call_cnt;
        method_call[method_call_cnt-1].is_static = 0;
        next = get_token(next, ",=", method_call[method_call_cnt-1].klass_name, MAX_TOKEN_LEN);
      }
    } else if (!strcmp(token, "mm")) {
      next = get_token(next, ",=", method_call[method_call_cnt-1].method_name, MAX_TOKEN_LEN);
    } else if (!strcmp(token, "mms")) {
      next = get_token(next, ",=", method_call[method_call_cnt-1].method_sig, MAX_TOKEN_LEN);
    } else if (!strcmp(token, "ms")) {
      method_call[method_call_cnt-1].is_static = 1;
    } else if (!strcmp(token, "disable_mc")) {
      gdata.enable_method_call = 0;
    } else if (!strcmp(token, "enable_acl")) {
      gdata.enable_agent_class_loader = 1;
	} else {
      f_log(0, "Unknown option: %s\n\n", token);
      f_log(0, "Supported options ( ',' or '=' or '~' separated) : \n");
      f_log(0, "\tp=prefix\n");
      f_log(0, "\tout=output_directory\n");
      f_log(0, "\tverbose=0,1,2,3,4\n");
      f_log(0, "\twc=watch class\n");
      f_log(0, "\twf=watch field\n");
      f_log(0, "\twfs=watch field signature\n");
      f_log(0, "\twv=watch value\n");
      f_log(0, "\twov=watch object value\n");
      f_log(0, "\n");
      f_log(0, "Example: -agentlib:agent=\"out=d:\\\\Work\\\\jvmti\\\\agent\\\\tmp\\\\,wc=com/lysis/idtv3/microscheduling/adaptors/PAbstractSlot,wf=mVideoContent,wfs=Lcom/lysis/idtv3/content/adaptors/PVideoContent;,wv=null\"\n");
      exit(0);
	}
  }

  sprintf(file, "%sagent_out_%ld", dir, (long)time(&stamp));
  
  gdata.out_file = fopen(file, "w");
  if(!gdata.out_file) {
    fatal_error("ERROR: Cannot open file %s for writing\n", file);
  }
}


char* get_jni_exception_descr(JNIEnv *jni) 
{
  static char ex_buf[MAX_TO_STRING_LEN];
  ex_buf[0] = 0;
  if ((*jni)->ExceptionCheck(jni)) {
	jvalue v; v.l = (*jni)->ExceptionOccurred(jni);
    (*jni)->ExceptionClear(jni); // have to clear the exception before JNI will work again.
    /* return toString(jni, e, ex_buf, MAX_TO_STRING_LEN - 1); */
    return valueOf(0, jni, v, 'L', ex_buf, MAX_TO_STRING_LEN - 1);
  }
  return ex_buf;
}


jclass find_class(JNIEnv *jni, const char *klass) 
{
  jclass k;
  f_trace(jni, TRACE_DEBUG, "calling find_class %s\n", klass);
  k = (*jni)->FindClass(jni, klass);
  if (k == NULL ) {
    fatal_error("ERROR: JNI: Cannot find class %s %s\n", klass, get_jni_exception_descr(jni));
  }
  return (*jni)->NewGlobalRef(jni, k);
}


jmethodID get_method_id(JNIEnv *jni, jclass klass, const char* method, const char* sig) 
{
  jmethodID mid;
  f_trace(jni, TRACE_DEBUG, "calling get_method_id %s %s\n", method, sig);
  mid = (*jni)->GetMethodID(jni, klass, method, sig);
  if(mid == NULL) {
    fatal_error("ERROR: JNI: GetMethodID returns NULL for %s %s %s\n", method, sig, get_jni_exception_descr(jni));
  }
  return mid;
}

jmethodID get_static_method_id(JNIEnv *jni, jclass klass, const char* method, const char* sig) 
{
  jmethodID mid;
  f_trace(jni, TRACE_DEBUG, "calling get_static_method_id %s %s\n", method, sig);
  mid = (*jni)->GetStaticMethodID(jni, klass, method, sig);
  if(mid == NULL) {
    fatal_error("ERROR: JNI: GetStaticMethodID returns NULL for %s %s %s\n", method, sig, get_jni_exception_descr(jni));
  }
  return mid;
}

jfieldID get_field_id(JNIEnv *jni, jclass klass, const char *field, const char *sig) 
{
  jfieldID fid;
  f_trace(jni, TRACE_DEBUG, "calling get_field_id %s %s\n", field, sig);
  fid = (*jni)->GetFieldID(jni, klass, field, sig);
  if(fid == NULL ) {
    fatal_error("ERROR: JNI: Cannot find field %s with signature %s %s\n", field, sig, get_jni_exception_descr(jni));
  }
  return fid;
}


jfieldID get_static_field_id(JNIEnv *jni, jclass klass, const char *field, const char *sig) 
{
  jfieldID fid;
  f_trace(jni, TRACE_DEBUG, "calling get_static_field_id %s %s\n", field, sig);
  fid = (*jni)->GetStaticFieldID(jni, klass, field, sig);
  if(fid == NULL ) {
    fatal_error("ERROR: JNI: Cannot find field %s with signature %s %s\n", field, sig, get_jni_exception_descr(jni));
  }
  return fid;
}


jmethodID get_value_of_id(JNIEnv *jni, char sig) 
{
  char sign[50];
  memset(sign, 0, sizeof(sign));
  strcpy(sign, "(?)Ljava/lang/String;");
  sign[1] = sig;
  return get_static_method_id(jni, gdata.string_class, "valueOf", sign);
}


void enter_critical_section(jvmtiEnv *jvmti)
{
  jvmtiError error = (*jvmti)->RawMonitorEnter(jvmti, gdata.lock);
  check_jvmti_error(jvmti, error, "Cannot enter with raw monitor");
}


void exit_critical_section(jvmtiEnv *jvmti)
{
  jvmtiError error = (*jvmti)->RawMonitorExit(jvmti, gdata.lock);
  check_jvmti_error(jvmti, error, "Cannot exit with raw monitor");
}


void f_trace(JNIEnv *jni, int level, const char * format, ...)
{
  if(gdata.verbose >= level) {
    va_list ap;
    va_start(ap, format);
    fprintf(stdout, "-----------AGENT TRACE-------");
    vfprintf(stdout, format, ap);
    fflush(stdout);
    va_end(ap);
  }
}


void f_log(JNIEnv *jni, const char * format, ...)
{
  va_list ap;
  va_start(ap, format);
  
  if(gdata.verbose >= TRACE_IMPORTANT) {
    fprintf(stdout, "-----------AGENT TRACE-------");
    vfprintf(stdout, format, ap);
    fflush(stdout);
  }

  if(gdata.out_file) {
    log_current_time(gdata.out_file);
    vfprintf(gdata.out_file, format, ap);
    fflush(gdata.out_file);
  }

  /*
    if(gdata.logger && gdata.trace_method && jni){
    static char buf[MAX_LOG_LEN];
    sprintf(buf, "%s", gdata.prefix);
    vsprintf(buf + strlen(gdata.prefix), format, ap);

    (*jni)->CallVoidMethod(jni, gdata.logger, gdata.trace_method, (*jni)->NewStringUTF(jni, buf));
    }
  */

  va_end(ap);
}



#ifdef WINDOWS

#include <windows.h>



int __stdcall DllMain(void* hModule, unsigned long  ul_reason_for_call, void* lpReserved)
{
  return 1;
}


void log_current_time(FILE* f) 
{
  SYSTEMTIME st;
  GetSystemTime(&st);
  fprintf(f, "%02d:%02d:%02d,%3d ", st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);
}



#else

#include <sys/time.h>

void log_current_time(FILE* f) 
{
  struct timeval tv;
  time_t curtime;
  struct tm *local;

  gettimeofday(&tv, 0);
  curtime = tv.tv_sec;
  local = localtime(&curtime);
  fprintf(f, "%02d:%02d:%02d,%3d ", local->tm_hour, local->tm_min, local->tm_sec, (int)tv.tv_usec);
}

#endif


/* char *method_name, *class_name; */
/* jclass klass; */
/* (*jvmti)->GetMethodName(jvmti, method, &method_name, NULL, NULL); */
/* (*jvmti)->GetMethodDeclaringClass(jvmti, method, &klass); */
/* (*jvmti)->GetClassSignature(jvmti, klass, &class_name, NULL); */


/* char *sign; */
/* error = (*jvmti)->GetClassSignature(jvmti, watch_field[i].klass, &sign, NULL); */
/* if(error != JVMTI_ERROR_NONE) { */
/*   watch_field[i].klass = find_class(jni, watch_field[i].klass_name); */
/*   error = (*jvmti)->GetClassSignature(jvmti, watch_field[i].klass, &sign, NULL); */
/* } */
/* check_jvmti_error(jvmti, error, "GetClassSignature error"); */
/* stdout_message("comparing %d %d\n", klass, watch_field[i].klass); */
//(*jni)->DeleteGlobalRef(jni, klass);
/* stdout_message("comparing %s and %s\n", watch_field[i].field_name, get_field_name(jni, field)); */


/* { */
/*   long i = 0, count = 10000000;  */
/*   time_t now = time(0); */
/*   while(i++ < count) { */
/*     (*jni)->IsInstanceOf(jni, gdata.system_out, gdata.string_class); */
/*   } */
/*   fatal_error("IsInstanceOf count %d exec time %d\n", count, time(0) - now); */
/* } */



/*
  char* toString(JNIEnv* jni, jobject obj, char* buf, int buf_size) 
  {
  jboolean iscopy;
  buf[0] = 0;
  {
  jstring s = (jstring)(*jni)->CallObjectMethod(jni, obj, gdata.to_string);
  const char *c = (*jni)->GetStringUTFChars(jni, s, &iscopy);
  strncpy(buf, c, buf_size);
  (*jni)->ReleaseStringUTFChars(jni, s, c);
  }
  return buf;
  }
*/



/*       if(!gdata.logger && (*jni)->IsSameObject(jni, klass, (*jni)->FindClass(jni, "Lorg/apache/log4j/Logger;"))) { */
/* f_log(jni, "class Lorg/apache/log4j/logger; found\n"); */
/*  { */
/*           jmethodID getLogger = get_static_method_id(jni, klass, "getRootLogger", "()Lorg/apache/log4j/Logger;"); */
/* f_log(jni, "method getLogger found %d\n", getLogger); */
/*             gdata.logger = (*jni)->NewGlobalRef(jni, (*jni)->CallStaticObjectMethod(jni, klass, getLogger)); */
/* f_log(jni, "method getLogger called %d\n", gdata.logger); */
/*             gdata.trace_method = get_method_id(jni, klass, "debug", "(Ljava/lang/Object;)V"); */
/* f_log(jni, "trace method found %d\n", gdata.trace_method); */
/*       } */
/*       } */

/* jobject        logger; */
/* jmethodID      trace_method; */
/* jobject        system_out; */
/* jmethodID      println; */




