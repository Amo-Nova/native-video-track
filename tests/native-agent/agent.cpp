#include <jni.h>
#include <jvmti.h>
#include <string>
#include <cstring>

// Test adapter for Bitwig's trimmed JVM, which has JVMTI but omits java.instrument.
// Calls only the workspace's Java model test. Does not synthesize Windows UI input.
extern "C" __declspec(dllexport) jint JNICALL Agent_OnAttach(JavaVM* vm,char* options,void*) {
    JNIEnv* env=nullptr;jvmtiEnv* ti=nullptr;
    if(vm->GetEnv((void**)&env,JNI_VERSION_1_8)!=JNI_OK||vm->GetEnv((void**)&ti,JVMTI_VERSION_1_2)!=JNI_OK)return JNI_ERR;
    std::string args=options?options:"";auto separator=args.find('|');if(separator==std::string::npos)return JNI_ERR;
    std::string jar=args.substr(0,separator),config=args.substr(separator+1);
    jint count=0;jclass* classes=nullptr;jclass bridge=nullptr;
    if(ti->GetLoadedClasses(&count,&classes)!=JVMTI_ERROR_NONE)return JNI_ERR;
    for(int i=0;i<count;i++){
        char* signature=nullptr;
        if(ti->GetClassSignature(classes[i],&signature,nullptr)==JVMTI_ERROR_NONE){
            if(std::strcmp(signature,"Llocal/bitwig/video/BitwigBridge;")==0)bridge=classes[i];
            ti->Deallocate((unsigned char*)signature);
        }
    }
    ti->Deallocate((unsigned char*)classes);if(!bridge)return JNI_ERR;
    jclass cls=env->FindClass("java/lang/Class");
    jobject parent=env->CallObjectMethod(bridge,env->GetMethodID(cls,"getClassLoader","()Ljava/lang/ClassLoader;"));
    jclass fileClass=env->FindClass("java/io/File");
    jobject file=env->NewObject(fileClass,env->GetMethodID(fileClass,"<init>","(Ljava/lang/String;)V"),env->NewStringUTF(jar.c_str()));
    jobject uri=env->CallObjectMethod(file,env->GetMethodID(fileClass,"toURI","()Ljava/net/URI;"));
    jobject url=env->CallObjectMethod(uri,env->GetMethodID(env->FindClass("java/net/URI"),"toURL","()Ljava/net/URL;"));
    jobjectArray urls=env->NewObjectArray(1,env->FindClass("java/net/URL"),url);
    jclass loaderClass=env->FindClass("java/net/URLClassLoader");
    jobject loader=env->CallStaticObjectMethod(loaderClass,env->GetStaticMethodID(loaderClass,"newInstance","([Ljava/net/URL;Ljava/lang/ClassLoader;)Ljava/net/URLClassLoader;"),urls,parent);
    jclass driver=(jclass)env->CallObjectMethod(loader,env->GetMethodID(env->FindClass("java/lang/ClassLoader"),"loadClass","(Ljava/lang/String;)Ljava/lang/Class;"),env->NewStringUTF("local.bitwig.video.test.NativeAcceptanceAgent"));
    if(env->ExceptionCheck()||!driver){env->ExceptionDescribe();env->ExceptionClear();return JNI_ERR;}
    jmethodID entry=env->GetStaticMethodID(driver,"nativeMain","(Ljava/lang/String;Ljava/lang/Class;)V");
    if(!entry){env->ExceptionDescribe();env->ExceptionClear();return JNI_ERR;}
    env->CallStaticVoidMethod(driver,entry,env->NewStringUTF(config.c_str()),bridge);
    if(env->ExceptionCheck()){env->ExceptionDescribe();env->ExceptionClear();return JNI_ERR;}
    return JNI_OK;
}
