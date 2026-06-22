#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <android/log.h>
#include <aaudio/AAudio.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstring>
#include <condition_variable>
#include <cstdlib>
#include <mutex>
#include <sys/stat.h>
#include <string>
#include <thread>
#include <vector>

extern "C" {
#include "autostart.h"
#include "joyport/joystick.h"
#include "joyport/joyport.h"
#include "kbdbuf.h"
#include "keyboard.h"
#include "machine.h"
#include "palette.h"
#include "resources.h"
#include "sound.h"
#include "video.h"
#include "videoarch.h"
#include "vsync.h"
}

extern "C" int main_program(int argc, char** argv);
extern "C" void datasette_control(int port, int command);
extern "C" int tape_image_attach(unsigned int unit, const char* name);

namespace {

constexpr const char* kTag = "VICEAndroidBridge";
constexpr int32_t kAudioRingMillis = 500;
constexpr int32_t kAudioPrebufferMillis = 80;
std::mutex g_mutex;
std::mutex g_pause_mutex;
std::condition_variable g_pause_cv;
std::string g_data_dir;
ANativeWindow* g_window = nullptr;
int g_joystick_port = 2;
int g_joystick_mask = 0;
std::atomic_bool g_core_started{false};
std::atomic_bool g_core_running{false};
std::atomic_int g_refresh_log_count{0};
std::atomic_int g_aspect_mode{0};
std::atomic_bool g_crt_enabled{false};
std::atomic_bool g_bezel_enabled{false};
std::atomic_bool g_emulation_paused{false};
std::atomic_bool g_pause_gate_active{false};
std::atomic_bool g_tape_turbo_enabled{false};
std::vector<uint32_t> g_frame_buffer;
const palette_t* g_last_palette = nullptr;
int g_frame_width = 0;
int g_frame_height = 0;
int g_surface_buffer_width = 0;
int g_surface_buffer_height = 0;
int g_surface_view_width = 0;
int g_surface_view_height = 0;
AAudioStream* g_audio_stream = nullptr;
int g_audio_channels = 1;
int32_t g_audio_buffer_size_frames = 0;
int32_t g_audio_last_xrun_count = 0;
bool g_audio_started = false;
std::vector<int16_t> g_audio_ring;
std::atomic<uint64_t> g_audio_read_frame{0};
std::atomic<uint64_t> g_audio_write_frame{0};
std::atomic<int32_t> g_audio_ring_capacity_frames{0};
std::atomic<int32_t> g_audio_prebuffer_frames{0};
std::atomic_bool g_audio_prefilled{false};
int16_t g_audio_last_output[2] = {0, 0};
std::atomic_int g_audio_write_log_count{0};
std::atomic_int g_audio_drop_log_count{0};
std::atomic_int g_audio_xrun_log_count{0};
std::atomic_int g_audio_callback_log_count{0};
std::atomic_int g_last_visible_source_x{0};
std::atomic_int g_last_visible_source_y{0};
std::atomic<uint64_t> g_fps_window_start_ns{0};
std::atomic_int g_fps_window_frames{0};
std::atomic_int g_current_fps{0};

struct MatrixKey {
    int row;
    int column;
};

uint16_t vice_joystick_mask(int mask) {
    uint16_t out = 0;
    if ((mask & 0x01) != 0) {
        out |= JOYPORT_UP;
    }
    if ((mask & 0x02) != 0) {
        out |= JOYPORT_DOWN;
    }
    if ((mask & 0x04) != 0) {
        out |= JOYPORT_LEFT;
    }
    if ((mask & 0x08) != 0) {
        out |= JOYPORT_RIGHT;
    }
    if ((mask & 0x10) != 0) {
        out |= JOYPORT_FIRE_1;
    }
    if ((mask & 0x20) != 0) {
        out |= JOYPORT_FIRE_2;
    }
    return out;
}

unsigned int vice_joystick_port(int port) {
    return port <= 1 ? JOYPORT_1 : JOYPORT_2;
}

bool c64_matrix_key(int key, MatrixKey& out) {
    switch (key) {
        case 0:
            out = {7, 4};
            return true;
        case 1:
            out = {7, 7};
            return true;
        case 2:
            out = {0, 1};
            return true;
        case 3:
            out = {0, 4};
            return true;
        case 4:
            out = {0, 5};
            return true;
        case 5:
            out = {0, 6};
            return true;
        case 6:
            out = {0, 3};
            return true;
        default:
            return false;
    }
}

bool command_requests_autoload(const std::string& command) {
    return command.find("-autoload") != std::string::npos;
}

int command_prg_mode(const std::string& command) {
    return 1;
}

std::string command_option_value(const std::string& command, const std::string& option) {
    const size_t option_pos = command.find(option);
    if (option_pos == std::string::npos) {
        return {};
    }
    size_t pos = option_pos + option.size();
    while (pos < command.size() && command[pos] == ' ') {
        pos++;
    }
    if (pos >= command.size()) {
        return {};
    }
    if (command[pos] == '"') {
        const size_t end = command.find('"', pos + 1);
        if (end == std::string::npos) {
            return {};
        }
        return command.substr(pos + 1, end - pos - 1);
    }
    const size_t end = command.find(' ', pos);
    return command.substr(pos, end == std::string::npos ? std::string::npos : end - pos);
}

std::string to_string(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

void release_window_locked() {
    if (g_window != nullptr) {
        ANativeWindow_release(g_window);
        g_window = nullptr;
    }
    g_surface_view_width = 0;
    g_surface_view_height = 0;
    g_surface_buffer_width = 0;
    g_surface_buffer_height = 0;
}

void ensure_dir(const std::string& path) {
    if (path.empty()) {
        return;
    }
    if (mkdir(path.c_str(), 0700) != 0 && errno != EEXIST) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Could not create %s: errno=%d",
                            path.c_str(), errno);
    }
}

