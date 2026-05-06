#include <jni.h>
#include <cstdlib>
#include <unistd.h>

extern "C" JNIEXPORT void JNICALL
Java_io_justtrack_testapp_CrashActivity_crashAbort(JNIEnv *, jobject) {
    abort();
}

extern "C" JNIEXPORT void JNICALL
Java_io_justtrack_testapp_CrashActivity_crashILL(JNIEnv *, jobject) {
    kill(getpid(), SIGILL);
}

extern "C" JNIEXPORT void JNICALL
Java_io_justtrack_testapp_CrashActivity_crashSEGV(JNIEnv *, jobject) {
    kill(getpid(), SIGSEGV);
}

extern "C" JNIEXPORT void JNICALL
Java_io_justtrack_testapp_CrashActivity_crashFPE(JNIEnv *, jobject) {
    kill(getpid(), SIGFPE);
}

extern "C" JNIEXPORT void JNICALL
Java_io_justtrack_testapp_CrashActivity_crashBUS(JNIEnv *, jobject) {
    kill(getpid(), SIGBUS);
}

extern "C" JNIEXPORT void JNICALL
Java_io_justtrack_testapp_CrashActivity_crashPIPE(JNIEnv *, jobject) {
    kill(getpid(), SIGPIPE);
}

extern "C" JNIEXPORT void JNICALL
Java_io_justtrack_testapp_CrashActivity_crashTRAP(JNIEnv *, jobject) {
    kill(getpid(), SIGTRAP);
}