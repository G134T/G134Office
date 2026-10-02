cmake_minimum_required(VERSION 3.20)

project(G134OfficeLite
    VERSION 0.2.0
    LANGUAGES CXX
)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(EMSCRIPTEN)
    add_executable(G134OfficeLiteCore web/wasm/lite_core.cpp)
    set_target_properties(G134OfficeLiteCore PROPERTIES
        OUTPUT_NAME "g134lite"
        SUFFIX ".js"
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/web"
    )
    target_link_options(G134OfficeLiteCore PRIVATE
        "-O3"
        "-sWASM=1"
        "-sMODULARIZE=1"
        "-sEXPORT_NAME=G134LiteCore"
        "-sALLOW_MEMORY_GROWTH=1"
        "-sEXPORTED_FUNCTIONS=['_g134_word_count','_g134_char_count','_g134_line_count','_g134_reading_seconds','_g134_nonspace_char_count','_g134_is_empty']"
        "-sEXPORTED_RUNTIME_METHODS=['ccall']"
    )
else()
    add_library(lite_text STATIC src/textutil.cpp)
    target_include_directories(lite_text PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/src)
    target_compile_definitions(lite_text PUBLIC UNICODE _UNICODE)
    if(MSVC)
        target_compile_options(lite_text PRIVATE /utf-8 /W4 /permissive-)
    endif()

    add_executable(G134OfficeLite WIN32 src/main.cpp src/app.rc)
    target_compile_definitions(G134OfficeLite PRIVATE UNICODE _UNICODE)
    target_link_libraries(G134OfficeLite PRIVATE lite_text user32 gdi32 comdlg32 shell32 comctl32 ole32 uuid)
    if(MSVC)
        target_compile_options(G134OfficeLite PRIVATE $<$<COMPILE_LANGUAGE:CXX>:/utf-8> $<$<COMPILE_LANGUAGE:CXX>:/W4> $<$<COMPILE_LANGUAGE:CXX>:/permissive->)
        target_link_options(G134OfficeLite PRIVATE "/ENTRY:wWinMainCRTStartup" "/MANIFEST:NO")
    elseif(MINGW)
        target_link_options(G134OfficeLite PRIVATE -municode)
    endif()

    add_executable(lite_tests tests/textutil_test.cpp)
    target_link_libraries(lite_tests PRIVATE lite_text)
    if(MSVC)
        target_compile_options(lite_tests PRIVATE /utf-8 /W4 /permissive-)
    endif()
endif()
