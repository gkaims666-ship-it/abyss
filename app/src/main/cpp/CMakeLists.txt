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

# Pinned for reproducibility. Bump if the bridge API below needs a newer one.
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b4404
)
FetchContent_MakeAvailable(llama_cpp)

add_library(abyss_llm SHARED abyss_llm.cpp)

find_library(log-lib log)
target_link_libraries(abyss_llm llama ${log-lib} android)
