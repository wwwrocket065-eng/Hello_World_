#include <jni.h>
#include "cloud_goose.h"
static int initialized = 0;
// Java benchmark executor must be single-threaded: Goose export calls share its init thread.
JNIEXPORT jdouble JNICALL Java_com_cloudlab_raymarch_MainActivity_gooseBatch(JNIEnv *env,jclass klass,jdouble cover,jlong tick){
  (void)env;(void)klass;
  if(!initialized){goose_init(0,(char**)0);initialized=1;}
  return cloud_batch((double)cover,(int64_t)tick);
}