#include <cstdint>
#include <cstring>
#include <limits>
#ifndef GLASSSTUDY_TIMER_TEST
#include <jni.h>
#endif

// EGL's ABI, kept independent of GLES header versions; all GL calls are
// resolved only on the optical renderer's current context/thread.
extern "C" void (*eglGetProcAddress(const char *name))();
namespace {
constexpr unsigned TIME_ELAPSED=0x88BF, COUNTER_BITS=0x8864;
constexpr unsigned RESULT=0x8866, AVAILABLE=0x8867, DISJOINT=0x8FBB;
struct Backend {
    void (*gen)(int,unsigned*)=nullptr;
    void (*del)(int,const unsigned*)=nullptr;
    void (*begin)(unsigned,unsigned)=nullptr;
    void (*end)(unsigned)=nullptr;
    void (*query)(unsigned,unsigned,int*)=nullptr;
    void (*get)(unsigned,unsigned,unsigned*)=nullptr;
    void (*get64)(unsigned,unsigned,uint64_t*)=nullptr;
    void (*integer)(unsigned,int*)=nullptr;
    bool enabled=false;
};
thread_local Backend api;
template<class T> T load(const char* name) { return reinterpret_cast<T>(eglGetProcAddress(name)); }
}
extern "C" int glassstudyTimerInit() {
    api=Backend{};
    api.gen=load<decltype(api.gen)>("glGenQueriesEXT");
    api.del=load<decltype(api.del)>("glDeleteQueriesEXT");
    api.begin=load<decltype(api.begin)>("glBeginQueryEXT");
    api.end=load<decltype(api.end)>("glEndQueryEXT");
    api.query=load<decltype(api.query)>("glGetQueryivEXT");
    api.get=load<decltype(api.get)>("glGetQueryObjectuivEXT");
    api.get64=load<decltype(api.get64)>("glGetQueryObjectui64vEXT");
    api.integer=load<decltype(api.integer)>("glGetIntegerv");
    if(!api.gen || !api.del || !api.begin || !api.end || !api.query || !api.get || !api.get64 || !api.integer)return 0;
    int bits=0;api.query(TIME_ELAPSED,COUNTER_BITS,&bits);
    // A 32-bit *hardware counter* can overflow on a long phone draw even
    // when queried with the 64-bit accessor. Disable rather than misreport.
    api.enabled=bits>=64;
    if(api.enabled) { int ignored=0;api.integer(DISJOINT,&ignored); }
    return bits;
}
extern "C" unsigned glassstudyTimerBegin() {
    if(!api.enabled)return 0;
    unsigned id=0;api.gen(1,&id);if(id)api.begin(TIME_ELAPSED,id);return id;
}
extern "C" void glassstudyTimerEnd() { if(api.enabled)api.end(TIME_ELAPSED); }
extern "C" void glassstudyTimerDelete(unsigned id) { if(api.enabled && id)api.del(1,&id); }
extern "C" bool glassstudyTimerDisjoint() {
    if(!api.enabled)return false;
    int value=0;api.integer(DISJOINT,&value);return value!=0;
}
extern "C" int64_t glassstudyTimerResult(unsigned id) {
    if(!api.enabled || !id)return -2;
    unsigned ready=0;api.get(id,AVAILABLE,&ready);
    if(!ready)return -1; // NEVER wait on an unfinished query.
    uint64_t value=0;api.get64(id,RESULT,&value);
    return value<=uint64_t(std::numeric_limits<int64_t>::max()) ? int64_t(value) : -2;
}
#ifndef GLASSSTUDY_TIMER_TEST
extern "C" JNIEXPORT jint JNICALL Java_com_glassstudy_filament_OpticalGpuTimer_nativeInit(JNIEnv*,jobject) { return glassstudyTimerInit(); }
extern "C" JNIEXPORT jint JNICALL Java_com_glassstudy_filament_OpticalGpuTimer_nativeBegin(JNIEnv*,jobject) { return static_cast<jint>(glassstudyTimerBegin()); }
extern "C" JNIEXPORT void JNICALL Java_com_glassstudy_filament_OpticalGpuTimer_nativeEnd(JNIEnv*,jobject) { glassstudyTimerEnd(); }
extern "C" JNIEXPORT void JNICALL Java_com_glassstudy_filament_OpticalGpuTimer_nativeDelete(JNIEnv*,jobject,jint id) { glassstudyTimerDelete(static_cast<unsigned>(id)); }
extern "C" JNIEXPORT jboolean JNICALL Java_com_glassstudy_filament_OpticalGpuTimer_nativeDisjoint(JNIEnv*,jobject) { return glassstudyTimerDisjoint(); }
extern "C" JNIEXPORT jlong JNICALL Java_com_glassstudy_filament_OpticalGpuTimer_nativeResult(JNIEnv*,jobject,jint id) { return glassstudyTimerResult(static_cast<unsigned>(id)); }
#endif
