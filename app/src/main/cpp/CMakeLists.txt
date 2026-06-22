cmake_minimum_required(VERSION 3.22.1)

project(viceandroid)

get_filename_component(WORKSPACE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../.." ABSOLUTE)
set(VICE_BUILD_DIR "${WORKSPACE_DIR}/VICE-source/vice-3.10/build-android-arm64-headless")
set(VICE_SRC_DIR "${WORKSPACE_DIR}/VICE-source/vice-3.10/src")

set(VICE_BASE_OBJECT_NAMES
        alarm attach autostart autostart-prg cbmdos cbmimage charset clipboard
        cmdline color crc32 crt debug dma event findpath fliplist gcr info init
        initcmdline interrupt kbdbuf keyboard keymap lib log machine-bus machine
        main mainlock m3u network opencbmlib palette profiler ram rawfile rawnet
        resources romset screenshot sha1 snapshot socket sound sysfile traps util
        vicefeatures vsync zfile zipcode midi)

set(VICE_BASE_OBJECTS)
foreach(obj ${VICE_BASE_OBJECT_NAMES})
    list(APPEND VICE_BASE_OBJECTS "${VICE_BUILD_DIR}/src/${obj}.o")
endforeach()

set(VICE_STATIC_LIBS
        "${VICE_BUILD_DIR}/src/arch/shared/libarchdep.a"
        "${VICE_BUILD_DIR}/src/tapeport/libtapeport.a"
        "${VICE_BUILD_DIR}/src/c64/libc64sc.a"
        "${VICE_BUILD_DIR}/src/c64/cart/libc64cartsystem.a"
        "${VICE_BUILD_DIR}/src/c64/cart/libc64cart.a"
        "${VICE_BUILD_DIR}/src/c64/cart/libc64commoncart.a"
        "${VICE_BUILD_DIR}/src/datasette/libdatasette.a"
        "${VICE_BUILD_DIR}/src/drive/iec/libdriveiec.a"
        "${VICE_BUILD_DIR}/src/drive/iecieee/libdriveiecieee.a"
        "${VICE_BUILD_DIR}/src/drive/iec/c64exp/libdriveiecc64exp.a"
        "${VICE_BUILD_DIR}/src/drive/ieee/libdriveieee.a"
        "${VICE_BUILD_DIR}/src/drive/libdrive.a"
        "${VICE_BUILD_DIR}/src/drive/tcbm/libdrivetcbm.a"
        "${VICE_BUILD_DIR}/src/lib/p64/libp64.a"
        "${VICE_BUILD_DIR}/src/iecbus/libiecbus.a"
        "${VICE_BUILD_DIR}/src/parallel/libparallel.a"
        "${VICE_BUILD_DIR}/src/vdrive/libvdrive.a"
        "${VICE_BUILD_DIR}/src/sid/libsid.a"
        "${VICE_BUILD_DIR}/src/resid/libresid.a"
        "${VICE_BUILD_DIR}/src/resid-dtv/libresiddtv.a"
        "${VICE_BUILD_DIR}/src/monitor/libmonitor.a"
        "${VICE_BUILD_DIR}/src/joyport/libjoyport.a"
        "${VICE_BUILD_DIR}/src/samplerdrv/libsamplerdrv.a"
        "${VICE_BUILD_DIR}/src/arch/shared/sounddrv/libsounddrv.a"
        "${VICE_BUILD_DIR}/src/arch/shared/mididrv/libmididrv.a"
        "${VICE_BUILD_DIR}/src/arch/shared/socketdrv/libsocketdrv.a"
        "${VICE_BUILD_DIR}/src/arch/shared/hwsiddrv/libhwsiddrv.a"
        "${VICE_BUILD_DIR}/src/gfxoutputdrv/libgfxoutputdrv.a"
        "${VICE_BUILD_DIR}/src/printerdrv/libprinterdrv.a"
        "${VICE_BUILD_DIR}/src/diskimage/libdiskimage.a"
        "${VICE_BUILD_DIR}/src/fsdevice/libfsdevice.a"
        "${VICE_BUILD_DIR}/src/tape/libtape.a"
        "${VICE_BUILD_DIR}/src/fileio/libfileio.a"
        "${VICE_BUILD_DIR}/src/serial/libserial.a"
        "${VICE_BUILD_DIR}/src/core/libcore.a"
        "${VICE_BUILD_DIR}/src/rs232drv/librs232drv.a"
        "${VICE_BUILD_DIR}/src/viciisc/libviciisc.a"
        "${VICE_BUILD_DIR}/src/raster/libraster.a"
        "${VICE_BUILD_DIR}/src/userport/libuserport.a"
        "${VICE_BUILD_DIR}/src/diag/libdiag.a"
        "${VICE_BUILD_DIR}/src/core/rtc/librtc.a"
        "${VICE_BUILD_DIR}/src/video/libvideo.a"
        "${VICE_BUILD_DIR}/src/arch/headless/libarch.a"
        "${VICE_BUILD_DIR}/src/imagecontents/libimagecontents.a"
        "${VICE_BUILD_DIR}/src/c64/libc64scstubs.a"
        "${VICE_BUILD_DIR}/src/hvsc/libhvsc.a"
        "${VICE_BUILD_DIR}/src/lib/libzmbv/libzmbv.a"
        "${VICE_BUILD_DIR}/src/arch/headless/libarch.a"
        "${VICE_BUILD_DIR}/src/arch/shared/libarchdep.a"
        "${VICE_BUILD_DIR}/src/lib/linenoise-ng/liblinenoiseng.a")

set(VICE_AVAILABLE_STATIC_LIBS)
foreach(vice_static_lib ${VICE_STATIC_LIBS})
    if(EXISTS "${vice_static_lib}")
        list(APPEND VICE_AVAILABLE_STATIC_LIBS "${vice_static_lib}")
    endif()
endforeach()

add_library(viceandroid SHARED
        vice_android_bridge.cpp
        ${VICE_BASE_OBJECTS})

target_compile_features(viceandroid PRIVATE cxx_std_17)

target_include_directories(viceandroid PRIVATE
        "${VICE_BUILD_DIR}/src"
        "${VICE_SRC_DIR}"
        "${VICE_SRC_DIR}/arch/headless"
        "${VICE_SRC_DIR}/arch/shared"
        "${VICE_SRC_DIR}/arch"
        "${VICE_SRC_DIR}/c64"
        "${VICE_SRC_DIR}/datasette"
        "${VICE_SRC_DIR}/drive"
        "${VICE_SRC_DIR}/monitor"
        "${VICE_SRC_DIR}/tape"
        "${VICE_SRC_DIR}/tapeport")

target_link_options(viceandroid PRIVATE
        -Wl,--wrap=archdep_init
        -Wl,--wrap=log_init
        -Wl,--wrap=video_init
        -Wl,--wrap=init_main
        -Wl,--wrap=video_canvas_create
        -Wl,--wrap=video_canvas_can_resize
        -Wl,--wrap=video_canvas_resize
        -Wl,--wrap=video_canvas_refresh
        -Wl,--wrap=sound_init_dummy_device
        -Wl,--wrap=ui_display_tape_control_status
        -Wl,--wrap=vsync_do_vsync
        -Wl,--wrap=maincpu_mainloop)

target_link_libraries(viceandroid
        -Wl,--start-group
        ${VICE_AVAILABLE_STATIC_LIBS}
        -Wl,--end-group
        z
        m
        aaudio
        android
        log)
