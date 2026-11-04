#include <jni.h>
#include <algorithm>
#include <llama.h>
#include <vector>
#include <string>
#include <atomic>
#include <mutex>
#include <thread>
#include <chrono>
static std::atomic<bool> cancelled{false};
static std::atomic<int> phase{0},prompt_done{0},prompt_total{0},generated{0};
static std::atomic<long long> deadline{0};
static long long now_ms(){return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();}
static bool abort_decode(void*){if(cancelled)return true;if(deadline>0&&now_ms()>deadline){phase=7;return true;}return false;}
extern "C" JNIEXPORT jlongArray JNICALL Java_com_example_arkrecruit_translate_QwenEngine_progressNative(JNIEnv* env,jclass){jlong values[]={phase.load(),prompt_done.load(),prompt_total.load(),generated.load()};jlongArray out=env->NewLongArray(4);env->SetLongArrayRegion(out,0,4,values);return out;}
struct Engine {llama_model* model=nullptr;llama_context* ctx=nullptr;};
static void fail(JNIEnv* env,const char* msg){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),msg);}
static jstring java_utf8(JNIEnv* env,const std::string& s){
  jbyteArray bytes=env->NewByteArray(s.size());env->SetByteArrayRegion(bytes,0,s.size(),reinterpret_cast<const jbyte*>(s.data()));
  jclass cls=env->FindClass("java/lang/String");return (jstring)env->NewObject(cls,env->GetMethodID(cls,"<init>","([BLjava/lang/String;)V"),bytes,env->NewStringUTF("UTF-8"));
}
extern "C" JNIEXPORT jlong JNICALL Java_com_example_arkrecruit_translate_QwenEngine_open(JNIEnv* env,jclass,jstring path){
  phase=1;deadline=0;
  static std::once_flag once;std::call_once(once,[]{llama_backend_init();});
  const char* p=env->GetStringUTFChars(path,nullptr);auto* e=new Engine;
  auto mp=llama_model_default_params();mp.n_gpu_layers=0;mp.use_mmap=true;
  e->model=llama_model_load_from_file(p,mp);env->ReleaseStringUTFChars(path,p);
  if(!e->model){phase=6;delete e;return 0;}
  auto cp=llama_context_default_params();cp.n_ctx=2048;cp.n_batch=256;cp.n_ubatch=128;
  cp.n_threads=std::max(1,std::min(4,(int)std::thread::hardware_concurrency()-2));cp.n_threads_batch=cp.n_threads;cp.abort_callback=abort_decode;cp.abort_callback_data=nullptr;
  e->ctx=llama_init_from_model(e->model,cp);
  if(!e->ctx){llama_model_free(e->model);delete e;return 0;}
  phase=0;return reinterpret_cast<jlong>(e);
}
extern "C" JNIEXPORT void JNICALL Java_com_example_arkrecruit_translate_QwenEngine_cancelNative(JNIEnv*,jclass){cancelled=true;}
extern "C" JNIEXPORT void JNICALL Java_com_example_arkrecruit_translate_QwenEngine_release(JNIEnv*,jclass,jlong ptr){auto* e=(Engine*)ptr;llama_free(e->ctx);llama_model_free(e->model);delete e;}
extern "C" JNIEXPORT jstring JNICALL Java_com_example_arkrecruit_translate_QwenEngine_generate(JNIEnv* env,jclass,jlong ptr,jstring input,jint limit){
  auto* e=(Engine*)ptr;cancelled=false;phase=2;prompt_done=0;prompt_total=0;generated=0;deadline=now_ms()+(limit>240?180000:90000);const char* raw=env->GetStringUTFChars(input,nullptr);std::string prompt(raw);env->ReleaseStringUTFChars(input,raw);
  const llama_vocab* vocab=llama_model_get_vocab(e->model);
  int n=-llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
  if(n<=0||n>1536){fail(env,"Translation input exceeds context");return nullptr;}
  prompt_total=n;
  std::vector<llama_token> tokens(n);llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),n,true,true);
  llama_memory_clear(llama_get_memory(e->ctx),true);
  for(int i=0;i<n;i+=256){if(abort_decode(nullptr)){if(phase==7){fail(env,"Qwen: превышено время обработки");return nullptr;}phase=5;return java_utf8(env,"");}auto b=llama_batch_get_one(tokens.data()+i,std::min(256,n-i));if(llama_decode(e->ctx,b)){if(cancelled){phase=5;return java_utf8(env,"");}phase=6;fail(env,"Qwen: ошибка обработки входа или превышено время");return nullptr;}prompt_done=std::min(n,i+256);}
  phase=3;
  auto* sampler=llama_sampler_init_greedy();std::string output;bool ended=false;
  for(int i=0;i<std::max(32,std::min(480,(int)limit))&&!abort_decode(nullptr);i++){
    auto token=llama_sampler_sample(sampler,e->ctx,-1);if(llama_vocab_is_eog(vocab,token)){ended=true;break;}
    char buf[256];int len=llama_token_to_piece(vocab,token,buf,sizeof(buf),0,true);
    if(len>0)output.append(buf,len);
    else if(len<0){std::vector<char> big(-len);len=llama_token_to_piece(vocab,token,big.data(),big.size(),0,true);if(len>0)output.append(big.data(),len);}
    generated=i+1;auto b=llama_batch_get_one(&token,1);if(llama_decode(e->ctx,b)){llama_sampler_free(sampler);if(cancelled){phase=5;return java_utf8(env,"");}phase=6;fail(env,"Qwen: ошибка генерации или превышено время");return nullptr;}
  }
  llama_sampler_free(sampler);
  if(cancelled){phase=5;return java_utf8(env,"");}
  if(phase==7){fail(env,"Qwen: превышено время генерации");return nullptr;}
  if(!ended){phase=6;fail(env,"Ответ Qwen достиг лимита токенов и не завершён");return nullptr;}
  phase=4;return java_utf8(env,output);
}
