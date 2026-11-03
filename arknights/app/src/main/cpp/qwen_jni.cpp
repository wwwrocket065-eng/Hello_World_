#include <jni.h>
#include <algorithm>
#include <llama.h>
#include <vector>
#include <string>
#include <atomic>
#include <mutex>
#include <thread>
static std::atomic<bool> cancelled{false};
struct Engine {llama_model* model=nullptr;llama_context* ctx=nullptr;};
static void fail(JNIEnv* env,const char* msg){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),msg);}
static jstring java_utf8(JNIEnv* env,const std::string& s){
  jbyteArray bytes=env->NewByteArray(s.size());env->SetByteArrayRegion(bytes,0,s.size(),reinterpret_cast<const jbyte*>(s.data()));
  jclass cls=env->FindClass("java/lang/String");return (jstring)env->NewObject(cls,env->GetMethodID(cls,"<init>","([BLjava/lang/String;)V"),bytes,env->NewStringUTF("UTF-8"));
}
extern "C" JNIEXPORT jlong JNICALL Java_com_example_arkrecruit_translate_QwenEngine_open(JNIEnv* env,jclass,jstring path){
  static std::once_flag once;std::call_once(once,[]{llama_backend_init();});
  const char* p=env->GetStringUTFChars(path,nullptr);auto* e=new Engine;
  auto mp=llama_model_default_params();mp.n_gpu_layers=0;mp.use_mmap=true;
  e->model=llama_model_load_from_file(p,mp);env->ReleaseStringUTFChars(path,p);
  if(!e->model){delete e;return 0;}
  auto cp=llama_context_default_params();cp.n_ctx=2048;cp.n_batch=256;cp.n_ubatch=128;
  cp.n_threads=std::max(1,std::min(4,(int)std::thread::hardware_concurrency()-2));cp.n_threads_batch=cp.n_threads;
  e->ctx=llama_init_from_model(e->model,cp);
  if(!e->ctx){llama_model_free(e->model);delete e;return 0;}
  return reinterpret_cast<jlong>(e);
}
extern "C" JNIEXPORT void JNICALL Java_com_example_arkrecruit_translate_QwenEngine_cancelNative(JNIEnv*,jclass){cancelled=true;}
extern "C" JNIEXPORT void JNICALL Java_com_example_arkrecruit_translate_QwenEngine_release(JNIEnv*,jclass,jlong ptr){auto* e=(Engine*)ptr;llama_free(e->ctx);llama_model_free(e->model);delete e;}
extern "C" JNIEXPORT jstring JNICALL Java_com_example_arkrecruit_translate_QwenEngine_generate(JNIEnv* env,jclass,jlong ptr,jstring input){
  auto* e=(Engine*)ptr;cancelled=false;const char* raw=env->GetStringUTFChars(input,nullptr);std::string prompt(raw);env->ReleaseStringUTFChars(input,raw);
  const llama_vocab* vocab=llama_model_get_vocab(e->model);
  int n=-llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
  if(n<=0||n>1536){fail(env,"Translation input exceeds context");return nullptr;}
  std::vector<llama_token> tokens(n);llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),n,true,true);
  llama_memory_clear(llama_get_memory(e->ctx),true);
  for(int i=0;i<n;i+=256){if(cancelled)return java_utf8(env,"");auto b=llama_batch_get_one(tokens.data()+i,std::min(256,n-i));if(llama_decode(e->ctx,b)){fail(env,"Qwen prompt decode failed");return nullptr;}}
  auto* sampler=llama_sampler_init_greedy();std::string output;bool ended=false;
  for(int i=0;i<480&&!cancelled;i++){
    auto token=llama_sampler_sample(sampler,e->ctx,-1);if(llama_vocab_is_eog(vocab,token)){ended=true;break;}
    char buf[256];int len=llama_token_to_piece(vocab,token,buf,sizeof(buf),0,true);
    if(len>0)output.append(buf,len);
    else if(len<0){std::vector<char> big(-len);len=llama_token_to_piece(vocab,token,big.data(),big.size(),0,true);if(len>0)output.append(big.data(),len);}
    auto b=llama_batch_get_one(&token,1);if(llama_decode(e->ctx,b)){llama_sampler_free(sampler);fail(env,"Qwen generation failed");return nullptr;}
  }
  llama_sampler_free(sampler);
  if(cancelled)return java_utf8(env,"");
  if(!ended){fail(env,"Translation truncated; reduce input length");return nullptr;}
  return java_utf8(env,output);
}
