cmake_minimum_required(VERSION 3.20)

project(G134OfficeLite
    VERSION 0.1.0
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_executable(G134OfficeLite WIN32 src/main.cpp)
target_compile_definitions(G134OfficeLite PRIVATE UNICODE _UNICODE)
if(MSVC)
    target_compile_options(G134OfficeLite PRIVATE /utf-8 /W4)
endif()
target_link_libraries(G134OfficeLite PRIVATE user32 gdi32 comdlg32 shell32)