void prepare_vice_environment() {
    if (g_data_dir.empty()) {
        return;
    }
    ensure_dir(g_data_dir);
    const std::string cache = g_data_dir + "/cache";
    const std::string config = g_data_dir + "/config";
    const std::string data = g_data_dir + "/data";
    const std::string state = g_data_dir + "/state";
    ensure_dir(cache);
    ensure_dir(config);
    ensure_dir(data);
    ensure_dir(state);
    setenv("HOME", g_data_dir.c_str(), 1);
    setenv("XDG_CACHE_HOME", cache.c_str(), 1);
    setenv("XDG_CONFIG_HOME", config.c_str(), 1);
    setenv("XDG_DATA_HOME", data.c_str(), 1);
    setenv("XDG_STATE_HOME", state.c_str(), 1);
}

void update_canvas_palette(video_canvas_t* canvas) {
    if (canvas == nullptr || canvas->palette == nullptr || canvas->videoconfig == nullptr ||
        canvas->palette->entries == nullptr) {
        return;
    }

    for (unsigned int i = 0; i < canvas->palette->num_entries; i++) {
        const palette_entry_t& color = canvas->palette->entries[i];
        const uint32_t color_code = color.red | (color.green << 8) | (color.blue << 16) | (0xffU << 24);
        video_render_setphysicalcolor(canvas->videoconfig, static_cast<int>(i), color_code, 32);
    }

#ifdef WORDS_BIGENDIAN
    for (int i = 0; i < 256; i++) {
        video_render_setrawrgb(&canvas->videoconfig->color_tables, i, i << 24, i << 16, i << 8);
    }
    video_render_setrawalpha(&canvas->videoconfig->color_tables, 0xffU);
#else
    for (int i = 0; i < 256; i++) {
        video_render_setrawrgb(&canvas->videoconfig->color_tables, i, i, i << 8, i << 16);
    }
    video_render_setrawalpha(&canvas->videoconfig->color_tables, 0xffU << 24);
#endif
    video_render_initraw(canvas->videoconfig);
    g_last_palette = canvas->palette;
}

std::vector<std::string> build_vice_args(const std::string& media, int media_type,
                                         const std::string& command_line) {
    std::vector<std::string> args;
    args.emplace_back("x64sc");
    args.emplace_back("-default");
    args.emplace_back("-verbose");
    args.emplace_back("+autostart-delay-random");
    args.emplace_back("-soundrate");
    args.emplace_back("48000");
    args.emplace_back("-soundbufsize");
    args.emplace_back("100");
    args.emplace_back("-soundfragsize");
    args.emplace_back("3");
    args.emplace_back("-soundoutput");
    args.emplace_back("2");
    args.emplace_back("-sidengine");
    args.emplace_back("0");
    args.emplace_back("-datasettesound");
    if (!g_data_dir.empty()) {
        args.emplace_back("-directory");
        args.emplace_back(g_data_dir);
        args.emplace_back("-logfile");
        args.emplace_back(g_data_dir + "/vice-start.log");
    }
    const std::string keybuf = command_option_value(command_line, "-keybuf");
    if (!keybuf.empty()) {
        args.emplace_back("-keybuf");
        args.emplace_back(keybuf);
    }

    switch (media_type) {
        case 0:
            args.emplace_back("-autostartprgmode");
            args.emplace_back(std::to_string(command_prg_mode(command_line)));
            args.emplace_back(command_requests_autoload(command_line) ? "-autoload" : "-autostart");
            args.emplace_back(media);
            break;
        case 1:
            args.emplace_back("-8");
            args.emplace_back(media);
            args.emplace_back("-autostart");
            args.emplace_back(media);
            break;
        case 2:
            if (command_line.find("-tapebasicload") != std::string::npos) {
                args.emplace_back("-tapebasicload");
                args.emplace_back("-autostart");
                args.emplace_back(media);
            } else {
                args.emplace_back("-1");
                args.emplace_back(media);
            }
            break;
        case 3:
            args.emplace_back("-cartcrt");
            args.emplace_back(media);
            break;
        default:
            args.emplace_back("-autostart");
            args.emplace_back(media);
            break;
    }
    return args;
}

void start_core_thread(std::vector<std::string> args) {
    std::thread([args = std::move(args)]() mutable {
        prepare_vice_environment();
        std::vector<char*> argv;
        argv.reserve(args.size() + 1);
        for (std::string& arg : args) {
            argv.push_back(arg.data());
        }
        argv.push_back(nullptr);
        g_core_running = true;
        __android_log_print(ANDROID_LOG_INFO, kTag, "Starting VICE main_program argc=%zu", args.size());
        int result = main_program(static_cast<int>(args.size()), argv.data());
        g_core_running = false;
        __android_log_print(ANDROID_LOG_ERROR, kTag, "VICE main_program returned %d", result);
    }).detach();
}

void wait_while_emulation_paused() {
    if (!g_emulation_paused.load(std::memory_order_acquire)) {
        return;
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE pause gate entered");
    sound_suspend();
    vsync_suspend_speed_eval();

    {
        std::unique_lock<std::mutex> lock(g_pause_mutex);
        g_pause_gate_active = true;
        g_pause_cv.notify_all();
        g_pause_cv.wait(lock, [] {
            return !g_emulation_paused.load(std::memory_order_acquire);
        });
        g_pause_gate_active = false;
    }

    sound_resume();
    vsync_suspend_speed_eval();
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE pause gate resumed");
}

bool tape_attach_only_request(int media_type, const std::string& command) {
    return media_type == 2 && command.find("-tapebasicload") == std::string::npos;
}

void schedule_keybuf_after_running_autoload(const std::string& keybuf) {
    if (keybuf.empty()) {
        return;
    }
    std::thread([keybuf] {
        std::this_thread::sleep_for(std::chrono::seconds(12));
        if (!g_core_running.load() || g_emulation_paused.load()) {
            return;
        }
        const int result = kbdbuf_feed_string(keybuf.c_str());
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "Delayed keybuf feed len=%zu result=%d",
                            keybuf.size(), result);
    }).detach();
}

