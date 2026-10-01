#include <jni.h>
#include <cstdlib>
#include <unistd.h>
#include <thread>
#include <csignal>
#include <execinfo.h>
#include <sstream>
#include <fcntl.h>
#include <unwind.h>
#include <dlfcn.h>
#include <cxxabi.h>
#include <android/log.h>
#include <string>
#include <vector>

#define LOG_TAG "JustTrackSdk-CrashReportNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALT_STACK_SIZE (64 * 1024) // 64 KB
#define JSON_FORMAT "{\n\t\"data\": {\n\t\t\"timestamp\": \"%s\",\n\t\t\"signal\": %d,\n\t\t\"error\": %d,\n\t\t\"code\": %d,\n\t\t\"pid\": %d,\n\t\t\"uid\": %d,\n\t\t\"status\": %d,\n\t\t\"addr\": \"%p\",\n\t\t\"value\": %d,\n\t\t\"band\": %ld,\n\t\t\"crashType\": %d,\n\t\t\"stacktrace\": \"%s\"\n\t}, \n\t\"breadcrumbs\": ["
#define ESCAPE_BUFFER_SIZE 8192
#define HANDLED_SIGNAL_COUNT 7  // Number of signals you are handling

// Array to hold previous handlers for specific signals
static struct sigaction oldSignalHandlers[HANDLED_SIGNAL_COUNT];
static int handledSignals[HANDLED_SIGNAL_COUNT] = {SIGABRT, SIGILL, SIGSEGV, SIGFPE, SIGBUS,
                                                   SIGPIPE, SIGTRAP};

static char altStack[ALT_STACK_SIZE];

static std::string internalFile;
static char jsonBuffer[32768];

int NATIVE_CRASH_TYPE = 1;


namespace JusttrackSignalHandler {
    struct BacktraceState {
        const void **current;
        const void **end;
    };
}

static const char *getTimeStamp() {
    static char buffer[64];
    struct timeval tv = {};
    gettimeofday(&tv, nullptr);
    struct tm *tmInfo = gmtime(&tv.tv_sec);

    // Format the time to yyyy-MM-dd'T'HH:mm:ss
    strftime(buffer, sizeof(buffer), "%Y-%m-%dT%H:%M:%S", tmInfo);

    // Append milliseconds and the 'Z' character
    snprintf(buffer + strlen(buffer), sizeof(buffer) - strlen(buffer), ".%03ldZ",
             tv.tv_usec / 1000);

    return buffer;
}

static _Unwind_Reason_Code unwindCallback(struct _Unwind_Context *context, void *arg) {
    auto state = static_cast<JusttrackSignalHandler::BacktraceState *>(arg);
    uintptr_t pc = _Unwind_GetIP(context);
    if (pc) {
        if (state->current == state->end) {
            return _URC_END_OF_STACK;
        } else {
            auto temp = state->current;
            *temp = reinterpret_cast<void *>(pc);
            state->current++;
        }
    }
    return _URC_NO_REASON;
}

static void captureBacktrace(const void **buffer, int max) {
    JusttrackSignalHandler::BacktraceState state = {buffer, buffer + max};
    _Unwind_Backtrace(unwindCallback, &state);
}

static void dumpBacktrace(char *buffer, size_t bufferSize, const void **stack, int max) {
    int index = 0;
    char *currentPosition = buffer; // Initialize the position pointer to the start of the buffer
    size_t currentLength = 0; // Start with no content in the buffer

    // Static buffer for storing a single formatted line of the stack trace
    static char stacktraceLine[1024];

    while (index < max && stack[index] != nullptr) {
        const void *addr = stack[index];
        const char *symbol = "";

        Dl_info info;
        if (dladdr(addr, &info) && info.dli_sname) {
            // Use a preallocated buffer for demangling
            static char demangled[1024];
            size_t demangled_size = sizeof(demangled);
            int status = -1;
            // Demangle the symbol name
            abi::__cxa_demangle(info.dli_sname, demangled, &demangled_size, &status);
            // Use the demangled name if demangling was successful, otherwise use the mangled name
            symbol = (status == 0) ? demangled : info.dli_sname;
        } else {
            symbol = "";  // Leave the method name as blank if it cannot be fetched
        }

        int lineLength = snprintf(stacktraceLine, sizeof(stacktraceLine), "#%02d  pc %p  %s\n", index, addr, symbol);

        // Correctly handle the case where snprintf return value is larger than buffer size
        if (lineLength >= sizeof(stacktraceLine)) {
            lineLength = sizeof(stacktraceLine) - 1; // Clamp lineLength
        }

        // Continue with your logic to check if there is enough space in the main buffer
        if (currentLength + lineLength < bufferSize) {
            memcpy(currentPosition, stacktraceLine, lineLength);
            currentPosition[lineLength] = '\0'; // Ensure null termination
            currentPosition += lineLength; // Advance currentPosition
            currentLength += lineLength; // Update currentLength
        } else {
            break; // Buffer full
        }

        index++;
    }
}

