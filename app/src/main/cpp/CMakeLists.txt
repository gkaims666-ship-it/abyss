cmake_minimum_required(VERSION 3.22.1)
project(abyss_llm CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Keep llama.cpp lean for mobile.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)

# Pinned for reproducibility. b5596 provides the vocab-split API the bridge uses
# (llama_model_load_from_file / llama_init_from_model / llama_model_get_vocab /
# llama_vocab / llama_vocab_is_eog). b4404 predated it and the bridge failed to
# compile. Bump only together with abyss_llm.cpp if the API shifts again.
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b5596
)
FetchContent_MakeAvailable(llama_cpp)

add_library(abyss_llm SHARED abyss_llm.cpp)

find_library(log-lib log)
target_link_libraries(abyss_llm llama ${log-lib} android)