int autostart_running_core_media(const std::string& media, int media_type,
                                 const std::string& command) {
    autostart_disable();
    if (media_type == 0) {
        resources_set_int("AutostartPrgMode", command_prg_mode(command));
    }
    if (media_type == 2) {
        autostart_tape_basic_load = command.find("-tapebasicload") != std::string::npos ? 1 : 0;
    }
    const int run_mode = command_requests_autoload(command)
            ? AUTOSTART_MODE_LOAD
            : AUTOSTART_MODE_RUN;
    const int result = autostart_autodetect(media.c_str(), nullptr, 0, run_mode);
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Autostart reboot result=%d type=%d runmode=%d media=%s",
                        result, media_type, run_mode, media.c_str());
    return result;
}

void reset_audio_ring() {
    g_audio_read_frame.store(0, std::memory_order_release);
    g_audio_write_frame.store(0, std::memory_order_release);
    g_audio_prefilled.store(false, std::memory_order_release);
    g_audio_last_output[0] = 0;
    g_audio_last_output[1] = 0;
}

int32_t audio_ring_available_frames() {
    const uint64_t read = g_audio_read_frame.load(std::memory_order_acquire);
    const uint64_t write = g_audio_write_frame.load(std::memory_order_acquire);
    if (write <= read) {
        return 0;
    }
    const uint64_t available = write - read;
    const int32_t capacity = g_audio_ring_capacity_frames.load(std::memory_order_acquire);
    return static_cast<int32_t>(std::min<uint64_t>(available, std::max(0, capacity)));
}

void push_audio_frames(const int16_t* input, int32_t frames) {
    const int32_t capacity = g_audio_ring_capacity_frames.load(std::memory_order_acquire);
    const int channels = g_audio_channels;
    if (input == nullptr || frames <= 0 || capacity <= 0 || channels <= 0 || g_audio_ring.empty()) {
        return;
    }

    if (frames > capacity) {
        input += static_cast<size_t>(frames - capacity) * static_cast<size_t>(channels);
        frames = capacity;
    }

    const uint64_t read = g_audio_read_frame.load(std::memory_order_acquire);
    const uint64_t write = g_audio_write_frame.load(std::memory_order_relaxed);
    const uint64_t available = write > read ? write - read : 0;
    if (available + static_cast<uint64_t>(frames) > static_cast<uint64_t>(capacity)) {
        const uint64_t keep = static_cast<uint64_t>(capacity - frames);
        g_audio_read_frame.store(write > keep ? write - keep : write, std::memory_order_release);
        int drop_log = g_audio_drop_log_count.fetch_add(1);
        if (drop_log < 8) {
            __android_log_print(ANDROID_LOG_WARN, kTag,
                                "AAudio ring full; dropping old frames available=%llu incoming=%d capacity=%d",
                                static_cast<unsigned long long>(available), frames, capacity);
        }
    }

    int32_t frames_remaining = frames;
    uint64_t write_cursor = write;
    const int16_t* source = input;
    while (frames_remaining > 0) {
        const int32_t ring_frame = static_cast<int32_t>(write_cursor % static_cast<uint64_t>(capacity));
        const int32_t chunk_frames = std::min(frames_remaining, capacity - ring_frame);
        std::memcpy(g_audio_ring.data() + static_cast<size_t>(ring_frame) * channels,
                    source,
                    static_cast<size_t>(chunk_frames) * static_cast<size_t>(channels) * sizeof(int16_t));
        frames_remaining -= chunk_frames;
        write_cursor += chunk_frames;
        source += static_cast<size_t>(chunk_frames) * static_cast<size_t>(channels);
    }
    g_audio_write_frame.store(write + static_cast<uint64_t>(frames), std::memory_order_release);
}

void fill_audio_from_last_sample(int16_t* output, int32_t frames, int channels) {
    if (output == nullptr || frames <= 0 || channels <= 0) {
        return;
    }
    const int16_t left = g_audio_last_output[0];
    const int16_t right = channels > 1 ? g_audio_last_output[1] : left;
    for (int32_t frame = 0; frame < frames; frame++) {
        const int32_t scale = frames - frame;
        output[static_cast<size_t>(frame) * channels] =
                static_cast<int16_t>((static_cast<int32_t>(left) * scale) / frames);
        if (channels > 1) {
            output[static_cast<size_t>(frame) * channels + 1] =
                    static_cast<int16_t>((static_cast<int32_t>(right) * scale) / frames);
        }
    }
    g_audio_last_output[0] = 0;
    g_audio_last_output[1] = 0;
}

