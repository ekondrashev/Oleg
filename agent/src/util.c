
#include "util.h"

void stdout_message(const char * format, ...)
{
  va_list ap;
  va_start(ap, format);
  (void)vfprintf(stdout, format, ap);
  va_end(ap);
  fflush(stdout);
}

void fatal_error(const char * format, ...)
{
  va_list ap;
  va_start(ap, format);
  (void)vfprintf(stderr, format, ap);
  (void)fflush(stderr);
  va_end(ap);
  exit(3);
}

/* Get a token from a string (strtok is not MT-safe)
 *    str	String to scan
 *    seps      Separation characters
 *    buf       Place to put results
 *    max       Size of buf
 *  Returns NULL if no token available or can't do the scan.
 */
char * get_token(char *str, char *seps, char *buf, int max)
{
  int len;
    
  buf[0] = 0;
  if ( str==NULL || str[0]==0 ) {
	return NULL;
  }
  str += strspn(str, seps);
  if ( str[0]==0 ) {
	return NULL;
  }
  len = (int)strcspn(str, seps);
  if ( len >= max ) {
	return NULL;
  }
  (void)strncpy(buf, str, len);
  buf[len] = 0;
  return str+len;
}

/* Determines if a class/method is specified by a list item 
 *   item	String that represents a pattern to match
 *          	  If it starts with a '*', then any class is allowed
 *                If it ends with a '*', then any method is allowed
 *   cname	Class name, e.g. "java.lang.Object"
 *   mname      Method name, e.g. "<init>"
 *  Returns 1(true) or 0(false).
 */
static int covered_by_list_item(char *item, char *cname, char *mname)
{
  int      len;
    
  len = (int)strlen(item);
  if ( item[0]=='*' ) {
	if ( strncmp(mname, item+1, len-1)==0 ) {
      return 1;
	}
  } else if ( item[len-1]=='*' ) {
	if ( strncmp(cname, item, len-1)==0 ) {
      return 1;
	}
  } else {
	int cname_len;
	
	cname_len = (int)strlen(cname);
	if ( strncmp(cname, item, (len>cname_len?cname_len:len))==0 ) {
      if ( cname_len >= len ) {
		/* No method name supplied in item, we must have matched */
		return 1;
      } else {
		int mname_len;
		
		mname_len = (int)strlen(mname);
		item += cname_len+1;
		len -= cname_len+1;
		if ( strncmp(mname, item, (len>mname_len?mname_len:len))==0 ) {
          return 1;
		}
      }
	}
  }
  return 0;
}

/* Determines if a class/method is specified by this list
 *   list	String of comma separated pattern items
 *   cname	Class name, e.g. "java.lang.Object"
 *   mname      Method name, e.g. "<init>"
 *  Returns 1(true) or 0(false).
 */
static int covered_by_list(char *list, char *cname, char *mname)
{
  char  token[1024];
  char *next;
    
  if ( list[0] == 0 ) {
    return 0;
  }
	
  next = get_token(list, ",", token, sizeof(token));
  while ( next != NULL ) {
	if ( covered_by_list_item(token, cname, mname) ) {
      return 1;
	}
    next = get_token(next, ",", token, sizeof(token));
  }
  return 0;
}

/* Determines which class and methods we are interested in 
 *   cname		Class name, e.g. "java.lang.Object"
 *   mname      	Method name, e.g. "<init>"
 *   include_list	Empty or an explicit list for inclusion
 *   exclude_list	Empty or an explicit list for exclusion
 *  Returns 1(true) or 0(false).
 */
int interested(char *cname, char *mname, char *include_list, char *exclude_list)
{
  if ( exclude_list!=NULL && exclude_list[0]!=0 && 
       covered_by_list(exclude_list, cname, mname) ) {
    return 0;
  }
  if ( include_list!=NULL && include_list[0]!=0 && 
       !covered_by_list(include_list, cname, mname) ) {
    return 0;
  }
  return 1;
}

/* ------------------------------------------------------------------- */
/* Generic JVMTI utility functions */