static char *escapeJson(const char *input) {
    static char output[ESCAPE_BUFFER_SIZE];
    char *p = output;
    size_t inputLength = strlen(input);

    for (size_t index = 0;
         index < inputLength && (p - output) < (ESCAPE_BUFFER_SIZE - 2); index++) { // Reserve space for 2 chars
        switch (input[index]) {
            case '\"':
                if ((p - output) < (ESCAPE_BUFFER_SIZE - 2)) {
                    *p++ = '\\';
                    *p++ = '\"';
                }
                break;
            case '\\':
                if ((p - output) < (ESCAPE_BUFFER_SIZE - 2)) {
                    *p++ = '\\';
                    *p++ = '\\';
                }
                break;
            case '\n':
                if ((p - output) < (ESCAPE_BUFFER_SIZE - 2)) {
                    *p++ = '\\';
                    *p++ = 'n';
                }
                break;
            case '\r':
                if ((p - output) < (ESCAPE_BUFFER_SIZE - 2)) {
                    *p++ = '\\';
                    *p++ = 'r';
                }
                break;
            case '\t':
                if ((p - output) < (ESCAPE_BUFFER_SIZE - 2)) {
                    *p++ = '\\';
                    *p++ = 't';
                }
                break;
            default:
                if ((p - output) < (ESCAPE_BUFFER_SIZE - 1)) { *p++ = input[index]; }
        }
    }
    *p = '\0'; // Ensure null-termination
    return output;
}

static void native_signal_handler(int crashSignal, siginfo_t *si, void *) {
    const char *timeStamp = getTimeStamp();
    const size_t max = 60;
    const void *buffer[max];

    // Capture the stack trace
    captureBacktrace(buffer, max);

    // Prepare a buffer for the stack trace
    char stacktraceBuffer[8192] = "";

    // Dump the stack trace to the buffer
    dumpBacktrace(stacktraceBuffer, sizeof(stacktraceBuffer), buffer, max);

    char *escapedStacktrace = escapeJson(stacktraceBuffer);

    snprintf(jsonBuffer, sizeof(jsonBuffer), JSON_FORMAT, timeStamp, si->si_signo, si->si_errno,
             si->si_code, si->si_pid, si->si_uid, si->si_status, si->si_addr,
             si->si_value.sival_int, si->si_band, NATIVE_CRASH_TYPE, escapedStacktrace);

    // Save stack trace to a file
    int fd = open(internalFile.c_str(), O_WRONLY | O_CREAT | O_TRUNC, S_IRWXU);
    if (fd != -1) {
        write(fd, jsonBuffer, strlen(jsonBuffer));

        write(fd, "\n\t]\n}", 6);

        close(fd);
    }

    int receivedSignalIndex = -1;
    for (int signalIndex = 0; signalIndex < HANDLED_SIGNAL_COUNT; signalIndex++) {
        if (handledSignals[signalIndex] == crashSignal) {
            receivedSignalIndex = signalIndex;
            break;
        }
    }

    if (receivedSignalIndex != -1) {
        // Restore the original signal action
        sigaction(crashSignal, &oldSignalHandlers[receivedSignalIndex], nullptr);
    }

    raise(crashSignal);
}

static void setup_signal_handler() {
    stack_t signalStack;
    signalStack.ss_sp = altStack;
    signalStack.ss_size = sizeof(altStack);
    signalStack.ss_flags = 0;
    if (sigaltstack(&signalStack, nullptr) == -1) {
        LOGE("Failed to set up alternate signal stack\n");
        return;
    }

    struct sigaction signalAction = {};
    signalAction.sa_flags = SA_SIGINFO | SA_ONSTACK;
    signalAction.sa_sigaction = native_signal_handler;
    sigemptyset(&signalAction.sa_mask);

    for (int index = 0; index < HANDLED_SIGNAL_COUNT; index++) {
        if (sigaction(handledSignals[index], &signalAction, &oldSignalHandlers[index]) != 0) {
            perror("Failed to set signal handler");
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_justtrack_crashes_CrashReportNativeLoaderImpl_registerListener(JNIEnv *env, jobject, jstring packageName, jstring filePrefixName) {
    // Convert the jstring to a C string
    const char *packageNameStr = env->GetStringUTFChars(packageName, nullptr);
    const char *filePrefixNameCStr = env->GetStringUTFChars(filePrefixName, nullptr);
    const char *timeStamp = getTimeStamp();

    internalFile = std::string("/data/data/") + packageNameStr + std::string("/files/") + filePrefixNameCStr + timeStamp;

    // Don't forget to release the string when you're done with it
    env->ReleaseStringUTFChars(packageName, packageNameStr);
    env->ReleaseStringUTFChars(filePrefixName, filePrefixNameCStr);

    setup_signal_handler();
}