aaudio_data_callback_result_t android_audio_data_callback(
        AAudioStream*, void*, void* audio_data, int32_t num_frames) {
    int16_t* output = static_cast<int16_t*>(audio_data);
    const int channels = g_audio_channels;
    const int32_t capacity = g_audio_ring_capacity_frames.load(std::memory_order_acquire);
    if (output == nullptr || num_frames <= 0 || channels <= 0 || capacity <= 0 ||
        g_audio_ring.empty()) {
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    int32_t available = audio_ring_available_frames();
    const int32_t configured_prebuffer = g_audio_prebuffer_frames.load(std::memory_order_acquire);
    const int32_t prebuffer = std::min<int32_t>(
            capacity / 3,
            std::max<int32_t>(1024, configured_prebuffer > 0 ? configured_prebuffer : 2048));
    if (!g_audio_prefilled.load(std::memory_order_acquire) && available < prebuffer) {
        fill_audio_from_last_sample(output, num_frames, channels);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    g_audio_prefilled.store(true, std::memory_order_release);

    uint64_t read = g_audio_read_frame.load(std::memory_order_relaxed);
    const uint64_t write = g_audio_write_frame.load(std::memory_order_acquire);
    if (write < read) {
        read = write;
        g_audio_read_frame.store(read, std::memory_order_release);
        available = 0;
    } else {
        available = static_cast<int32_t>(
                std::min<uint64_t>(write - read, static_cast<uint64_t>(capacity)));
    }

    const int32_t frames_to_read = std::min(num_frames, available);
    int32_t frames_remaining = frames_to_read;
    int16_t* dest = output;
    uint64_t read_cursor = read;
    while (frames_remaining > 0) {
        const int32_t ring_frame = static_cast<int32_t>(read_cursor % static_cast<uint64_t>(capacity));
        const int32_t chunk_frames = std::min(frames_remaining, capacity - ring_frame);
        std::memcpy(dest,
                    g_audio_ring.data() + static_cast<size_t>(ring_frame) * channels,
                    static_cast<size_t>(chunk_frames) * static_cast<size_t>(channels) * sizeof(int16_t));
        frames_remaining -= chunk_frames;
        read_cursor += chunk_frames;
        dest += static_cast<size_t>(chunk_frames) * static_cast<size_t>(channels);
    }

    if (frames_to_read > 0) {
        const int16_t* last = output + static_cast<size_t>(frames_to_read - 1) * channels;
        g_audio_last_output[0] = last[0];
        g_audio_last_output[1] = channels > 1 ? last[1] : last[0];
    }

    if (frames_to_read < num_frames) {
        fill_audio_from_last_sample(dest, num_frames - frames_to_read, channels);
        int xrun_log = g_audio_xrun_log_count.fetch_add(1);
        if (xrun_log < 8) {
            __android_log_print(ANDROID_LOG_WARN, kTag,
                                "AAudio callback underrun read=%d requested=%d available=%d",
                                frames_to_read, num_frames, available);
        } else if (xrun_log == 8) {
            __android_log_print(ANDROID_LOG_WARN, kTag,
                                "AAudio callback underrun logging suppressed");
        }
    }
    g_audio_read_frame.store(read + static_cast<uint64_t>(frames_to_read), std::memory_order_release);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

}

extern "C" int sound_register_device(const sound_device_t* pdevice);
extern "C" int android_sound_bufferspace(void);

extern "C" int android_sound_init(const char*, int* speed, int* fragsize, int* fragnr, int* channels) {
    if (g_audio_stream != nullptr) {
        AAudioStream_requestStop(g_audio_stream);
        AAudioStream_close(g_audio_stream);
        g_audio_stream = nullptr;
    }
    g_audio_buffer_size_frames = 0;
    g_audio_last_xrun_count = 0;
    g_audio_started = false;
    g_audio_write_log_count = 0;
    g_audio_drop_log_count = 0;
    g_audio_xrun_log_count = 0;
    g_audio_callback_log_count = 0;
    g_audio_ring_capacity_frames = 0;
    g_audio_prebuffer_frames = 0;
    g_audio_ring.clear();
    reset_audio_ring();

    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK || builder == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "AAudio builder creation failed");
        return 1;
    }

    const int requested_channels = channels != nullptr && *channels > 1 ? 2 : 1;
    const int requested_rate = speed != nullptr ? *speed : 48000;
    const int32_t requested_capacity = std::max<int32_t>(8192, requested_rate * 240 / 1000);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setSampleRate(builder, requested_rate);
    AAudioStreamBuilder_setChannelCount(builder, requested_channels);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_NONE);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, requested_capacity);
    AAudioStreamBuilder_setFramesPerDataCallback(builder, 512);
    AAudioStreamBuilder_setDataCallback(builder, android_audio_data_callback, nullptr);

    aaudio_result_t open_result = AAudioStreamBuilder_openStream(builder, &g_audio_stream);
    AAudioStreamBuilder_delete(builder);
    if (open_result != AAUDIO_OK || g_audio_stream == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "AAudio stream open failed: %s",
                            AAudio_convertResultToText(open_result));
        return 1;
    }

    g_audio_channels = AAudioStream_getChannelCount(g_audio_stream);
    if (channels != nullptr) {
        *channels = g_audio_channels;
    }
    if (speed != nullptr) {
        *speed = AAudioStream_getSampleRate(g_audio_stream);
    }

    const int32_t capacity = AAudioStream_getBufferCapacityInFrames(g_audio_stream);
    const int32_t burst = std::max<int32_t>(1, AAudioStream_getFramesPerBurst(g_audio_stream));
    const int32_t stream_rate = std::max<int32_t>(8000, AAudioStream_getSampleRate(g_audio_stream));
    int32_t target_buffer = std::max<int32_t>(stream_rate * 120 / 1000, burst * 8);
    if (fragsize != nullptr && fragnr != nullptr && *fragsize > 0 && *fragnr > 0) {
        target_buffer = std::max<int32_t>(target_buffer, *fragsize * *fragnr);
    }
    if (capacity > 0) {
        target_buffer = std::min<int32_t>(capacity, target_buffer);
    }
    const int32_t actual_buffer = AAudioStream_setBufferSizeInFrames(g_audio_stream, target_buffer);
    if (actual_buffer > 0) {
        g_audio_buffer_size_frames = actual_buffer;
    } else {
        g_audio_buffer_size_frames = AAudioStream_getBufferSizeInFrames(g_audio_stream);
        if (g_audio_buffer_size_frames <= 0) {
            g_audio_buffer_size_frames = capacity;
        }
    }

    const int32_t ring_capacity = std::max<int32_t>(stream_rate * kAudioRingMillis / 1000,
                                                   std::max<int32_t>(burst * 16, 8192));
    g_audio_ring.assign(static_cast<size_t>(ring_capacity) * static_cast<size_t>(g_audio_channels), 0);
    g_audio_ring_capacity_frames.store(ring_capacity, std::memory_order_release);
    g_audio_prebuffer_frames.store(std::max<int32_t>(2048, stream_rate * kAudioPrebufferMillis / 1000),
                                   std::memory_order_release);
    reset_audio_ring();

    aaudio_result_t start_result = AAudioStream_requestStart(g_audio_stream);
    if (start_result != AAUDIO_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "AAudio stream start failed: %s",
                            AAudio_convertResultToText(start_result));
        AAudioStream_close(g_audio_stream);
        g_audio_stream = nullptr;
        g_audio_ring.clear();
        g_audio_ring_capacity_frames = 0;
        g_audio_prebuffer_frames = 0;
        return 1;
    }
    g_audio_started = true;

    __android_log_print(ANDROID_LOG_INFO, kTag, "AAudio opened rate=%d channels=%d buffer=%d capacity=%d burst=%d ring=%d requested_capacity=%d",
                        AAudioStream_getSampleRate(g_audio_stream), g_audio_channels,
                        g_audio_buffer_size_frames, capacity, burst, ring_capacity, requested_capacity);
    return 0;
}