void check_jvmti_error(jvmtiEnv *jvmti, jvmtiError errnum, const char *str)
{
  if ( errnum != JVMTI_ERROR_NONE ) {
	char       *errnum_str;
	
	errnum_str = NULL;
	(void)(*jvmti)->GetErrorName(jvmti, errnum, &errnum_str);
	
	fatal_error("ERROR: JVMTI: %d(%s): %s\n", errnum, 
                (errnum_str==NULL?"Unknown":errnum_str),
                (str==NULL?"":str));
  }
}


void deallocate(jvmtiEnv *jvmti, void *ptr)
{
  jvmtiError error;
    
  error = (*jvmti)->Deallocate(jvmti, ptr);
  check_jvmti_error(jvmti, error, "Cannot deallocate memory");
}


void* allocate(jvmtiEnv *jvmti, jint len)
{
  jvmtiError error;
  void      *ptr;
    
  error = (*jvmti)->Allocate(jvmti, len, (unsigned char **)&ptr);
  check_jvmti_error(jvmti, error, "Cannot allocate memory");
  return ptr;
}

/* Add demo jar file to boot class path (the BCI Tracker class must be 
 *     in the boot classpath)
 *
 *   WARNING: This code assumes that the jar file can be found at one of:
 *              ${JAVA_HOME}/demo/jvmti/${DEMO_NAME}/${DEMO_NAME}.jar
 *              ${JAVA_HOME}/../demo/jvmti/${DEMO_NAME}/${DEMO_NAME}.jar
 *            where JAVA_HOME may refer to the jre directory.
 *            Both these values are added to the boot classpath.
 *            These locations are only true for these demos, installed
 *            in the JDK area. Platform specific code could be used to
 *            find the location of the DLL or .so library, and construct a
 *            path name to the jar file, relative to the library location.
 */
void
add_demo_jar_to_bootclasspath(jvmtiEnv *jvmti, char *demo_name)
{
  jvmtiError error;
  char      *file_sep;
  int        max_len;
  char      *java_home;
  char       jar_path[FILENAME_MAX+1];
   
  java_home = NULL;
  error = (*jvmti)->GetSystemProperty(jvmti, "java.home", &java_home);
  check_jvmti_error(jvmti, error, "Cannot get java.home property value");
  if ( java_home == NULL || java_home[0] == 0 ) {
	fatal_error("ERROR: Java home not found\n");
  }
   
#ifdef WIN32
  file_sep = "\\";
#else
  file_sep = "/";
#endif
    
  max_len = (int)(strlen(java_home) + strlen(demo_name)*2 + 
                  strlen(file_sep)*5 + 
                  16 /* ".." "demo" "jvmti" ".jar" NULL */ );
  if ( max_len > (int)sizeof(jar_path) ) {
	fatal_error("ERROR: Path to jar file too long\n");
  }
  (void)strcpy(jar_path, java_home);
  (void)strcat(jar_path, file_sep);
  (void)strcat(jar_path, "demo");
  (void)strcat(jar_path, file_sep);
  (void)strcat(jar_path, "jvmti");
  (void)strcat(jar_path, file_sep);
  (void)strcat(jar_path, demo_name);
  (void)strcat(jar_path, file_sep);
  (void)strcat(jar_path, demo_name);
  (void)strcat(jar_path, ".jar");
  error = (*jvmti)->AddToBootstrapClassLoaderSearch(jvmti, (const char*)jar_path);
  check_jvmti_error(jvmti, error, "Cannot add to boot classpath");
    
  (void)strcpy(jar_path, java_home);
  (void)strcat(jar_path, file_sep);
  (void)strcat(jar_path, "..");
  (void)strcat(jar_path, file_sep);
  (void)strcat(jar_path, "demo");
  (void)strcat(jar_path, file_sep);
  (void)strcat(jar_path, "jvmti");
  (void)strcat(jar_path, file_sep);
  (void)strcat(jar_path, demo_name);
  (void)strcat(jar_path, file_sep);
  (void)strcat(jar_path, demo_name);
  (void)strcat(jar_path, ".jar");
    
  error = (*jvmti)->AddToBootstrapClassLoaderSearch(jvmti, (const char*)jar_path);
  check_jvmti_error(jvmti, error, "Cannot add to boot classpath");
}

/* ------------------------------------------------------------------- */
