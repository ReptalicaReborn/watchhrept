// JNI bridge that runs Node.js (nodejs-mobile) inside the app's ":node" process.
#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#include <unistd.h>

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "node.h"

namespace {

const char *kTag = "ZaloWatchNode";
int stdoutPipe[2];
int stderrPipe[2];

struct PipeArgs {
    int fd;
    int priority;
};

// Node writes to stdout/stderr, which Android discards; forward both to logcat.
void *forwardToLogcat(void *raw) {
    auto *args = static_cast<PipeArgs *>(raw);
    char buf[2048];
    ssize_t n;
    while ((n = read(args->fd, buf, sizeof buf - 1)) > 0) {
        if (buf[n - 1] == '\n') n--;
        buf[n] = '\0';
        __android_log_write(args->priority, kTag, buf);
    }
    return nullptr;
}

void startForwarding(int (&fds)[2], int target, int priority) {
    if (pipe(fds) != 0) return;
    dup2(fds[1], target);
    pthread_t thread;
    if (pthread_create(&thread, nullptr, forwardToLogcat, new PipeArgs{fds[0], priority}) == 0) {
        pthread_detach(thread);
    }
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_watchhrept_wear_server_NodeRuntime_startNode(JNIEnv *env, jobject /* this */, jobjectArray arguments) {
    jsize argc = env->GetArrayLength(arguments);
    std::vector<std::string> args;
    size_t total = 0;
    for (jsize i = 0; i < argc; i++) {
        auto str = static_cast<jstring>(env->GetObjectArrayElement(arguments, i));
        const char *chars = env->GetStringUTFChars(str, nullptr);
        args.emplace_back(chars);
        env->ReleaseStringUTFChars(str, chars);
        env->DeleteLocalRef(str);
        total += args.back().size() + 1;
    }

    // libuv expects all argv strings in one contiguous block. Never freed: Node owns it for the process lifetime.
    char *block = new char[total];
    char **argv = new char *[argc + 1];
    char *cursor = block;
    for (jsize i = 0; i < argc; i++) {
        std::memcpy(cursor, args[i].c_str(), args[i].size() + 1);
        argv[i] = cursor;
        cursor += args[i].size() + 1;
    }
    argv[argc] = nullptr;

    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    startForwarding(stdoutPipe, STDOUT_FILENO, ANDROID_LOG_INFO);
    startForwarding(stderrPipe, STDERR_FILENO, ANDROID_LOG_ERROR);

    return static_cast<jint>(node::Start(argc, argv));
}