extern "C" int android_sound_write(int16_t* pbuf, size_t nr) {
    if (g_audio_stream == nullptr || pbuf == nullptr || g_audio_channels <= 0) {
        return 0;
    }
    const int32_t frames = static_cast<int32_t>(nr / static_cast<size_t>(g_audio_channels));
    if (frames <= 0) {
        return 0;
    }
    int refresh_log = g_audio_write_log_count.fetch_add(1);
    if (refresh_log < 4) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "AAudio enqueue #%d frames=%d ring=%d queued=%d",
                            refresh_log + 1, frames,
                            g_audio_ring_capacity_frames.load(std::memory_order_acquire),
                            audio_ring_available_frames());
    }
    push_audio_frames(pbuf, frames);
    return 0;
}

extern "C" int android_sound_bufferspace(void) {
    if (g_audio_stream == nullptr) {
        return 0;
    }
    int32_t buffer_size = g_audio_buffer_size_frames;
    if (buffer_size <= 0) {
        buffer_size = AAudioStream_getBufferCapacityInFrames(g_audio_stream);
    }
    if (buffer_size <= 0) {
        return 0;
    }
    int64_t queued = AAudioStream_getFramesWritten(g_audio_stream)
            - AAudioStream_getFramesRead(g_audio_stream);
    if (queued < 0) {
        queued = 0;
    }
    const int64_t available = static_cast<int64_t>(buffer_size) - queued;
    if (available <= 0) {
        return 0;
    }
    return static_cast<int>(std::min<int64_t>(available, buffer_size));
}

extern "C" void android_sound_close(void) {
    if (g_audio_stream != nullptr) {
        AAudioStream_requestStop(g_audio_stream);
        AAudioStream_close(g_audio_stream);
        g_audio_stream = nullptr;
        g_audio_buffer_size_frames = 0;
        g_audio_started = false;
    }
    reset_audio_ring();
    g_audio_ring.clear();
    g_audio_ring_capacity_frames = 0;
    g_audio_prebuffer_frames = 0;
}

extern "C" int android_sound_suspend(void) {
    if (g_audio_stream == nullptr || !g_audio_started) {
        return 0;
    }
    const int result = AAudioStream_requestPause(g_audio_stream) == AAUDIO_OK ? 0 : 1;
    if (result == 0) {
        g_audio_started = false;
        reset_audio_ring();
    }
    return result;
}

extern "C" int android_sound_resume(void) {
    if (g_audio_stream == nullptr || g_audio_started) {
        return 0;
    }
    reset_audio_ring();
    AAudioStream_requestFlush(g_audio_stream);
    const int result = AAudioStream_requestStart(g_audio_stream) == AAUDIO_OK ? 0 : 1;
    if (result == 0) {
        g_audio_started = true;
    }
    return result;
}

static const sound_device_t android_sound_device = {
        "dummy",
        android_sound_init,
        android_sound_write,
        nullptr,
        nullptr,
        nullptr,
        android_sound_close,
        android_sound_suspend,
        android_sound_resume,
        0,
        2,
        false
};

extern "C" int __wrap_sound_init_dummy_device(void) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "Registering Android AAudio sound device");
    return sound_register_device(&android_sound_device);
}

