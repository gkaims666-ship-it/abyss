// JNI bridge between Abyss (Kotlin) and llama.cpp. Built only when the project
// is compiled with -PwithLocalLlm. Written against the vocab-split API in
// llama.cpp tag b5596 (see CMakeLists). If you bump the tag and the API shifts,
// adjust the symbols here.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>

#include "llama.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "AbyssLLM", __VA_ARGS__)

static std::mutex g_mutex;
static llama_model*   g_model = nullptr;
static llama_context* g_ctx   = nullptr;

static std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    env->ReleaseStringUTFChars(s, c);
    return out;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_abyss_app_core_local_LlamaBridge_nativeInit(
        JNIEnv* env, jobject, jstring modelPath, jint nCtx, jint nThreads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    static bool backend_ready = false;
    if (!backend_ready) { llama_backend_init(); backend_ready = true; }

    std::string path = jstr(env, modelPath);

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) { LOG("model load failed: %s", path.c_str()); return JNI_FALSE; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = (uint32_t) nCtx;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) { llama_model_free(g_model); g_model = nullptr; return JNI_FALSE; }
    LOG("model loaded");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_abyss_app_core_local_LlamaBridge_nativeGenerate(
        JNIEnv* env, jobject, jstring prompt, jint maxTokens, jfloat temperature) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx) return env->NewStringUTF("");

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    std::string p = jstr(env, prompt);

    // Tokenize prompt.
    int n_prompt = -llama_tokenize(vocab, p.c_str(), (int)p.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, p.c_str(), (int)p.size(), tokens.data(), (int)tokens.size(), true, true) < 0) {
        return env->NewStringUTF("");
    }

    // Sampler chain: top-k, top-p, temperature, distribution.
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result;
    llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());

    for (int generated = 0; generated < maxTokens; ++generated) {
        if (llama_decode(g_ctx, batch) != 0) break;
        llama_token tok = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        char piece[256];
        int n = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, true);
        if (n > 0) result.append(piece, n);

        batch = llama_batch_get_one(&tok, 1);
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_abyss_app_core_local_LlamaBridge_nativeFree(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx)   { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}