void blit_canvas_to_window(struct video_canvas_s* canvas,
                           unsigned int xs, unsigned int ys,
                           unsigned int xi, unsigned int yi,
                           unsigned int w, unsigned int h,
                           bool log_refresh) {
    if (canvas == nullptr || canvas->draw_buffer == nullptr || canvas->viewport == nullptr ||
        canvas->videoconfig == nullptr || w == 0 || h == 0) {
        return;
    }

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_window == nullptr) {
        return;
    }

    update_canvas_palette(canvas);

    const int render_scale_x = std::max(1, canvas->videoconfig->scalex);
    const int render_scale_y = std::max(1, canvas->videoconfig->scaley);
    int frame_width = static_cast<int>(canvas->draw_buffer->visible_width);
    int frame_height = static_cast<int>(canvas->draw_buffer->visible_height);
    if (frame_width <= 0) {
        frame_width = static_cast<int>(canvas->draw_buffer->canvas_width);
    }
    if (frame_height <= 0) {
        frame_height = static_cast<int>(canvas->draw_buffer->canvas_height);
    }
    if (frame_width <= 0) {
        frame_width = static_cast<int>(xi + w);
    }
    if (frame_height <= 0) {
        frame_height = static_cast<int>(yi + h);
    }
    const int target_x = static_cast<int>(xi);
    const int target_y = static_cast<int>(yi);
    const int target_update_width = static_cast<int>(w);
    const int target_update_height = static_cast<int>(h);
    if (frame_width <= 0 || frame_height <= 0 ||
        target_x >= frame_width || target_y >= frame_height) {
        return;
    }

    const int update_width = std::min(target_update_width, frame_width - target_x);
    const int update_height = std::min(target_update_height, frame_height - target_y);
    if (update_width <= 0 || update_height <= 0) {
        return;
    }

    if (log_refresh) {
        g_last_visible_source_x = static_cast<int>(xs);
        g_last_visible_source_y = static_cast<int>(ys);
    }

    int refresh_log = log_refresh ? g_refresh_log_count.fetch_add(1) : g_refresh_log_count.load();
    if (log_refresh && refresh_log < 8) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "VICE refresh #%d xs=%u ys=%u xi=%u yi=%u w=%u h=%u scale=%dx%d frame=%dx%d draw=%ux%u pitch=%u",
                            refresh_log + 1, xs, ys, xi, yi, w, h,
                            render_scale_x, render_scale_y,
                            frame_width, frame_height,
                            canvas->draw_buffer->draw_buffer_width,
                            canvas->draw_buffer->draw_buffer_height,
                            canvas->draw_buffer->draw_buffer_pitch);
    }

    const size_t required_pixels = static_cast<size_t>(frame_width) * static_cast<size_t>(frame_height);
    if (g_frame_width != frame_width || g_frame_height != frame_height ||
        g_frame_buffer.size() < required_pixels) {
        g_frame_width = frame_width;
        g_frame_height = frame_height;
        g_frame_buffer.assign(required_pixels, 0xff000000U);
    }

    const uint8_t* source = canvas->draw_buffer->draw_buffer;
    const int source_pitch = static_cast<int>(canvas->draw_buffer->draw_buffer_pitch != 0
            ? canvas->draw_buffer->draw_buffer_pitch
            : canvas->draw_buffer->draw_buffer_width);
    const int source_height = static_cast<int>(canvas->draw_buffer->draw_buffer_height);
    if (source == nullptr || source_pitch <= 0 || source_height <= 0) {
        return;
    }
    const uint32_t* colors = canvas->videoconfig->color_tables.physical_colors;
    if (log_refresh && refresh_log < 8) {
        const int sample_y = std::min(source_height - 1, std::max(0, static_cast<int>(ys)));
        const int sample_x = std::min(source_pitch - 1, std::max(0, static_cast<int>(xs)));
        const uint8_t sample = source[static_cast<size_t>(sample_y) * source_pitch + sample_x];
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "VICE sample #%d src[%d,%d]=%u color=%08x c0=%08x c6=%08x c14=%08x",
                            refresh_log + 1, sample_x, sample_y, sample,
                            colors[sample], colors[0], colors[6], colors[14]);
    }
    for (int y = 0; y < update_height; y++) {
        const int source_y = static_cast<int>(ys) + y;
        const int dest_y = target_y + y;
        if (source_y < 0 || source_y >= source_height || dest_y < 0 || dest_y >= frame_height) {
            continue;
        }
        const uint8_t* source_row = source + static_cast<size_t>(source_y) * source_pitch;
        uint32_t* dest_row = g_frame_buffer.data() + static_cast<size_t>(dest_y) * frame_width;
        for (int x = 0; x < update_width; x++) {
            const int source_x = static_cast<int>(xs) + x;
            const int dest_x = target_x + x;
            if (source_x < 0 || source_x >= source_pitch || dest_x < 0 || dest_x >= frame_width) {
                continue;
            }
            dest_row[dest_x] = colors[source_row[source_x]];
        }
    }

    const int target_width = frame_width;
    const int target_height = frame_height;
    if (g_surface_buffer_width != target_width || g_surface_buffer_height != target_height) {
        ANativeWindow_setBuffersGeometry(g_window, target_width, target_height, WINDOW_FORMAT_RGBA_8888);
        g_surface_buffer_width = target_width;
        g_surface_buffer_height = target_height;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(g_window, &buffer, nullptr) != 0 || buffer.bits == nullptr) {
        return;
    }

    uint8_t* dst = static_cast<uint8_t*>(buffer.bits);
    const uint32_t* src = g_frame_buffer.data();
    const int copy_width = std::min(frame_width, buffer.width);
    const int copy_height = std::min(frame_height, buffer.height);
    for (int y = 0; y < copy_height; y++) {
        std::memcpy(dst + static_cast<size_t>(y) * buffer.stride * 4,
                    src + static_cast<size_t>(y) * frame_width,
                    static_cast<size_t>(copy_width) * sizeof(uint32_t));
    }

    ANativeWindow_unlockAndPost(g_window);

    const uint64_t now_ns = static_cast<uint64_t>(
            std::chrono::duration_cast<std::chrono::nanoseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count());
    uint64_t window_start = g_fps_window_start_ns.load(std::memory_order_acquire);
    if (window_start == 0) {
        g_fps_window_start_ns.store(now_ns, std::memory_order_release);
        g_fps_window_frames.store(0, std::memory_order_release);
    }
    const int frames = g_fps_window_frames.fetch_add(1, std::memory_order_acq_rel) + 1;
    if (window_start != 0 && now_ns - window_start >= 1000000000ULL) {
        const uint64_t elapsed = now_ns - window_start;
        const int fps = static_cast<int>((static_cast<uint64_t>(frames) * 1000000000ULL + elapsed / 2) / elapsed);
        g_current_fps.store(fps, std::memory_order_release);
        g_fps_window_start_ns.store(now_ns, std::memory_order_release);
        g_fps_window_frames.store(0, std::memory_order_release);
    }
}

extern "C" void __wrap_video_canvas_refresh(struct video_canvas_s* canvas,
                                            unsigned int xs, unsigned int ys,
                                            unsigned int xi, unsigned int yi,
                                            unsigned int w, unsigned int h) {
    blit_canvas_to_window(canvas, xs, ys, xi, yi, w, h, true);
}

extern "C" int __real_archdep_init(int* argc, char** argv);
extern "C" int __wrap_archdep_init(int* argc, char** argv) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage archdep_init begin argc=%d", argc ? *argc : -1);
    int result = __real_archdep_init(argc, argv);
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage archdep_init end result=%d argc=%d",
                        result, argc ? *argc : -1);
    return result;
}

extern "C" int __real_log_init(void);
extern "C" int __wrap_log_init(void) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage log_init begin");
    int result = __real_log_init();
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage log_init end result=%d", result);
    return result;
}

extern "C" int __real_video_init(void);
extern "C" int __wrap_video_init(void) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage video_init begin");
    int result = __real_video_init();
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage video_init end result=%d", result);
    return result;
}

extern "C" int __real_init_main(void);
extern "C" int __wrap_init_main(void) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage init_main begin");
    int result = __real_init_main();
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage init_main end result=%d", result);
    return result;
}

extern "C" struct video_canvas_s* __real_video_canvas_create(struct video_canvas_s* canvas,
                                                             unsigned int* width,
                                                             unsigned int* height,
                                                             int mapped);
extern "C" struct video_canvas_s* __wrap_video_canvas_create(struct video_canvas_s* canvas,
                                                             unsigned int* width,
                                                             unsigned int* height,
                                                             int mapped) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage video_canvas_create begin requested=%ux%u",
                        width ? *width : 0, height ? *height : 0);
    struct video_canvas_s* result = __real_video_canvas_create(canvas, width, height, mapped);
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage video_canvas_create end result=%p size=%ux%u",
                        result, width ? *width : 0, height ? *height : 0);
    return result;
}

extern "C" char __wrap_video_canvas_can_resize(struct video_canvas_s* canvas) {
    return 1;
}

extern "C" void __real_video_canvas_resize(struct video_canvas_s* canvas, char resize_canvas);
extern "C" void __wrap_video_canvas_resize(struct video_canvas_s* canvas, char resize_canvas) {
    if (canvas != nullptr && canvas->draw_buffer != nullptr) {
        if (canvas->draw_buffer->canvas_width == 0 && canvas->draw_buffer->visible_width != 0) {
            canvas->draw_buffer->canvas_width = canvas->draw_buffer->visible_width;
        }
        if (canvas->draw_buffer->canvas_height == 0 && canvas->draw_buffer->visible_height != 0) {
            canvas->draw_buffer->canvas_height = canvas->draw_buffer->visible_height;
        }
        if (canvas->draw_buffer->canvas_physical_width == 0 && canvas->draw_buffer->canvas_width != 0) {
            canvas->draw_buffer->canvas_physical_width = canvas->draw_buffer->canvas_width;
        }
        if (canvas->draw_buffer->canvas_physical_height == 0 && canvas->draw_buffer->canvas_height != 0) {
            canvas->draw_buffer->canvas_physical_height = canvas->draw_buffer->canvas_height;
        }
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "VICE stage video_canvas_resize resize=%d canvas=%ux%u physical=%ux%u visible=%ux%u",
                            resize_canvas,
                            canvas->draw_buffer->canvas_width,
                            canvas->draw_buffer->canvas_height,
                            canvas->draw_buffer->canvas_physical_width,
                            canvas->draw_buffer->canvas_physical_height,
                            canvas->draw_buffer->visible_width,
                            canvas->draw_buffer->visible_height);
    }
    __real_video_canvas_resize(canvas, resize_canvas);
}

extern "C" void __real_maincpu_mainloop(void);
extern "C" void __wrap_maincpu_mainloop(void) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VICE stage maincpu_mainloop begin");
    __real_maincpu_mainloop();
    __android_log_print(ANDROID_LOG_ERROR, kTag, "VICE stage maincpu_mainloop returned");
}

extern "C" void __real_ui_display_tape_control_status(int port, int control);
extern "C" void __wrap_ui_display_tape_control_status(int port, int control) {
    if (port == 0) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "Tape control status port=%d control=%d",
                            port, control);
    }
    __real_ui_display_tape_control_status(port, control);
}

extern "C" int vsync_get_warp_mode(void);
extern "C" void vsync_set_warp_mode(int val);
extern "C" void __real_vsync_do_vsync(struct video_canvas_s* canvas);
extern "C" void __wrap_vsync_do_vsync(struct video_canvas_s* canvas) {
    wait_while_emulation_paused();
    if (g_tape_turbo_enabled.load(std::memory_order_acquire)) {
        if (!vsync_get_warp_mode()) {
            vsync_set_warp_mode(1);
            vsync_suspend_speed_eval();
        }
    } else if (vsync_get_warp_mode()) {
        vsync_set_warp_mode(0);
        vsync_suspend_speed_eval();
    }
    __real_vsync_do_vsync(canvas);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeInitialize(JNIEnv* env, jclass, jstring data_dir) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_data_dir = to_string(env, data_dir);
    __android_log_print(ANDROID_LOG_INFO, kTag, "Initialized bridge with data dir: %s", g_data_dir.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeSetSurface(JNIEnv* env, jclass, jobject surface) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_window_locked();
    if (surface != nullptr) {
        g_window = ANativeWindow_fromSurface(env, surface);
        if (g_window != nullptr) {
            g_surface_view_width = ANativeWindow_getWidth(g_window);
            g_surface_view_height = ANativeWindow_getHeight(g_window);
            g_surface_buffer_width = 0;
            g_surface_buffer_height = 0;
            __android_log_print(ANDROID_LOG_INFO, kTag, "Surface attached: %dx%d",
                                g_surface_view_width, g_surface_view_height);
        }
    } else {
        __android_log_print(ANDROID_LOG_INFO, kTag, "Surface detached");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeSetDisplayOptions(
        JNIEnv*, jclass, jint aspect_mode, jboolean crt_enabled, jboolean bezel_enabled) {
    g_aspect_mode = static_cast<int>(aspect_mode);
    g_crt_enabled = crt_enabled == JNI_TRUE;
    g_bezel_enabled = bezel_enabled == JNI_TRUE;
    __android_log_print(ANDROID_LOG_INFO, kTag, "Display options aspect=%d crt=%d bezel=%d",
                        static_cast<int>(aspect_mode),
                        crt_enabled == JNI_TRUE,
                        bezel_enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_viceandroid_c64_ViceNative_nativeGetFps(JNIEnv*, jclass) {
    return g_current_fps.load(std::memory_order_acquire);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeLaunch(
        JNIEnv* env, jclass, jstring media_path, jint media_type, jstring command_line) {
    const std::string media = to_string(env, media_path);
    const std::string command = to_string(env, command_line);
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Launch request type=%d media=%s command=%s",
                        static_cast<int>(media_type), media.c_str(), command.c_str());
    if (!g_core_started.exchange(true)) {
        start_core_thread(build_vice_args(media, static_cast<int>(media_type), command));
        return;
    }
    const int type = static_cast<int>(media_type);
    if (tape_attach_only_request(type, command)) {
        autostart_disable();
        datasette_control(0, 5);
        int attach_result = tape_image_attach(1, media.c_str());
        datasette_control(0, 0);
        __android_log_print(ANDROID_LOG_INFO, kTag, "Tape attach-only result=%d", attach_result);
        return;
    }
    autostart_running_core_media(media, type, command);
    schedule_keybuf_after_running_autoload(command_option_value(command, "-keybuf"));
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeReset(JNIEnv*, jclass) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "Power-cycle reset request running=%d paused=%d",
                        g_core_running.load() ? 1 : 0,
                        g_emulation_paused.load() ? 1 : 0);
    if (g_core_running.load()) {
        machine_trigger_reset(MACHINE_RESET_MODE_POWER_CYCLE);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeSetPaused(JNIEnv*, jclass, jboolean paused) {
    const bool next = paused == JNI_TRUE;
    const bool previous = g_emulation_paused.exchange(next, std::memory_order_acq_rel);
    if (previous == next) {
        return;
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "Pause request=%d gate=%d",
                        next ? 1 : 0, g_pause_gate_active.load() ? 1 : 0);
    if (!next) {
        g_pause_cv.notify_all();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeSetJoystick(JNIEnv*, jclass, jint port, jint mask) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_joystick_port = static_cast<int>(port);
    g_joystick_mask = static_cast<int>(mask);
    const uint16_t vice_mask = vice_joystick_mask(g_joystick_mask);
    joystick_set_value_absolute(vice_joystick_port(g_joystick_port), vice_mask);
    __android_log_print(ANDROID_LOG_VERBOSE, kTag, "Joystick port=%d mask=%d vice=%u",
                        g_joystick_port, g_joystick_mask, vice_mask);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeSetKey(JNIEnv*, jclass, jint key, jboolean pressed) {
    __android_log_print(ANDROID_LOG_VERBOSE, kTag, "Key=%d pressed=%d", static_cast<int>(key), pressed == JNI_TRUE);
    if (!g_core_running.load()) {
        return;
    }
    MatrixKey mapped{};
    if (c64_matrix_key(static_cast<int>(key), mapped)) {
        keyboard_set_keyarr_any(mapped.row, mapped.column, pressed == JNI_TRUE ? 1 : 0);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeSetMatrixKey(
        JNIEnv*, jclass, jint row, jint column, jboolean pressed) {
    __android_log_print(ANDROID_LOG_VERBOSE, kTag, "Matrix key row=%d col=%d pressed=%d",
                        static_cast<int>(row), static_cast<int>(column), pressed == JNI_TRUE);
    if (!g_core_running.load()) {
        return;
    }
    keyboard_set_keyarr_any(static_cast<int>(row), static_cast<int>(column),
                            pressed == JNI_TRUE ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeFeedKeyboard(JNIEnv* env, jclass, jstring text) {
    if (!g_core_running.load()) {
        return;
    }
    const std::string value = to_string(env, text);
    if (value.empty()) {
        return;
    }
    const int result = value == "\r" ? kbdbuf_feed("\r") : kbdbuf_feed_string(value.c_str());
    __android_log_print(ANDROID_LOG_INFO, kTag, "Keyboard feed string len=%zu result=%d",
                        value.size(), result);
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeTapeCommand(JNIEnv*, jclass, jint command) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "Tape command=%d", static_cast<int>(command));
    if (g_core_running) {
        datasette_control(0, static_cast<int>(command));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_viceandroid_c64_ViceNative_nativeSetTapeTurbo(JNIEnv*, jclass, jboolean enabled) {
    const bool next = enabled == JNI_TRUE;
    g_tape_turbo_enabled.store(next, std::memory_order_release);
    if (!next && vsync_get_warp_mode()) {
        vsync_set_warp_mode(0);
        vsync_suspend_speed_eval();
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "Tape turbo=%d", next ? 1 : 0);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_viceandroid_c64_ViceNative_nativeGetViceResource(JNIEnv* env, jclass, jstring name) {
    const std::string resource = to_string(env, name);
    if (resource.empty()) {
        return env->NewStringUTF("");
    }
    int int_value = 0;
    if (resources_get_int(resource.c_str(), &int_value) == 0) {
        const std::string out = resource + " = " + std::to_string(int_value);
        return env->NewStringUTF(out.c_str());
    }
    const char* string_value = nullptr;
    if (resources_get_string(resource.c_str(), &string_value) == 0 && string_value != nullptr) {
        const std::string out = resource + " = " + string_value;
        return env->NewStringUTF(out.c_str());
    }
    const std::string out = resource + " not found";
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_viceandroid_c64_ViceNative_nativeSetViceResourceInt(
        JNIEnv* env, jclass, jstring name, jint value) {
    const std::string resource = to_string(env, name);
    if (resource.empty()) {
        return JNI_FALSE;
    }
    const int result = resources_set_int(resource.c_str(), static_cast<int>(value));
    __android_log_print(ANDROID_LOG_INFO, kTag, "Set resource int %s=%d result=%d",
                        resource.c_str(), static_cast<int>(value), result);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_viceandroid_c64_ViceNative_nativeSetViceResourceString(
        JNIEnv* env, jclass, jstring name, jstring value) {
    const std::string resource = to_string(env, name);
    const std::string string_value = to_string(env, value);
    if (resource.empty()) {
        return JNI_FALSE;
    }
    const int result = resources_set_string(resource.c_str(), string_value.c_str());
    __android_log_print(ANDROID_LOG_INFO, kTag, "Set resource string %s=%s result=%d",
                        resource.c_str(), string_value.c_str(), result);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_viceandroid_c64_ViceNative_nativeHasRealCore(JNIEnv*, jclass) {
    return JNI_TRUE;
}
