package com.viceandroid.c64;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int REQUEST_PICK_GAMES_FOLDER = 2001;
    private static final int REQUEST_IMPORT_MEDIA = 2002;
    private static final int REQUEST_PICK_APP_FOLDER = 2003;
    private static final String PREFS_NAME = "vice_c64_android";
    private static final String PREF_SETUP_COMPLETED = "setup_completed";
    private static final String PREF_APP_FOLDER_URI = "app_folder_uri";
    private static final String PREF_GAMES_FOLDER_URI = "games_folder_uri";
    private static final String PREF_VIEW_STYLE = "view_style";
    private static final String PREF_ASPECT_MODE = "aspect_mode";
    private static final String PREF_CRT_ENABLED = "crt_enabled";
    private static final String PREF_CRT_USER_SET = "crt_user_set";
    private static final String PREF_BEZEL_ENABLED = "bezel_enabled";
    private static final String PREF_BEZEL_GITHUB_URL = "bezel_github_url";
    private static final String PREF_TAPE_AUTOSTART = "tape_autostart";
    private static final String PREF_TAPE_TURBO = "tape_turbo";
    private static final String PREF_LAST_MEDIA_URI = "last_media_uri";
    private static final String PREF_LAST_MEDIA_NAME = "last_media_name";
    private static final String PREF_LAST_MEDIA_TYPE = "last_media_type";
    private static final String PREF_GAME_IGDB_PREFIX = "game_igdb_json_";
    private static final String PREF_GAME_IGDB_CHECKED_PREFIX = "game_igdb_checked_";
    private static final String PREF_CONTROLLER_MODE = "controller_mode";
    private static final String PREF_VIRTUAL_PAD_POSITION = "virtual_pad_position";
    private static final String PREF_VIRTUAL_BUTTON_1_KEY = "virtual_button_1_key";
    private static final String PREF_VIRTUAL_BUTTON_2_KEY = "virtual_button_2_key";
    private static final String PREF_EXTERNAL_GAME_KEY_PREFIX = "external_game_key_";
    private static final int VIEW_GRID = 0;
    private static final int VIEW_CAROUSEL = 1;
    private static final int ASPECT_4_3 = 0;
    private static final int ASPECT_16_9 = 1;
    private static final int CONTROLLER_MODE_VIRTUAL = 0;
    private static final int CONTROLLER_MODE_EXTERNAL = 1;
    private static final int KEY_MAPPING_DEFAULT = -2;
    private static final int KEY_MAPPING_NONE = -1;
    private static final int MAX_CONCURRENT_IGDB_REQUESTS = 3;
    private static final int MAX_IGDB_LOOKUPS_PER_SCAN = 160;
    private static final int MAX_SCAN_DEPTH = 32;
    private static final int JOY_UP = 0x01;
    private static final int JOY_DOWN = 0x02;
    private static final int JOY_LEFT = 0x04;
    private static final int JOY_RIGHT = 0x08;
    private static final int JOY_FIRE = 0x10;
    private static final int JOY_FIRE_2 = 0x20;
    private static final int JOY_DIRECTIONS = JOY_UP | JOY_DOWN | JOY_LEFT | JOY_RIGHT;
    private static final long OVERLAY_AUTO_HIDE_MS = 3000L;
    private static final ResourceChoice[] ON_OFF_CHOICES = new ResourceChoice[] {
            new ResourceChoice("Off", 0),
            new ResourceChoice("On", 1)
    };
    private static final AdvancedResourceOption[] ADVANCED_RESOURCE_OPTIONS = new AdvancedResourceOption[] {
            new AdvancedResourceOption("Autostart warp", "AutostartWarp", ON_OFF_CHOICES),
            new AdvancedResourceOption("Autostart true drive handling", "AutostartHandleTrueDriveEmulation", ON_OFF_CHOICES),
            new AdvancedResourceOption("Drive 8 true emulation", "Drive8TrueEmulation", ON_OFF_CHOICES),
            new AdvancedResourceOption("Drive 8 model", "Drive8Type", new ResourceChoice[] {
                    new ResourceChoice("None", 0),
                    new ResourceChoice("1541", 1541),
                    new ResourceChoice("1541-II", 1542),
                    new ResourceChoice("1571", 1571),
                    new ResourceChoice("1581", 1581)
            }),
            new AdvancedResourceOption("Drive idle method", "Drive8IdleMethod", new ResourceChoice[] {
                    new ResourceChoice("No idle", 0),
                    new ResourceChoice("Skip cycles", 1),
                    new ResourceChoice("Trap idle", 2)
            }),
            new AdvancedResourceOption("Drive sound", "DriveSoundEmulation", ON_OFF_CHOICES),
            new AdvancedResourceOption("Tape sound", "DatasetteSound", ON_OFF_CHOICES),
            new AdvancedResourceOption("Virtual device traps", "VirtualDevices", ON_OFF_CHOICES)
    };
    private static final AdvancedResourceOption[] DISK_DRIVE_OPTIONS = new AdvancedResourceOption[] {
            new AdvancedResourceOption("Drive 8 true emulation", "Drive8TrueEmulation", ON_OFF_CHOICES),
            new AdvancedResourceOption("Drive 8 model", "Drive8Type", new ResourceChoice[] {
                    new ResourceChoice("1541", 1541),
                    new ResourceChoice("1541-II", 1542),
                    new ResourceChoice("1571", 1571),
                    new ResourceChoice("1581", 1581)
            }),
            new AdvancedResourceOption("Drive idle method", "Drive8IdleMethod", new ResourceChoice[] {
                    new ResourceChoice("No idle", 0),
                    new ResourceChoice("Skip cycles", 1),
                    new ResourceChoice("Trap idle", 2)
            }),
            new AdvancedResourceOption("Drive sound", "DriveSoundEmulation", ON_OFF_CHOICES),
            new AdvancedResourceOption("Virtual device traps", "VirtualDevices", ON_OFF_CHOICES)
    };
    private static final ControllerKeyChoice[] VIRTUAL_BUTTON_KEY_CHOICES = new ControllerKeyChoice[] {
            new ControllerKeyChoice("Joystick/default", KEY_MAPPING_DEFAULT),
            new ControllerKeyChoice("Space", C64Native.C64Key.SPACE.ordinal()),
            new ControllerKeyChoice("Run/Stop", C64Native.C64Key.RUN_STOP.ordinal()),
            new ControllerKeyChoice("Return", C64Native.C64Key.RETURN.ordinal()),
            new ControllerKeyChoice("F1", C64Native.C64Key.F1.ordinal()),
            new ControllerKeyChoice("F3", C64Native.C64Key.F3.ordinal()),
            new ControllerKeyChoice("F5", C64Native.C64Key.F5.ordinal()),
            new ControllerKeyChoice("F7", C64Native.C64Key.F7.ordinal())
    };
    private static final ControllerKeyChoice[] EXTERNAL_EXTRA_KEY_CHOICES = new ControllerKeyChoice[] {
            new ControllerKeyChoice("No key", KEY_MAPPING_NONE),
            new ControllerKeyChoice("Space", C64Native.C64Key.SPACE.ordinal()),
            new ControllerKeyChoice("Run/Stop", C64Native.C64Key.RUN_STOP.ordinal()),
            new ControllerKeyChoice("Return", C64Native.C64Key.RETURN.ordinal()),
            new ControllerKeyChoice("F1", C64Native.C64Key.F1.ordinal()),
            new ControllerKeyChoice("F3", C64Native.C64Key.F3.ordinal()),
            new ControllerKeyChoice("F5", C64Native.C64Key.F5.ordinal()),
            new ControllerKeyChoice("F7", C64Native.C64Key.F7.ordinal())
    };
    private static final ControllerButtonMapping[] EXTERNAL_EXTRA_BUTTONS = new ControllerButtonMapping[] {
            new ControllerButtonMapping("X", KeyEvent.KEYCODE_BUTTON_X),
            new ControllerButtonMapping("Y", KeyEvent.KEYCODE_BUTTON_Y),
            new ControllerButtonMapping("LB", KeyEvent.KEYCODE_BUTTON_L1),
            new ControllerButtonMapping("RB", KeyEvent.KEYCODE_BUTTON_R1),
            new ControllerButtonMapping("L3", KeyEvent.KEYCODE_BUTTON_THUMBL),
            new ControllerButtonMapping("R3", KeyEvent.KEYCODE_BUTTON_THUMBR),
            new ControllerButtonMapping("Start", KeyEvent.KEYCODE_BUTTON_START),
            new ControllerButtonMapping("Select", KeyEvent.KEYCODE_BUTTON_SELECT)
    };

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable overlayAutoHide = this::hideTransientOverlayControls;
    private final Runnable fpsUpdater = new Runnable() {
        @Override
        public void run() {
            updateFpsLabel();
            if (!activityPaused) {
                uiHandler.postDelayed(this, 1000);
            }
        }
    };
    private final List<MediaEntry> mediaLibrary = new ArrayList<>();
    private SharedPreferences prefs;
    private Uri appFolderUri;
    private Uri gamesFolderUri;
    private FrameLayout rootView;
    private FrameLayout screenContainer;
    private TextureView renderView;
    private Surface renderSurface;
    private View libraryScreen;
    private View settingsPanel;
    private View playBar;
    private View crtOverlay;
    private ImageView bezelOverlay;
    private View tapePromptOverlay;
    private TextView tapePromptTitle;
    private TextView tapePromptDetail;
    private LinearLayout tapeControlsRow;
    private Button tapePanelToggleButton;
    private Button diskPanelToggleButton;
    private TextView statusView;
    private TextView mediaStatusView;
    private EditText searchView;
    private GridLayout mediaGrid;
    private LinearLayout formatTabs;
    private HorizontalScrollView carouselScroll;
    private LinearLayout carousel;
    private Button viewButton;
    private IgdbService igdbService;
    private int igdbNextIndex;
    private int igdbInFlight;
    private View controlsOverlay;
    private View keyboardOverlay;
    private DpadView dpadView;
    private View actionClusterView;
    private boolean controlsVisible;
    private boolean keyboardVisible;
    private boolean activityPaused;
    private int joystickMask;
    private int virtualJoystickMask;
    private int gamepadAxisMask;
    private int gamepadButtonMask;
    private int aspectMode;
    private boolean crtEnabled;
    private boolean bezelEnabled;
    private Button aspectButton;
    private TextView fpsView;
    private Button crtButton;
    private Button bezelButton;
    private Button tapePanelAutostartButton;
    private Button tapePanelTurboButton;
    private Button launcherAspectButton;
    private Button launcherCrtButton;
    private Button launcherBezelButton;
    private Button launcherTapeAutostartButton;
    private Button launcherTapeTurboButton;
    private C64Runtime c64Runtime;
    private C64Native.MediaType activeFormatFilter;
    private C64Native.MediaType currentMediaType = C64Native.MediaType.UNKNOWN;
    private String currentMediaName = "";
    private boolean currentTapeAutostart = true;
    private boolean tapePanelVisible;
    private boolean overlayControlsVisible = true;
    private int mediaLoadGeneration;
    private final List<Button> formatTabButtons = new ArrayList<>();
    private final Map<String, File> bezelIndex = new HashMap<>();
    private String bezelIndexRootPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (needsSetup()) {
            startActivity(new Intent(this, SetupWizardActivity.class));
            finish();
            return;
        }
        String appFolder = prefs.getString(PREF_APP_FOLDER_URI, "");
        if (!appFolder.isEmpty()) {
            appFolderUri = Uri.parse(appFolder);
        }
        String saved = prefs.getString(PREF_GAMES_FOLDER_URI, "");
        if (!saved.isEmpty()) {
            gamesFolderUri = Uri.parse(saved);
        }
        aspectMode = prefs.getInt(PREF_ASPECT_MODE, ASPECT_4_3);
        crtEnabled = prefs.getBoolean(PREF_CRT_USER_SET, false)
                && prefs.getBoolean(PREF_CRT_ENABLED, false);
        if (!prefs.getBoolean(PREF_CRT_USER_SET, false) && prefs.contains(PREF_CRT_ENABLED)) {
            prefs.edit().putBoolean(PREF_CRT_ENABLED, false).apply();
        }
        bezelEnabled = prefs.getBoolean(PREF_BEZEL_ENABLED, false);
        igdbService = IgdbService.getInstance(this);
        installC64RuntimeData();
        C64Native.initialize(c64DataDir());
        c64Runtime = new C64Runtime(this, c64DataDir());
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enterImmersiveMode();
        setContentView(createContentView());
        applyDisplayPreferences();
        openLibrary();
        maybeLoadLastMediaOnStartup();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
            hideSettingsPanel();
            return;
        }
        if (libraryScreen != null && libraryScreen.getVisibility() == View.VISIBLE) {
            closeLibrary();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        activityPaused = true;
        uiHandler.removeCallbacks(fpsUpdater);
        uiHandler.removeCallbacks(overlayAutoHide);
        releaseAllJoystickInputs();
        C64Native.setPaused(true);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityPaused = false;
        enterImmersiveMode();
        showTransientOverlayControls();
        syncEmulationPauseState();
        uiHandler.removeCallbacks(fpsUpdater);
        uiHandler.post(fpsUpdater);
    }

    @Override
    protected void onDestroy() {
        releaseAllJoystickInputs();
        uiHandler.removeCallbacks(fpsUpdater);
        uiHandler.removeCallbacks(overlayAutoHide);
        C64Native.setPaused(false);
        C64Native.setSurface(null);
        releaseRenderSurface();
        if (c64Runtime != null) {
            c64Runtime.stop();
        }
        super.onDestroy();
    }

    private void releaseRenderSurface() {
        if (renderSurface != null) {
            renderSurface.release();
            renderSurface = null;
        }
    }

    private View createContentView() {
        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(0xFF050607);
        rootView.setOnTouchListener(this::handleOverlayTouch);

        screenContainer = new FrameLayout(this);
        screenContainer.setBackgroundColor(Color.BLACK);
        screenContainer.setOnTouchListener(this::handleOverlayTouch);
        rootView.addView(screenContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        renderView = new TextureView(this);
        renderView.setOpaque(true);
        renderView.setOnTouchListener(this::handleOverlayTouch);
        renderView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                releaseRenderSurface();
                renderSurface = new Surface(surfaceTexture);
                C64Native.setSurface(renderSurface);
                if (C64Native.hasRealCore()) {
                    updateStatus("C64 display attached.");
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                if (renderSurface == null) {
                    renderSurface = new Surface(surfaceTexture);
                }
                C64Native.setSurface(renderSurface);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                C64Native.setSurface(null);
                releaseRenderSurface();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });
        screenContainer.addView(renderView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        bezelOverlay = new ImageView(this);
        bezelOverlay.setBackgroundColor(Color.TRANSPARENT);
        bezelOverlay.setScaleType(ImageView.ScaleType.FIT_XY);
        bezelOverlay.setAdjustViewBounds(false);
        bezelOverlay.setVisibility(View.GONE);
        screenContainer.addView(bezelOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        crtOverlay = new ScanlineView(this);
        crtOverlay.setVisibility(crtEnabled ? View.VISIBLE : View.GONE);
        screenContainer.addView(crtOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout playBar = new LinearLayout(this);
        this.playBar = playBar;
        playBar.setGravity(Gravity.CENTER);
        playBar.setOrientation(LinearLayout.HORIZONTAL);
        playBar.setPadding(dp(4), dp(4), dp(4), dp(4));
        playBar.setBackgroundColor(0xAA181B1F);
        aspectButton = makeTopButton(aspectLabel(), v -> toggleAspect());
        crtButton = makeTopButton(crtLabel(), v -> toggleCrt());
        bezelButton = makeTopButton(bezelLabel(), v -> toggleBezel());
        playBar.addView(aspectButton);
        playBar.addView(crtButton);
        playBar.addView(bezelButton);
        playBar.addView(makeTopButton("Pad", v -> toggleControls()));
        playBar.addView(makeTopButton("Keyb", v -> toggleKeyboard()));
        playBar.addView(makeTopButton("Settings", v -> toggleSettings()));
        FrameLayout.LayoutParams playBarParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        playBarParams.setMargins(0, dp(8), dp(8), 0);
        rootView.addView(playBar, playBarParams);

        fpsView = makeFpsView();
        FrameLayout.LayoutParams fpsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(36),
                Gravity.BOTTOM | Gravity.RIGHT);
        fpsParams.setMargins(0, 0, dp(8), dp(8));
        rootView.addView(fpsView, fpsParams);

        LinearLayout mediaToggleBar = new LinearLayout(this);
        mediaToggleBar.setGravity(Gravity.CENTER_VERTICAL);
        mediaToggleBar.setOrientation(LinearLayout.HORIZONTAL);
        tapePanelToggleButton = makeTopButton("Tape", v -> toggleTapePanel());
        tapePanelToggleButton.setVisibility(View.GONE);
        mediaToggleBar.addView(tapePanelToggleButton);
        diskPanelToggleButton = makeTopButton("💾 Disk", v -> showDiskDriveOptionsDialog());
        diskPanelToggleButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams diskToggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        diskToggleParams.setMargins(dp(6), 0, 0, 0);
        mediaToggleBar.addView(diskPanelToggleButton, diskToggleParams);
        FrameLayout.LayoutParams mediaToggleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        mediaToggleParams.setMargins(dp(8), dp(8), 0, 0);
        rootView.addView(mediaToggleBar, mediaToggleParams);

        libraryScreen = createLibraryScreen();
        libraryScreen.setVisibility(View.GONE);
        rootView.addView(libraryScreen, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        settingsPanel = createSettingsPanel();
        settingsPanel.setVisibility(View.GONE);
        rootView.addView(settingsPanel, new FrameLayout.LayoutParams(ViceMenuPanel.panelWidth(this),
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.RIGHT));

        controlsOverlay = createControlsOverlay();
        controlsOverlay.setVisibility(View.GONE);
        rootView.addView(controlsOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        keyboardOverlay = createKeyboardOverlay();
        keyboardOverlay.setVisibility(View.GONE);
        rootView.addView(keyboardOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        tapePromptOverlay = createTapePromptOverlay();
        tapePromptOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams tapePromptParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tapePromptParams.setMargins(dp(8), dp(64), 0, dp(8));
        rootView.addView(tapePromptOverlay, tapePromptParams);
        showTransientOverlayControls();
        return rootView;
    }

    private View createSettingsPanel() {
        return ViceMenuPanel.build(this, new ViceMenuPanel.Host() {
            @Override public void onRerunSetup() {
                hideSettingsPanel();
                rerunSetupWizard();
            }

            @Override public void onGameLibrary() {
                hideSettingsPanel(false);
                openLibrary();
            }

            @Override public void onDownloadBezels() {
                hideSettingsPanel();
                showDownloadBezelsDialog();
            }

            @Override public void onController() {
                hideSettingsPanel();
                showControllerSettingsDialog();
            }

            @Override public void onAdvancedSettings() {
                hideSettingsPanel();
                showAdvancedViceSettings();
            }

            @Override public void onReset() {
                resetC64FromSettings();
            }

            @Override public void onDebugInfo() {
                showDebugInfo();
            }

            @Override public void onClose() {
                hideSettingsPanel();
            }
        });
    }

    private View createLibraryScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(0xFF050607);
        screen.setClickable(true);
        screen.setFocusable(true);

        LinearLayout header = createLauncherControlBar();
        screen.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(dp(12), dp(8), dp(12), dp(4));

        searchView = new EditText(this);
        searchView.setSingleLine(true);
        searchView.setHint("Search C64 games");
        searchView.setTextColor(Color.WHITE);
        searchView.setHintTextColor(0xFF8C939D);
        searchView.setTextSize(13.0f);
        searchView.setPadding(dp(10), 0, dp(10), 0);
        searchRow.addView(searchView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f));
        viewButton = makeTopButton("Grid", v -> toggleViewMode());
        searchRow.addView(viewButton);
        screen.addView(searchRow);

        mediaStatusView = new TextView(this);
        mediaStatusView.setTextColor(0xFFBAC2CC);
        mediaStatusView.setTextSize(12.0f);
        mediaStatusView.setPadding(dp(12), 0, dp(12), dp(6));
        screen.addView(mediaStatusView);

        formatTabs = new LinearLayout(this);
        formatTabs.setGravity(Gravity.CENTER_VERTICAL);
        formatTabs.setOrientation(LinearLayout.HORIZONTAL);
        formatTabs.setPadding(dp(12), 0, dp(12), dp(6));
        addFormatTab("All", null);
        addFormatTab("PRG", C64Native.MediaType.PRG);
        addFormatTab("Disk", C64Native.MediaType.DISK);
        addFormatTab("Tape", C64Native.MediaType.TAPE);
        addFormatTab("Cart", C64Native.MediaType.CARTRIDGE);
        screen.addView(formatTabs, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView gridScroll = new ScrollView(this);
        gridScroll.setFillViewport(false);
        mediaGrid = new GridLayout(this);
        mediaGrid.setColumnCount(4);
        mediaGrid.setUseDefaultMargins(false);
        mediaGrid.setPadding(dp(8), dp(4), dp(8), dp(12));
        gridScroll.addView(mediaGrid, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        screen.addView(gridScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        carouselScroll = new HorizontalScrollView(this);
        carouselScroll.setFillViewport(false);
        carouselScroll.setVisibility(View.GONE);
        carousel = new LinearLayout(this);
        carousel.setOrientation(LinearLayout.HORIZONTAL);
        carousel.setGravity(Gravity.CENTER_VERTICAL);
        carousel.setPadding(dp(8), dp(4), dp(8), dp(12));
        carouselScroll.addView(carousel, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.MATCH_PARENT));
        screen.addView(carouselScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderLibrary(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        return screen;
    }

    private LinearLayout createLauncherControlBar() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.setBackgroundColor(0xFF0B0D10);

        TextView title = new TextView(this);
        title.setText("C64");
        title.setTextColor(0xFFE8EAED);
        title.setTextSize(16.0f);
        title.setPadding(dp(4), 0, dp(10), 0);
        header.addView(title);

        Button coreButton = makeTopButton("C64", v -> Toast.makeText(this, "C64 core target", Toast.LENGTH_SHORT).show());
        header.addView(coreButton);

        launcherAspectButton = makeTopButton(aspectLabel(), v -> toggleAspect());
        header.addView(launcherAspectButton);
        launcherCrtButton = makeTopButton(crtLabel(), v -> toggleCrt());
        header.addView(launcherCrtButton);
        launcherBezelButton = makeTopButton(bezelLabel(), v -> toggleBezel());
        header.addView(launcherBezelButton);
        launcherTapeAutostartButton = makeTopButton(tapeAutostartLabel(), v -> toggleTapeAutostart());
        header.addView(launcherTapeAutostartButton);
        launcherTapeTurboButton = makeTopButton(tapeTurboLabel(), v -> toggleTapeTurbo());
        header.addView(launcherTapeTurboButton);

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));

        header.addView(makeTopButton("Pad", v -> toggleControls()));
        header.addView(makeTopButton("Keyb", v -> toggleKeyboard()));
        header.addView(makeTopButton("⚙", v -> toggleSettings()));
        return header;
    }

    private View createControlsOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(false);

        dpadView = new DpadView(this);
        dpadView.setListener((up, down, left, right) -> {
            int directions = 0;
            if (up) {
                directions |= JOY_UP;
            }
            if (down) {
                directions |= JOY_DOWN;
            }
            if (left) {
                directions |= JOY_LEFT;
            }
            if (right) {
                directions |= JOY_RIGHT;
            }
            virtualJoystickMask = (virtualJoystickMask & ~JOY_DIRECTIONS) | directions;
            updateJoystickState();
        });
        FrameLayout.LayoutParams dpadParams = new FrameLayout.LayoutParams(
                dp(172),
                dp(172),
                Gravity.LEFT | Gravity.BOTTOM);
        dpadParams.setMargins(dp(18), 0, 0, dp(18));
        overlay.addView(dpadView, dpadParams);

        LinearLayout actionCluster = new LinearLayout(this);
        actionCluster.setOrientation(LinearLayout.VERTICAL);
        actionCluster.setGravity(Gravity.CENTER);
        actionCluster.addView(makeAssignableVirtualButton("A", JOY_FIRE, PREF_VIRTUAL_BUTTON_1_KEY),
                fixedButtonParams(64, 64));
        actionCluster.addView(makeAssignableVirtualButton("B", JOY_FIRE_2, PREF_VIRTUAL_BUTTON_2_KEY),
                fixedButtonParams(64, 64));
        actionClusterView = actionCluster;
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT | Gravity.BOTTOM);
        actionParams.setMargins(0, 0, dp(18), dp(18));
        overlay.addView(actionCluster, actionParams);
        applyVirtualPadPosition();

        return overlay;
    }

    private View createKeyboardOverlay() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackgroundColor(0xEE0B0D10);
        panel.setClickable(true);

        addKeyboardRow(panel, new KeySpec[] {
                key("←", 7, 1), key("1", 7, 0), key("2", 7, 3), key("3", 1, 0),
                key("4", 1, 3), key("5", 2, 0), key("6", 2, 3), key("7", 3, 0),
                key("8", 3, 3), key("9", 4, 0), key("0", 4, 3), key("+", 5, 0),
                key("-", 5, 3), key("£", 6, 0), key("DEL", 0, 0), key("F1", 0, 4)
        });
        addKeyboardRow(panel, new KeySpec[] {
                key("CTRL", 7, 2, 1.35f), key("Q", 7, 6), key("W", 1, 1),
                key("E", 1, 6), key("R", 2, 1), key("T", 2, 6), key("Y", 3, 1),
                key("U", 3, 6), key("I", 4, 1), key("O", 4, 6), key("P", 5, 1),
                key("@", 5, 6), key("*", 6, 1), key("↑", 6, 6), key("RESTORE", -3, 0, 1.65f),
                key("F3", 0, 5)
        });
        addKeyboardRow(panel, new KeySpec[] {
                key("RUN/STOP", 7, 7, 1.8f), key("A", 1, 2), key("S", 1, 5),
                key("D", 2, 2), key("F", 2, 5), key("G", 3, 2), key("H", 3, 5),
                key("J", 4, 2), key("K", 4, 5), key("L", 5, 2), key(":", 5, 5),
                key(";", 6, 2), key("=", 6, 5), key("RETURN", 0, 1, 1.8f), key("F5", 0, 6)
        });
        addKeyboardRow(panel, new KeySpec[] {
                key("C=", 7, 5, 1.25f), key("SHIFT", 1, 7, 1.45f), key("Z", 1, 4),
                key("X", 2, 7), key("C", 2, 4), key("V", 3, 7), key("B", 3, 4),
                key("N", 4, 7), key("M", 4, 4), key(",", 5, 7), key(".", 5, 4),
                key("/", 6, 7), key("SHIFT", 6, 4, 1.45f), key("CRSR ↕", 0, 7, 1.45f),
                key("CRSR ↔", 0, 2, 1.45f), key("F7", 0, 3)
        });
        addKeyboardRow(panel, new KeySpec[] {
                key("SPACE", 7, 4, 6.0f),
                actionKey("TYPE RUN", () -> C64Native.feedKeyboard("RUN\r"), 1.8f),
                actionKey("LIST", () -> C64Native.feedKeyboard("LIST\r"), 1.4f),
                actionKey("CLOSE", this::toggleKeyboard, 1.4f)
        });
        return panel;
    }

    private void addKeyboardRow(LinearLayout panel, KeySpec[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        for (KeySpec spec : keys) {
            Button button = spec.action == null ? makeMatrixKeyButton(spec) : makeKeyboardActionButton(spec);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), spec.weight);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(button, params);
        }
        panel.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private Button makeMatrixKeyButton(KeySpec spec) {
        Button button = makeCompactButton(spec.label, null);
        button.setTextSize(spec.label.length() > 5 ? 10.0f : 12.0f);
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    C64Native.setMatrixKey(spec.row, spec.column, true);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    C64Native.setMatrixKey(spec.row, spec.column, false);
                    v.setPressed(false);
                    return true;
                default:
                    return true;
            }
        });
        return button;
    }

    private Button makeKeyboardActionButton(KeySpec spec) {
        Button button = makeCompactButton(spec.label, v -> {
            if (spec.action != null) {
                spec.action.run();
            }
        });
        button.setTextSize(10.0f);
        return button;
    }

    private View createTapePromptOverlay() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(cardBackground(0xEE181B1F, 0xFF46505C));
        panel.setClickable(true);
        panel.setMinimumWidth(dp(286));

        tapePromptTitle = new TextView(this);
        tapePromptTitle.setTextColor(Color.WHITE);
        tapePromptTitle.setTextSize(14.0f);
        tapePromptTitle.setMaxLines(1);
        panel.addView(tapePromptTitle);

        tapePromptDetail = new TextView(this);
        tapePromptDetail.setTextColor(0xFFBAC2CC);
        tapePromptDetail.setTextSize(11.0f);
        tapePromptDetail.setMaxLines(4);
        tapePromptDetail.setPadding(0, dp(2), 0, dp(8));
        panel.addView(tapePromptDetail);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        tapePanelAutostartButton = makeCompactButton(tapeAutostartLabel(), v -> toggleTapeAutostart());
        modeRow.addView(tapePanelAutostartButton, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        tapePanelTurboButton = makeCompactButton(tapeTurboLabel(), v -> toggleTapeTurbo());
        LinearLayout.LayoutParams turboParams = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        turboParams.setMargins(dp(6), 0, 0, 0);
        modeRow.addView(tapePanelTurboButton, turboParams);
        Button modalButton = makeCompactButton("Controls", v -> showTapeControlsDialog());
        LinearLayout.LayoutParams modalParams = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        modalParams.setMargins(dp(6), 0, 0, 0);
        modeRow.addView(modalButton, modalParams);
        panel.addView(modeRow);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.addView(makeTapeButton("Play", C64Native.TapeCommand.PLAY));
        buttons.addView(makeTapeButton("Stop", C64Native.TapeCommand.STOP));
        buttons.addView(makeTapeButton("Rewind", C64Native.TapeCommand.REWIND));
        tapeControlsRow = buttons;
        panel.addView(tapeControlsRow);
        return panel;
    }

    private LinearLayout.LayoutParams fixedButtonParams(int widthDp, int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp));
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private Button makeAssignableVirtualButton(String label, int fallbackBit, String prefKey) {
        Button button = makeCompactButton(label, null);
        button.setTextColor(0xEEFFFFFF);
        button.setTextSize(18.0f);
        button.setBackground(virtualPadButtonBackground(false));
        button.setOnTouchListener((v, event) -> {
            int keyOrdinal = prefs == null
                    ? KEY_MAPPING_DEFAULT
                    : prefs.getInt(prefKey, KEY_MAPPING_DEFAULT);
            C64Native.C64Key mappedKey = c64KeyForOrdinal(keyOrdinal);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (mappedKey != null) {
                        C64Native.setKey(mappedKey, true);
                    } else {
                        virtualJoystickMask |= fallbackBit;
                        updateJoystickState();
                    }
                    v.setPressed(true);
                    v.setBackground(virtualPadButtonBackground(true));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mappedKey != null) {
                        C64Native.setKey(mappedKey, false);
                    } else {
                        virtualJoystickMask &= ~fallbackBit;
                        updateJoystickState();
                    }
                    v.setPressed(false);
                    v.setBackground(virtualPadButtonBackground(false));
                    return true;
                default:
                    return true;
            }
        });
        return button;
    }

    private void applyVirtualPadPosition() {
        if (controlsOverlay == null || dpadView == null || actionClusterView == null || prefs == null) {
            return;
        }
        int position = prefs.getInt(PREF_VIRTUAL_PAD_POSITION, 0);
        FrameLayout.LayoutParams dpadParams = (FrameLayout.LayoutParams) dpadView.getLayoutParams();
        FrameLayout.LayoutParams actionParams = (FrameLayout.LayoutParams) actionClusterView.getLayoutParams();
        switch (position) {
            case 1:
                setOverlayPosition(dpadParams, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 18, 18);
                setOverlayPosition(actionParams, Gravity.LEFT | Gravity.BOTTOM, 18, 0, 0, 18);
                break;
            case 2:
                setOverlayPosition(dpadParams, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, 0, 0, 0);
                setOverlayPosition(actionParams, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 18, 0);
                break;
            case 3:
                setOverlayPosition(dpadParams, Gravity.LEFT | Gravity.TOP, 18, 72, 0, 0);
                setOverlayPosition(actionParams, Gravity.RIGHT | Gravity.TOP, 0, 72, 18, 0);
                break;
            default:
                setOverlayPosition(dpadParams, Gravity.LEFT | Gravity.BOTTOM, 18, 0, 0, 18);
                setOverlayPosition(actionParams, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 18, 18);
                break;
        }
        dpadView.setLayoutParams(dpadParams);
        actionClusterView.setLayoutParams(actionParams);
    }

    private void setOverlayPosition(FrameLayout.LayoutParams params, int gravity,
                                    int left, int top, int right, int bottom) {
        params.gravity = gravity;
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
    }

    private GradientDrawable virtualPadButtonBackground(boolean pressed) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(999));
        drawable.setColor(pressed ? 0x99C5CBD3 : 0x665F6670);
        drawable.setStroke(dp(1), pressed ? 0xEEFFFFFF : 0x99D6DADF);
        return drawable;
    }

    private Button makeKeyButton(String label, C64Native.C64Key key) {
        Button button = makeCompactButton(label, null);
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    C64Native.setKey(key, true);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    C64Native.setKey(key, false);
                    v.setPressed(false);
                    return true;
                default:
                    return true;
            }
        });
        return button;
    }

    private Button makeTapeButton(String label, C64Native.TapeCommand command) {
        Button button = makeCompactButton(label, v -> {
            if (C64Native.hasRealCore()) {
                C64Native.tapeCommand(command);
                updateStatus("Tape " + label.toLowerCase(Locale.US) + " sent to C64 core");
            } else if (c64Runtime != null && c64Runtime.isRunning()) {
                c64Runtime.tapeCommand(command);
                updateStatus("Tape " + label.toLowerCase(Locale.US) + " sent to runtime");
            } else {
                C64Native.tapeCommand(command);
                updateStatus("Tape " + label.toLowerCase(Locale.US) + " requested; core is not running");
            }
        });
        LinearLayout.LayoutParams params = fixedButtonParams(78, 38);
        button.setLayoutParams(params);
        return button;
    }

    private void showTapePrompt(String displayName, boolean autostart) {
        if (tapePromptOverlay == null || currentMediaType != C64Native.MediaType.TAPE) {
            return;
        }
        String name = displayName == null || displayName.isBlank() ? currentMediaName : displayName;
        if (tapePromptTitle != null) {
            tapePromptTitle.setText("Tape inserted");
        }
        if (tapePromptDetail != null) {
            String file = name == null || name.isBlank() ? "Attached tape" : name;
            tapePromptDetail.setText(autostart
                    ? file + "\nAuto: VICE starts tape when needed."
                    : file + "\nManual: press Play at the tape prompt.");
        }
        if (tapePanelAutostartButton != null) {
            tapePanelAutostartButton.setText(tapeAutostartLabel());
        }
        if (tapePanelTurboButton != null) {
            tapePanelTurboButton.setText(tapeTurboLabel());
        }
        if (tapeControlsRow != null) {
            tapeControlsRow.setVisibility(autostart ? View.GONE : View.VISIBLE);
        }
        updateTapePanelVisibility();
    }

    private void hideTapePrompt() {
        tapePanelVisible = false;
        if (tapePromptOverlay != null) {
            tapePromptOverlay.setVisibility(View.GONE);
        }
        if (tapePanelToggleButton != null) {
            tapePanelToggleButton.setVisibility(View.GONE);
        }
        updateDiskButtonVisibility(false);
    }

    private void toggleTapePanel() {
        if (currentMediaType != C64Native.MediaType.TAPE) {
            return;
        }
        tapePanelVisible = !tapePanelVisible;
        updateTapePanelVisibility();
    }

    private void updateTapePanelVisibility() {
        boolean available = currentMediaType == C64Native.MediaType.TAPE && overlayControlsVisible;
        if (tapePanelToggleButton != null) {
            tapePanelToggleButton.setVisibility(available ? View.VISIBLE : View.GONE);
            if (available) {
                tapePanelToggleButton.bringToFront();
            }
        }
        if (tapePromptOverlay != null) {
            tapePromptOverlay.setVisibility(available && tapePanelVisible ? View.VISIBLE : View.GONE);
            if (available && tapePanelVisible) {
                tapePromptOverlay.bringToFront();
            }
        }
        if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
            settingsPanel.bringToFront();
        }
        updateDiskButtonVisibility(true);
    }

    private void showTransientOverlayControls() {
        if (libraryScreen != null && libraryScreen.getVisibility() == View.VISIBLE) {
            return;
        }
        overlayControlsVisible = true;
        if (playBar != null) {
            playBar.setVisibility(View.VISIBLE);
            playBar.bringToFront();
        }
        if (fpsView != null) {
            fpsView.setVisibility(View.VISIBLE);
            fpsView.bringToFront();
        }
        updateTapePanelVisibility();
        updateDiskButtonVisibility(true);
        scheduleOverlayAutoHide();
    }

    private void scheduleOverlayAutoHide() {
        uiHandler.removeCallbacks(overlayAutoHide);
        if (libraryScreen != null && libraryScreen.getVisibility() == View.VISIBLE) {
            return;
        }
        if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
            return;
        }
        uiHandler.postDelayed(overlayAutoHide, OVERLAY_AUTO_HIDE_MS);
    }

    private void hideTransientOverlayControls() {
        if (libraryScreen != null && libraryScreen.getVisibility() == View.VISIBLE) {
            return;
        }
        if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
            return;
        }
        overlayControlsVisible = false;
        if (playBar != null) {
            playBar.setVisibility(View.INVISIBLE);
        }
        if (fpsView != null) {
            fpsView.setVisibility(View.INVISIBLE);
        }
        updateTapePanelVisibility();
        updateDiskButtonVisibility(false);
    }

    private void updateDiskButtonVisibility(boolean allowVisible) {
        if (diskPanelToggleButton == null) {
            return;
        }
        boolean available = allowVisible && overlayControlsVisible
                && libraryScreen != null && libraryScreen.getVisibility() != View.VISIBLE;
        diskPanelToggleButton.setVisibility(available ? View.VISIBLE : View.GONE);
        if (available) {
            diskPanelToggleButton.bringToFront();
        }
    }

    private void showTapeControlsDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(14), dp(16), dp(14));
        container.setBackground(cardBackground(0xFF15191E, 0xFF46505C));

        TextView title = new TextView(this);
        title.setText("VICE Datasette");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16.0f);
        container.addView(title);

        TextView detail = new TextView(this);
        detail.setText("Tape control commands are sent to VICE datasette port 1.");
        detail.setTextColor(0xFFBAC2CC);
        detail.setTextSize(11.0f);
        detail.setPadding(0, dp(3), 0, dp(10));
        container.addView(detail);

        addTapeControlRow(container, "Stop", C64Native.TapeCommand.STOP, "Play", C64Native.TapeCommand.PLAY);
        addTapeControlRow(container, "Forward", C64Native.TapeCommand.FORWARD, "Rewind", C64Native.TapeCommand.REWIND);
        addTapeControlRow(container, "Record", C64Native.TapeCommand.RECORD, "Reset", C64Native.TapeCommand.RESET);
        addTapeControlRow(container, "Reset Counter", C64Native.TapeCommand.RESET_COUNTER, null, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private void addTapeControlRow(LinearLayout container, String leftLabel, C64Native.TapeCommand leftCommand,
                                   String rightLabel, C64Native.TapeCommand rightCommand) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(makeTapeButton(leftLabel, leftCommand), new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        if (rightLabel != null && rightCommand != null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
            params.setMargins(dp(8), 0, 0, 0);
            row.addView(makeTapeButton(rightLabel, rightCommand), params);
        }
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(6), 0, 0);
        container.addView(row, rowParams);
    }

    private void openLibrary() {
        uiHandler.removeCallbacks(overlayAutoHide);
        overlayControlsVisible = false;
        releaseAllJoystickInputs();
        if (libraryScreen != null) {
            libraryScreen.setVisibility(View.VISIBLE);
            libraryScreen.bringToFront();
        }
        if (playBar != null) {
            playBar.setVisibility(View.GONE);
        }
        if (controlsOverlay != null) {
            controlsOverlay.setVisibility(View.GONE);
        }
        if (keyboardOverlay != null) {
            keyboardVisible = false;
            keyboardOverlay.setVisibility(View.GONE);
        }
        hideTapePrompt();
        refreshLibrary();
        if (rootView != null) {
            rootView.post(this::applyDisplayPreferences);
        }
        syncEmulationPauseState();
    }

    private void closeLibrary() {
        if (libraryScreen != null) {
            libraryScreen.setVisibility(View.GONE);
        }
        if (controlsOverlay != null) {
            controlsOverlay.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
            if (controlsVisible) {
                controlsOverlay.bringToFront();
            }
        }
        showTransientOverlayControls();
        if (currentMediaType == C64Native.MediaType.TAPE) {
            showTapePrompt(null, currentTapeAutostart);
        } else {
            updateDiskButtonVisibility(true);
        }
        if (rootView != null) {
            rootView.post(this::applyDisplayPreferences);
        }
        syncEmulationPauseState();
    }

    private void refreshLibrary() {
        if (mediaStatusView != null) {
            mediaStatusView.setText(gamesFolderUri == null ? "Choose a games folder or import a single C64 file." : "Scanning games folder...");
        }
        mediaLibrary.clear();
        renderLibrary();
        if (gamesFolderUri == null) {
            return;
        }
        new Thread(() -> {
            List<MediaEntry> scanned = scanMediaLibrary(gamesFolderUri);
            Collections.sort(scanned, Comparator.comparing(a -> a.displayName.toLowerCase(Locale.US)));
            uiHandler.post(() -> {
                hydrateSavedIgdbMatches(scanned);
                mediaLibrary.clear();
                mediaLibrary.addAll(scanned);
                renderLibrary();
                beginIgdbMatching();
            });
        }, "ViceC64LibraryScan").start();
    }

    private void renderLibrary() {
        if (mediaGrid == null || carousel == null || mediaStatusView == null) {
            return;
        }
        boolean carouselMode = prefs.getInt(PREF_VIEW_STYLE, VIEW_GRID) == VIEW_CAROUSEL;
        mediaGrid.removeAllViews();
        carousel.removeAllViews();
        mediaGrid.setVisibility(carouselMode ? View.GONE : View.VISIBLE);
        carouselScroll.setVisibility(carouselMode ? View.VISIBLE : View.GONE);
        viewButton.setText(carouselMode ? "Carousel" : "Grid");
        updateFormatTabs();

        String query = searchView == null ? "" : searchView.getText().toString();
        int shown = 0;
        for (MediaEntry entry : mediaLibrary) {
            if (!matchesFormat(entry) || !matchesSearch(displayBaseName(entry.displayName), query)) {
                continue;
            }
            if (carouselMode) {
                carousel.addView(createMediaCard(entry, true));
            } else {
                mediaGrid.addView(createMediaCard(entry, false));
            }
            shown++;
        }
        if (gamesFolderUri == null) {
            mediaStatusView.setText("No folder selected. Use Folder or Import.");
        } else if (mediaLibrary.isEmpty()) {
            mediaStatusView.setText("No C64 media found. Supported: PRG, P00, D64, G64, D71, D81, TAP, CRT.");
        } else {
            String igdb = igdbService.hasCredentials() ? "IGDB ready" : "IGDB credentials missing";
            String scope = activeFormatFilter == null ? "C64 files" : formatLabel(activeFormatFilter).toLowerCase(Locale.US) + " files";
            mediaStatusView.setText(shown + " of " + countFormat(activeFormatFilter) + " " + scope
                    + " | total " + mediaLibrary.size() + " | " + igdb);
        }
    }

    private void addFormatTab(String label, C64Native.MediaType type) {
        Button button = makeTopButton(label, v -> {
            activeFormatFilter = type;
            renderLibrary();
        });
        button.setTag(type);
        formatTabButtons.add(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34));
        params.setMargins(0, 0, dp(6), 0);
        formatTabs.addView(button, params);
    }

    private void updateFormatTabs() {
        if (formatTabButtons.isEmpty()) {
            return;
        }
        for (Button button : formatTabButtons) {
            Object tag = button.getTag();
            C64Native.MediaType type = tag instanceof C64Native.MediaType ? (C64Native.MediaType) tag : null;
            boolean active = activeFormatFilter == type;
            button.setText(tabLabel(type) + " " + countFormat(type));
            button.setTextColor(Color.WHITE);
            button.setAlpha(active ? 1.0f : 0.78f);
            button.setBackground(cardBackground(active ? 0xFF334252 : 0xFF242A31,
                    active ? 0xFF7EA4C6 : 0xFF46505C));
        }
    }

    private boolean matchesFormat(MediaEntry entry) {
        return activeFormatFilter == null || entry.mediaType == activeFormatFilter;
    }

    private int countFormat(C64Native.MediaType type) {
        if (type == null) {
            return mediaLibrary.size();
        }
        int count = 0;
        for (MediaEntry entry : mediaLibrary) {
            if (entry.mediaType == type) {
                count++;
            }
        }
        return count;
    }

    private String tabLabel(C64Native.MediaType type) {
        return type == null ? "All" : formatLabel(type);
    }

    private String formatLabel(C64Native.MediaType type) {
        switch (type) {
            case PRG:
                return "PRG";
            case DISK:
                return "Disk";
            case TAPE:
                return "Tape";
            case CARTRIDGE:
                return "Cart";
            default:
                return "C64";
        }
    }

    private View createMediaCard(MediaEntry entry, boolean carouselMode) {
        int cardW = carouselMode ? 190 : 168;
        int cardH = carouselMode ? 292 : 248;
        int coverH = carouselMode ? 220 : 176;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(cardBackground(0xFF191D22, 0xFF353B44));
        card.setOnClickListener(v -> showMediaDetails(entry));

        FrameLayout coverSlot = new FrameLayout(this);
        coverSlot.setBackground(cardBackground(0xFF262C34, 0xFF404853));
        if (entry.coverBitmap != null) {
            ImageView cover = new ImageView(this);
            cover.setImageBitmap(entry.coverBitmap);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            coverSlot.addView(cover, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            TextView label = new TextView(this);
            label.setText(entry.igdbLookupStarted ? "IGDB" : entry.mediaType.name());
            label.setTextColor(0xFFB9C2CE);
            label.setTextSize(12.0f);
            label.setGravity(Gravity.CENTER);
            coverSlot.addView(label, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        card.addView(coverSlot, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(coverH)));

        TextView title = new TextView(this);
        title.setText(entry.igdbGame != null && entry.igdbGame.name != null && !entry.igdbGame.name.isBlank()
                ? entry.igdbGame.name
                : displayBaseName(entry.displayName));
        title.setTextColor(Color.WHITE);
        title.setTextSize(12.0f);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setPadding(0, dp(4), 0, 0);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

        TextView detail = new TextView(this);
        detail.setText(entry.igdbGame == null ? fileExtensionLabel(entry.displayName) : metadataDetail(entry));
        detail.setTextColor(0xFF9AA3AF);
        detail.setTextSize(10.0f);
        detail.setGravity(Gravity.CENTER);
        card.addView(detail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));

        if (carouselMode) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(cardW), dp(cardH));
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            card.setLayoutParams(params);
        } else {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(cardW);
            params.height = dp(cardH);
            params.setMargins(dp(6), dp(6), dp(6), dp(6));
            card.setLayoutParams(params);
        }
        return card;
    }

    private void showMediaDetails(MediaEntry entry) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(14), dp(14), dp(14), dp(14));
        container.setBackgroundColor(0xF8101418);

        TextView title = new TextView(this);
        title.setText(entry.igdbGame != null && entry.igdbGame.name != null && !entry.igdbGame.name.isBlank()
                ? entry.igdbGame.name
                : displayBaseName(entry.displayName));
        title.setTextColor(Color.WHITE);
        title.setTextSize(18.0f);
        container.addView(title);

        TextView detail = new TextView(this);
        detail.setText(entry.displayName + "\n" + entry.mediaType.name() + "\n" + metadataDetail(entry));
        detail.setTextColor(0xFFC8D0DB);
        detail.setPadding(0, dp(8), 0, dp(8));
        container.addView(detail);

        Button load = makeCompactButton(loadButtonLabel(entry), null);
        container.addView(load);
        Button attachOnly = null;
        if (entry.mediaType == C64Native.MediaType.TAPE) {
            attachOnly = makeCompactButton("Attach Only", null);
            LinearLayout.LayoutParams attachParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            attachParams.setMargins(0, dp(8), 0, 0);
            container.addView(attachOnly, attachParams);
        }

        TextView summary = new TextView(this);
        summary.setText(entry.igdbGame != null && entry.igdbGame.summary != null ? entry.igdbGame.summary : "");
        summary.setTextColor(0xFFE1E6ED);
        summary.setPadding(0, dp(10), 0, 0);
        container.addView(summary);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .create();
        final Button attachOnlyButton = attachOnly;
        load.setOnClickListener(v -> {
            boolean tapeAutostart = entry.mediaType != C64Native.MediaType.TAPE
                    || prefs.getBoolean(PREF_TAPE_AUTOSTART, true);
            dialog.dismiss();
            loadMedia(entry, tapeAutostart);
        });
        if (attachOnlyButton != null) {
            attachOnlyButton.setOnClickListener(v -> {
                prefs.edit().putBoolean(PREF_TAPE_AUTOSTART, false).apply();
                dialog.dismiss();
                loadMedia(entry, false);
            });
        }
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private String loadButtonLabel(MediaEntry entry) {
        if (entry.mediaType == C64Native.MediaType.PRG) {
            return "Load PRG";
        }
        if (entry.mediaType == C64Native.MediaType.TAPE) {
            return prefs != null && prefs.getBoolean(PREF_TAPE_AUTOSTART, true)
                    ? "Load Tape (Auto)"
                    : "Load Tape";
        }
        return "Load";
    }

    private void loadMedia(MediaEntry entry) {
        loadMedia(entry, entry.mediaType != C64Native.MediaType.TAPE
                || prefs.getBoolean(PREF_TAPE_AUTOSTART, true));
    }

    private void loadMedia(MediaEntry entry, boolean tapeAutostart) {
        final int generation = ++mediaLoadGeneration;
        new Thread(() -> {
            try {
                File mediaFile = resolvePlayableFile(entry);
                LaunchTarget launchTarget = prepareLaunchTarget(entry, mediaFile, tapeAutostart);
                ensureC64RuntimeDataInstalled();
                String command = C64Native.launch(launchTarget.file.getAbsolutePath(),
                        launchTarget.mediaType, tapeAutostart);
                saveLastMedia(entry);
                uiHandler.post(() -> {
                    currentMediaType = launchTarget.mediaType;
                    currentMediaName = entry.displayName;
                    currentTapeAutostart = tapeAutostart;
                    applyDisplayPreferences();
                    updateStatus(C64Native.hasRealCore()
                            ? "C64 running: " + entry.displayName
                            : "Prepared C64 command: " + command);
                    Toast.makeText(this, "Loading " + entry.displayName, Toast.LENGTH_SHORT).show();
                    closeLibrary();
                    if (launchTarget.mediaType == C64Native.MediaType.TAPE) {
                        tapePanelVisible = false;
                        applyTapeTurboPreference();
                        updateStatus("Tape inserted: " + entry.displayName);
                        showTapePrompt(entry.displayName, tapeAutostart);
                    } else {
                        C64Native.setTapeTurbo(false);
                        hideTapePrompt();
                        updateDiskButtonVisibility(true);
                    }
                });
            } catch (Exception ex) {
                uiHandler.post(() -> updateStatus("Load failed: " + ex.getMessage()));
            }
        }, "ViceC64LoadMedia").start();
    }

    private LaunchTarget prepareLaunchTarget(MediaEntry entry, File mediaFile, boolean tapeAutostart) throws IOException {
        return new LaunchTarget(mediaFile, entry.mediaType);
    }

    private void maybeLoadLastMediaOnStartup() {
        String uriString = prefs.getString(PREF_LAST_MEDIA_URI, "");
        String displayName = prefs.getString(PREF_LAST_MEDIA_NAME, "");
        int typeOrdinal = prefs.getInt(PREF_LAST_MEDIA_TYPE, C64Native.MediaType.UNKNOWN.ordinal());
        if (uriString.isEmpty() || displayName.isEmpty()
                || typeOrdinal < 0 || typeOrdinal >= C64Native.MediaType.values().length) {
            return;
        }
        C64Native.MediaType type = C64Native.MediaType.values()[typeOrdinal];
        if (mediaTypeForName(displayName) == C64Native.MediaType.UNKNOWN
                || type == C64Native.MediaType.UNKNOWN) {
            return;
        }
        MediaEntry last = new MediaEntry(displayName, Uri.parse(uriString), type);
        uiHandler.postDelayed(() -> {
            updateStatus("Loading last C64 game: " + displayName);
            loadMedia(last);
        }, 350);
    }

    private void saveLastMedia(MediaEntry entry) {
        prefs.edit()
                .putString(PREF_LAST_MEDIA_URI, entry.uri.toString())
                .putString(PREF_LAST_MEDIA_NAME, entry.displayName)
                .putInt(PREF_LAST_MEDIA_TYPE, entry.mediaType.ordinal())
                .apply();
    }

    private File resolvePlayableFile(MediaEntry entry) throws Exception {
        if ("file".equalsIgnoreCase(entry.uri.getScheme())) {
            File direct = new File(entry.uri.getPath());
            if (direct.isFile()) {
                return direct;
            }
        }
        File cacheDir = new File(getCacheDir(), "selected_media");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IllegalStateException("Could not create media cache");
        }
        File out = new File(cacheDir, sanitizeFileName(entry.displayName));
        try (InputStream input = getContentResolver().openInputStream(entry.uri);
             FileOutputStream output = new FileOutputStream(out)) {
            if (input == null) {
                throw new IllegalStateException("Could not open media");
            }
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        }
        return out;
    }

    private List<MediaEntry> scanMediaLibrary(Uri treeUri) {
        List<MediaEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            scanDocumentChildren(treeUri, DocumentsContract.getTreeDocumentId(treeUri), out, seen, 0);
        } catch (Exception ignored) {
            File folder = StoragePathUtils.fileForTreeUri(treeUri);
            if (folder != null) {
                scanFiles(folder, out);
            }
        }
        return out;
    }

    private void scanDocumentChildren(Uri treeUri, String documentId, List<MediaEntry> out, Set<String> seen, int depth) {
        if (depth > MAX_SCAN_DEPTH) {
            return;
        }
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanDocumentChildren(treeUri, childId, out, seen, depth + 1);
                    continue;
                }
                C64Native.MediaType type = mediaTypeForName(name);
                if (type == C64Native.MediaType.UNKNOWN) {
                    continue;
                }
                Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                String key = documentUri.toString();
                if (seen.add(key)) {
                    out.add(new MediaEntry(name, documentUri, type));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void scanFiles(File directory, List<MediaEntry> out) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanFiles(file, out);
            } else {
                C64Native.MediaType type = mediaTypeForName(file.getName());
                if (type != C64Native.MediaType.UNKNOWN) {
                    out.add(new MediaEntry(file.getName(), Uri.fromFile(file), type));
                }
            }
        }
    }

    private void beginIgdbMatching() {
        if (mediaLibrary.isEmpty() || !igdbService.hasCredentials()) {
            renderLibrary();
            return;
        }
        igdbNextIndex = 0;
        igdbInFlight = 0;
        pumpIgdbQueue();
    }

    private void pumpIgdbQueue() {
        while (igdbInFlight < MAX_CONCURRENT_IGDB_REQUESTS
                && igdbNextIndex < mediaLibrary.size()
                && igdbNextIndex < MAX_IGDB_LOOKUPS_PER_SCAN) {
            MediaEntry entry = mediaLibrary.get(igdbNextIndex++);
            if (entry.igdbGame != null) {
                if (entry.coverBitmap == null && entry.igdbGame.coverUrl != null && !entry.igdbGame.coverUrl.isBlank()) {
                    entry.igdbLookupStarted = true;
                    igdbInFlight++;
                    loadIgdbCover(entry);
                }
                continue;
            }
            if (entry.igdbLookupStarted) {
                continue;
            }
            entry.igdbLookupStarted = true;
            igdbInFlight++;
            String query = buildIgdbQueryName(entry.displayName);
            igdbService.lookupGame(query, game -> {
                entry.igdbGame = game;
                if (game != null) {
                    saveIgdbMatch(entry, game);
                    igdbService.cacheGame(query, game);
                    loadIgdbCover(entry);
                } else {
                    markIgdbChecked(entry);
                    igdbInFlight--;
                    renderLibrary();
                    pumpIgdbQueue();
                }
            });
        }
    }

    private void loadIgdbCover(MediaEntry entry) {
        if (entry.igdbGame == null || entry.igdbGame.coverUrl == null || entry.igdbGame.coverUrl.isBlank()) {
            igdbInFlight--;
            pumpIgdbQueue();
            return;
        }
        igdbService.loadCover(entry.igdbGame.coverUrl, entry.igdbGame.id, cover -> {
            entry.coverBitmap = cover;
            igdbInFlight--;
            renderLibrary();
            pumpIgdbQueue();
        });
    }

    private void hydrateSavedIgdbMatches(List<MediaEntry> entries) {
        for (MediaEntry entry : entries) {
            String saved = prefs.getString(igdbPrefKey(entry), "");
            IgdbService.IgdbGame game = igdbService.decodeGame(saved);
            if (game != null) {
                entry.igdbGame = game;
                entry.igdbLookupStarted = true;
                continue;
            }
            IgdbService.IgdbGame cached = igdbService.getCachedGame(buildIgdbQueryName(entry.displayName));
            if (cached != null) {
                entry.igdbGame = cached;
                entry.igdbLookupStarted = true;
                saveIgdbMatch(entry, cached);
                continue;
            }
            if (prefs.getBoolean(igdbCheckedPrefKey(entry), false)) {
                entry.igdbLookupStarted = true;
            }
        }
    }

    private void saveIgdbMatch(MediaEntry entry, IgdbService.IgdbGame game) {
        String encoded = igdbService.encodeGame(game);
        if (!encoded.isEmpty()) {
            prefs.edit().putString(igdbPrefKey(entry), encoded).remove(igdbCheckedPrefKey(entry)).apply();
        }
    }

    private void markIgdbChecked(MediaEntry entry) {
        prefs.edit().putBoolean(igdbCheckedPrefKey(entry), true).apply();
    }

    private String igdbPrefKey(MediaEntry entry) {
        return PREF_GAME_IGDB_PREFIX + normalizeTitleKey(displayBaseName(entry.displayName));
    }

    private String igdbCheckedPrefKey(MediaEntry entry) {
        return PREF_GAME_IGDB_CHECKED_PREFIX + normalizeTitleKey(displayBaseName(entry.displayName));
    }

    private String buildIgdbQueryName(String rawName) {
        String value = displayBaseName(rawName).trim();
        int cut = value.length();
        for (char c : new char[] {'(', '[', '{'}) {
            int index = value.indexOf(c);
            if (index >= 0) {
                cut = Math.min(cut, index);
            }
        }
        value = value.substring(0, cut).replace('_', ' ').replace('-', ' ').trim();
        value = value.replaceAll("(?i)\\b(side|disk|disc|tape)\\s*[a-z0-9]+\\b", "").trim();
        return value.isEmpty() ? displayBaseName(rawName) : value;
    }

    private void openGamesFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_GAMES_FOLDER);
    }

    private void openAppFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_APP_FOLDER);
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMPORT_MEDIA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (requestCode == REQUEST_PICK_GAMES_FOLDER) {
            gamesFolderUri = uri;
            prefs.edit().putString(PREF_GAMES_FOLDER_URI, uri.toString()).apply();
            refreshStatusPanel();
            refreshLibrary();
        } else if (requestCode == REQUEST_PICK_APP_FOLDER) {
            appFolderUri = uri;
            prefs.edit().putString(PREF_APP_FOLDER_URI, uri.toString()).apply();
            refreshStatusPanel();
        } else if (requestCode == REQUEST_IMPORT_MEDIA) {
            String name = displayNameForUri(uri);
            C64Native.MediaType type = mediaTypeForName(name);
            if (type == C64Native.MediaType.UNKNOWN) {
                updateStatus("Unsupported C64 file: " + name);
                return;
            }
            MediaEntry entry = new MediaEntry(name, uri, type);
            loadMedia(entry);
        }
    }

    private String displayNameForUri(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null || last.isBlank() ? "c64_media" : last;
    }

    private void toggleSettings() {
        if (settingsPanel == null) {
            return;
        }
        boolean show = settingsPanel.getVisibility() != View.VISIBLE;
        if (show) {
            releaseAllJoystickInputs();
            uiHandler.removeCallbacks(overlayAutoHide);
            showTransientOverlayControls();
        }
        settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            settingsPanel.bringToFront();
        } else {
            scheduleOverlayAutoHide();
        }
        syncEmulationPauseState();
    }

    private void resetC64() {
        if (C64Native.hasRealCore()) {
            C64Native.reset();
        } else if (c64Runtime != null && c64Runtime.isRunning()) {
            c64Runtime.reset();
        } else {
            C64Native.reset();
        }
        updateStatus("C64 reset requested");
    }

    private void resetC64FromSettings() {
        releaseAllJoystickInputs();
        if (settingsPanel != null) {
            settingsPanel.setVisibility(View.GONE);
        }
        if (libraryScreen != null) {
            libraryScreen.setVisibility(View.GONE);
        }
        showTransientOverlayControls();
        if (controlsOverlay != null) {
            controlsOverlay.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
            if (controlsVisible) {
                controlsOverlay.bringToFront();
            }
        }
        if (rootView != null) {
            rootView.post(this::applyDisplayPreferences);
        }
        C64Native.setPaused(activityPaused);
        uiHandler.postDelayed(() -> {
            resetC64();
            syncEmulationPauseState();
        }, 60);
    }

    private void showControllerSettingsDialog() {
        controlsVisible = true;
        if (controlsOverlay != null) {
            controlsOverlay.setVisibility(View.VISIBLE);
            controlsOverlay.bringToFront();
        }
        applyVirtualPadPosition();

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(8));
        panel.setBackground(cardBackground(0xFF15191E, 0xFF46505C));
        scroll.addView(panel);

        TextView hint = new TextView(this);
        hint.setText("Virtual mode shows the D-pad and two buttons. External mode maps crosshair to directions and A/B to button 1/2.");
        hint.setTextColor(0xFFBAC2CC);
        hint.setTextSize(12.0f);
        hint.setPadding(0, 0, 0, dp(8));
        panel.addView(hint);

        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton virtualMode = controllerRadio("Virtual");
        virtualMode.setId(View.generateViewId());
        RadioButton externalMode = controllerRadio("External");
        externalMode.setId(View.generateViewId());
        modeGroup.addView(virtualMode, new RadioGroup.LayoutParams(0, dp(40), 1.0f));
        modeGroup.addView(externalMode, new RadioGroup.LayoutParams(0, dp(40), 1.0f));
        modeGroup.check(prefs.getInt(PREF_CONTROLLER_MODE, CONTROLLER_MODE_VIRTUAL) == CONTROLLER_MODE_EXTERNAL
                ? externalMode.getId()
                : virtualMode.getId());
        panel.addView(modeGroup);

        Spinner positionSpinner = controllerSpinner(new String[] {
                "Bottom corners",
                "Swapped corners",
                "Raised sides",
                "Top corners"
        });
        positionSpinner.setSelection(prefs.getInt(PREF_VIRTUAL_PAD_POSITION, 0));
        panel.addView(labeledControl("Position", positionSpinner));

        Spinner virtualASpinner = controllerSpinner(VIRTUAL_BUTTON_KEY_CHOICES);
        virtualASpinner.setSelection(indexForChoice(VIRTUAL_BUTTON_KEY_CHOICES,
                prefs.getInt(PREF_VIRTUAL_BUTTON_1_KEY, KEY_MAPPING_DEFAULT)));
        panel.addView(labeledControl("Button A", virtualASpinner));

        Spinner virtualBSpinner = controllerSpinner(VIRTUAL_BUTTON_KEY_CHOICES);
        virtualBSpinner.setSelection(indexForChoice(VIRTUAL_BUTTON_KEY_CHOICES,
                prefs.getInt(PREF_VIRTUAL_BUTTON_2_KEY, KEY_MAPPING_DEFAULT)));
        panel.addView(labeledControl("Button B", virtualBSpinner));

        LinearLayout externalSection = new LinearLayout(this);
        externalSection.setOrientation(LinearLayout.VERTICAL);
        panel.addView(externalSection);

        TextView externalTitle = new TextView(this);
        externalTitle.setText("External: " + controllerMappingGameLabel());
        externalTitle.setTextColor(Color.WHITE);
        externalTitle.setTextSize(13.0f);
        externalTitle.setPadding(0, dp(10), 0, dp(4));
        externalSection.addView(externalTitle);

        TextView externalHint = new TextView(this);
        externalHint.setText("Crosshair/D-pad = directions. A = button 1. B = button 2. Assign keys to the other buttons only if this game needs them.");
        externalHint.setTextColor(0xFFBAC2CC);
        externalHint.setTextSize(11.0f);
        externalHint.setPadding(0, 0, 0, dp(6));
        externalSection.addView(externalHint);

        List<Spinner> externalSpinners = new ArrayList<>();
        for (ControllerButtonMapping button : EXTERNAL_EXTRA_BUTTONS) {
            Spinner spinner = controllerSpinner(EXTERNAL_EXTRA_KEY_CHOICES);
            spinner.setSelection(indexForChoice(EXTERNAL_EXTRA_KEY_CHOICES,
                    prefs.getInt(externalGameMappingPref(button.keyCode), KEY_MAPPING_NONE)));
            externalSpinners.add(spinner);
            externalSection.addView(labeledControl(button.label, spinner));
        }
        externalSection.setVisibility(modeGroup.getCheckedRadioButtonId() == externalMode.getId()
                ? View.VISIBLE
                : View.GONE);
        modeGroup.setOnCheckedChangeListener((group, checkedId) ->
                externalSection.setVisibility(checkedId == externalMode.getId()
                        ? View.VISIBLE
                        : View.GONE));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Controller")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    int selectedMode = modeGroup.getCheckedRadioButtonId() == externalMode.getId()
                            ? CONTROLLER_MODE_EXTERNAL
                            : CONTROLLER_MODE_VIRTUAL;
                    editor.putInt(PREF_CONTROLLER_MODE, selectedMode);
                    editor.putInt(PREF_VIRTUAL_PAD_POSITION, positionSpinner.getSelectedItemPosition());
                    editor.putInt(PREF_VIRTUAL_BUTTON_1_KEY,
                            ((ControllerKeyChoice) virtualASpinner.getSelectedItem()).value);
                    editor.putInt(PREF_VIRTUAL_BUTTON_2_KEY,
                            ((ControllerKeyChoice) virtualBSpinner.getSelectedItem()).value);
                    for (int i = 0; i < EXTERNAL_EXTRA_BUTTONS.length; i++) {
                        ControllerButtonMapping button = EXTERNAL_EXTRA_BUTTONS[i];
                        ControllerKeyChoice choice = (ControllerKeyChoice) externalSpinners.get(i).getSelectedItem();
                        editor.putInt(externalGameMappingPref(button.keyCode), choice.value);
                    }
                    editor.apply();
                    applyVirtualPadPosition();
                    controlsVisible = selectedMode == CONTROLLER_MODE_VIRTUAL;
                    if (controlsOverlay != null) {
                        controlsOverlay.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
                        if (controlsVisible) {
                            controlsOverlay.bringToFront();
                        }
                    }
                    updateStatus(selectedMode == CONTROLLER_MODE_EXTERNAL
                            ? "External controller saved for " + controllerMappingGameLabel()
                            : "Virtual controller settings saved.");
                })
                .create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private <T> Spinner controllerSpinner(T[] items) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<T> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private RadioButton controllerRadio(String label) {
        RadioButton radio = new RadioButton(this);
        radio.setText(label);
        radio.setTextColor(Color.WHITE);
        radio.setTextSize(13.0f);
        radio.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFE1E6ED));
        return radio;
    }

    private View labeledControl(String label, View control) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xFFE1E6ED);
        labelView.setTextSize(12.0f);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(control, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        return row;
    }

    private int indexForChoice(ControllerKeyChoice[] choices, int value) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].value == value) {
                return i;
            }
        }
        return 0;
    }

    private String externalGameMappingPref(int keyCode) {
        return PREF_EXTERNAL_GAME_KEY_PREFIX + controllerMappingGameKey() + "_" + keyCode;
    }

    private String controllerMappingGameLabel() {
        return currentMediaName == null || currentMediaName.isBlank()
                ? "current game"
                : currentMediaName;
    }

    private String controllerMappingGameKey() {
        String name = currentMediaName == null || currentMediaName.isBlank()
                ? "global"
                : currentMediaName;
        return name.toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]+", "_");
    }

    private void hideSettingsPanel() {
        hideSettingsPanel(true);
    }

    private void hideSettingsPanel(boolean syncPause) {
        if (settingsPanel != null) {
            settingsPanel.setVisibility(View.GONE);
        }
        showTransientOverlayControls();
        if (syncPause) {
            syncEmulationPauseState();
        }
    }

    private void syncEmulationPauseState() {
        C64Native.setPaused(activityPaused || isPauseOverlayVisible());
    }

    private boolean isPauseOverlayVisible() {
        return (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE)
                || (libraryScreen != null && libraryScreen.getVisibility() == View.VISIBLE);
    }

    private void releaseAllJoystickInputs() {
        if (dpadView != null) {
            dpadView.release();
        }
        virtualJoystickMask = 0;
        gamepadAxisMask = 0;
        gamepadButtonMask = 0;
        updateJoystickState();
    }

    private void toggleControls() {
        if (controlsOverlay == null) {
            return;
        }
        controlsVisible = !controlsVisible;
        if (!controlsVisible) {
            releaseAllJoystickInputs();
        }
        controlsOverlay.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        if (controlsVisible) {
            controlsOverlay.bringToFront();
        }
        if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
            settingsPanel.bringToFront();
        }
        if (playBar != null) {
            playBar.bringToFront();
        }
        scheduleOverlayAutoHide();
    }

    private void toggleKeyboard() {
        if (keyboardOverlay == null) {
            return;
        }
        keyboardVisible = !keyboardVisible;
        keyboardOverlay.setVisibility(keyboardVisible ? View.VISIBLE : View.GONE);
        if (keyboardVisible) {
            keyboardOverlay.bringToFront();
        }
        if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
            settingsPanel.bringToFront();
        }
        if (playBar != null) {
            playBar.bringToFront();
        }
        if (tapePanelToggleButton != null && tapePanelToggleButton.getVisibility() == View.VISIBLE) {
            tapePanelToggleButton.bringToFront();
        }
        if (diskPanelToggleButton != null && diskPanelToggleButton.getVisibility() == View.VISIBLE) {
            diskPanelToggleButton.bringToFront();
        }
        scheduleOverlayAutoHide();
    }

    private void toggleViewMode() {
        int next = prefs.getInt(PREF_VIEW_STYLE, VIEW_GRID) == VIEW_GRID ? VIEW_CAROUSEL : VIEW_GRID;
        prefs.edit().putInt(PREF_VIEW_STYLE, next).apply();
        renderLibrary();
    }

    private void toggleAspect() {
        aspectMode = aspectMode == ASPECT_4_3 ? ASPECT_16_9 : ASPECT_4_3;
        prefs.edit()
                .putInt(PREF_ASPECT_MODE, aspectMode)
                .putBoolean(PREF_CRT_ENABLED, crtEnabled)
                .putBoolean(PREF_BEZEL_ENABLED, bezelEnabled)
                .apply();
        if (bezelEnabled && aspectMode == ASPECT_16_9) {
            updateStatus("Bezel hidden in 16:9 mode.");
        }
        applyDisplayPreferences();
    }

    private void toggleCrt() {
        crtEnabled = !crtEnabled;
        prefs.edit()
                .putBoolean(PREF_CRT_ENABLED, crtEnabled)
                .putBoolean(PREF_CRT_USER_SET, true)
                .putInt(PREF_ASPECT_MODE, aspectMode)
                .apply();
        applyDisplayPreferences();
    }

    private void toggleBezel() {
        bezelEnabled = !bezelEnabled;
        prefs.edit()
                .putBoolean(PREF_BEZEL_ENABLED, bezelEnabled)
                .putInt(PREF_ASPECT_MODE, aspectMode)
                .apply();
        applyDisplayPreferences();
        if (bezelEnabled && aspectMode == ASPECT_16_9) {
            updateStatus("Bezel will apply in 4:3 mode.");
            return;
        }
        if (bezelEnabled && !currentMediaName.isBlank()
                && bezelOverlay != null && bezelOverlay.getVisibility() != View.VISIBLE) {
            updateStatus("No matching bezel: " + currentMediaName);
        }
    }

    private void toggleTapeAutostart() {
        boolean enabled = !prefs.getBoolean(PREF_TAPE_AUTOSTART, true);
        prefs.edit().putBoolean(PREF_TAPE_AUTOSTART, enabled).apply();
        currentTapeAutostart = enabled;
        if (currentMediaType == C64Native.MediaType.TAPE) {
            showTapePrompt(currentMediaName, enabled);
        }
        applyDisplayPreferences();
    }

    private void toggleTapeTurbo() {
        boolean enabled = !prefs.getBoolean(PREF_TAPE_TURBO, false);
        prefs.edit().putBoolean(PREF_TAPE_TURBO, enabled).apply();
        C64Native.setTapeTurbo(currentMediaType == C64Native.MediaType.TAPE && enabled);
        updateStatus(enabled ? "Tape turbo on." : "Tape turbo off.");
        applyDisplayPreferences();
    }

    private String aspectLabel() {
        return aspectMode == ASPECT_16_9 ? "16:9" : "4:3";
    }

    private String crtLabel() {
        return crtEnabled ? "CRT On" : "CRT Off";
    }

    private String bezelLabel() {
        if (bezelEnabled && aspectMode == ASPECT_16_9) {
            return "Bezel 4:3";
        }
        return bezelEnabled ? "Bezel On" : "Bezel Off";
    }

    private String tapeAutostartLabel() {
        return prefs != null && prefs.getBoolean(PREF_TAPE_AUTOSTART, true) ? "Tape Auto" : "Tape Manual";
    }

    private String tapeTurboLabel() {
        return prefs != null && prefs.getBoolean(PREF_TAPE_TURBO, false) ? "Turbo On" : "Turbo Off";
    }

    private void applyTapeTurboPreference() {
        C64Native.setTapeTurbo(prefs != null && prefs.getBoolean(PREF_TAPE_TURBO, false));
    }

    private void applyDisplayPreferences() {
        if (screenContainer == null || rootView == null) {
            return;
        }
        if (rootView.getWidth() <= 0 || rootView.getHeight() <= 0) {
            rootView.post(this::applyDisplayPreferences);
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) screenContainer.getLayoutParams();
        int rootW = rootView.getWidth();
        int rootH = rootView.getHeight();
        int topInset = topBarInset();
        int availableH = Math.max(dp(120), rootH - topInset);
        File activeBezel = shouldShowBezel() ? findMatchingBezel(currentMediaName) : null;
        float containerAspect = activeBezel != null ? 16.0f / 9.0f
                : (aspectMode == ASPECT_16_9 ? 16.0f / 9.0f : 4.0f / 3.0f);
        int targetW = rootW;
        int targetH = Math.round(targetW / containerAspect);
        if (targetH > availableH) {
            targetH = availableH;
            targetW = Math.round(targetH * containerAspect);
        }
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = topInset;
        params.bottomMargin = 0;
        params.leftMargin = 0;
        params.rightMargin = 0;
        params.width = targetW;
        params.height = targetH;
        screenContainer.setLayoutParams(params);
        applyRenderViewport(targetW, targetH, activeBezel != null);
        C64Native.setDisplayOptions(aspectMode, crtEnabled, activeBezel != null);
        if (crtOverlay != null) {
            crtOverlay.setVisibility(crtEnabled ? View.VISIBLE : View.GONE);
            if (crtEnabled) {
                crtOverlay.bringToFront();
            }
        }
        updateBezelOverlay(activeBezel);
        if (crtOverlay != null && crtEnabled) {
            crtOverlay.bringToFront();
        }
        if (aspectButton != null) {
            aspectButton.setText(aspectLabel());
        }
        if (crtButton != null) {
            crtButton.setText(crtLabel());
        }
        if (bezelButton != null) {
            bezelButton.setText(bezelLabel());
        }
        if (tapePanelAutostartButton != null) {
            tapePanelAutostartButton.setText(tapeAutostartLabel());
        }
        if (tapePanelTurboButton != null) {
            tapePanelTurboButton.setText(tapeTurboLabel());
        }
        if (launcherAspectButton != null) {
            launcherAspectButton.setText(aspectLabel());
        }
        if (launcherCrtButton != null) {
            launcherCrtButton.setText(crtLabel());
        }
        if (launcherBezelButton != null) {
            launcherBezelButton.setText(bezelLabel());
        }
        if (launcherTapeAutostartButton != null) {
            launcherTapeAutostartButton.setText(tapeAutostartLabel());
        }
        if (launcherTapeTurboButton != null) {
            launcherTapeTurboButton.setText(tapeTurboLabel());
        }
        if (playBar != null) {
            playBar.bringToFront();
        }
        if (fpsView != null) {
            fpsView.bringToFront();
        }
        updateDiskButtonVisibility(true);
    }

    private void applyRenderViewport(int containerWidth, int containerHeight, boolean hasBezel) {
        if (renderView == null || containerWidth <= 0 || containerHeight <= 0) {
            return;
        }
        float renderAspect = aspectMode == ASPECT_16_9 ? 16.0f / 9.0f : 4.0f / 3.0f;
        int width = containerWidth;
        int height = Math.round(width / renderAspect);
        if (height > containerHeight) {
            height = containerHeight;
            width = Math.round(height * renderAspect);
        }
        FrameLayout.LayoutParams renderParams = (FrameLayout.LayoutParams) renderView.getLayoutParams();
        renderParams.gravity = Gravity.CENTER;
        renderParams.width = Math.max(1, width);
        renderParams.height = Math.max(1, height);
        renderParams.leftMargin = 0;
        renderParams.topMargin = 0;
        renderParams.rightMargin = 0;
        renderParams.bottomMargin = 0;
        renderView.setLayoutParams(renderParams);
    }

    private boolean shouldShowBezel() {
        return bezelEnabled && aspectMode == ASPECT_4_3;
    }

    private TextView makeFpsView() {
        TextView view = new TextView(this);
        view.setText("0 FPS");
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setMinWidth(dp(62));
        view.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xAA262A30);
        bg.setCornerRadius(dp(4));
        view.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        params.setMargins(dp(4), 0, dp(4), 0);
        view.setLayoutParams(params);
        return view;
    }

    private void updateFpsLabel() {
        if (fpsView == null) {
            return;
        }
        int fps = C64Native.getFps();
        fpsView.setText(fps > 0 ? fps + " FPS" : "-- FPS");
    }

    private int topBarInset() {
        if (playBar == null || playBar.getVisibility() == View.GONE) {
            return 0;
        }
        int height = playBar.getHeight() > 0 ? playBar.getHeight() : playBar.getMeasuredHeight();
        if (height <= 0) {
            height = dp(52);
        }
        int topMargin = 0;
        if (playBar.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            topMargin = ((FrameLayout.LayoutParams) playBar.getLayoutParams()).topMargin;
        }
        return topMargin + height + dp(8);
    }

    private void rerunSetupWizard() {
        startActivity(new Intent(this, SetupWizardActivity.class));
        finish();
    }

    private void showAdvancedViceSettings() {
        showViceResourcePickerDialog("Advanced VICE",
                "Choose a VICE setting, select a value, then apply it to the running core.",
                ADVANCED_RESOURCE_OPTIONS,
                true);
    }

    private void showDiskDriveOptionsDialog() {
        showViceResourcePickerDialog("Disk Drive 8",
                currentMediaName == null || currentMediaName.isBlank()
                        ? "Drive settings for the running disk."
                        : currentMediaName + "\nDrive settings for the running disk.",
                DISK_DRIVE_OPTIONS,
                false);
    }

    private void showViceResourcePickerDialog(String titleText, String detailText,
                                              AdvancedResourceOption[] options,
                                              boolean includeCustomButton) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(14), dp(16), dp(14));
        container.setBackground(cardBackground(0xFF15191E, 0xFF46505C));
        scrollView.addView(container, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(16.0f);
        container.addView(title);

        TextView detail = new TextView(this);
        detail.setText(detailText);
        detail.setTextColor(0xFFBAC2CC);
        detail.setTextSize(11.0f);
        detail.setPadding(0, dp(3), 0, dp(10));
        container.addView(detail);

        TextView resultView = new TextView(this);
        resultView.setText("Choose a setting.");
        resultView.setTextColor(0xFFE1E6ED);
        resultView.setTextSize(12.0f);
        resultView.setPadding(0, 0, 0, dp(8));

        Spinner resourceSpinner = new Spinner(this);
        ArrayAdapter<AdvancedResourceOption> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resourceSpinner.setAdapter(adapter);
        container.addView(resourceSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        RadioGroup choiceGroup = new RadioGroup(this);
        choiceGroup.setOrientation(RadioGroup.VERTICAL);
        LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        choiceParams.setMargins(0, dp(8), 0, dp(8));
        container.addView(choiceGroup, choiceParams);
        container.addView(resultView);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.setMargins(0, dp(10), 0, 0);
        container.addView(actionRow, actionParams);

        actionRow.addView(makeCompactButton("Read", v -> {
            AdvancedResourceOption option = (AdvancedResourceOption) resourceSpinner.getSelectedItem();
            String result = C64Native.getViceResource(option.resource);
            showViceResourceResult(resultView, result);
            populateResourceChoices(choiceGroup, option, parseResourceInt(result));
        }), new LinearLayout.LayoutParams(0, dp(38), 1.0f));

        LinearLayout.LayoutParams spacedButton = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        spacedButton.setMargins(dp(8), 0, 0, 0);
        actionRow.addView(makeCompactButton("Apply", v -> {
            AdvancedResourceOption option = (AdvancedResourceOption) resourceSpinner.getSelectedItem();
            ResourceChoice choice = selectedResourceChoice(choiceGroup);
            if (choice == null) {
                resultView.setText("Choose a value");
                return;
            }
            applyViceResourceInt(option.resource, choice.value, resultView);
        }), spacedButton);

        if (includeCustomButton) {
            LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(0, dp(38), 1.0f);
            customParams.setMargins(dp(8), 0, 0, 0);
            actionRow.addView(makeCompactButton("Custom", v -> showCustomViceResourceDialog(resultView)),
                    customParams);
        }

        resourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AdvancedResourceOption option = options[position];
                String result = C64Native.getViceResource(option.resource);
                populateResourceChoices(choiceGroup, option, parseResourceInt(result));
                showViceResourceResult(resultView, result);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scrollView)
                .create();
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private void populateResourceChoices(RadioGroup group, AdvancedResourceOption option, Integer currentValue) {
        group.removeAllViews();
        for (int i = 0; i < option.choices.length; i++) {
            ResourceChoice choice = option.choices[i];
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setText(choice.label);
            radio.setTextColor(Color.WHITE);
            radio.setTextSize(13.0f);
            radio.setTag(choice);
            radio.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFE1E6ED));
            group.addView(radio, new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    dp(36)));
            if ((currentValue != null && currentValue == choice.value)
                    || (currentValue == null && i == 0)) {
                radio.setChecked(true);
            }
        }
    }

    private ResourceChoice selectedResourceChoice(RadioGroup group) {
        int checkedId = group.getCheckedRadioButtonId();
        if (checkedId == -1) {
            return null;
        }
        View checked = group.findViewById(checkedId);
        return checked != null && checked.getTag() instanceof ResourceChoice
                ? (ResourceChoice) checked.getTag()
                : null;
    }

    private Integer parseResourceInt(String result) {
        if (result == null) {
            return null;
        }
        int equals = result.indexOf('=');
        if (equals < 0 || equals + 1 >= result.length()) {
            return null;
        }
        try {
            return Integer.parseInt(result.substring(equals + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showCustomViceResourceDialog(TextView parentResultView) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(14), dp(16), dp(6));
        layout.setBackground(cardBackground(0xFF15191E, 0xFF46505C));

        EditText resourceInput = new EditText(this);
        resourceInput.setSingleLine(true);
        resourceInput.setTextColor(Color.WHITE);
        resourceInput.setHintTextColor(0xFF8C939D);
        resourceInput.setHint("Resource name");
        layout.addView(resourceInput);

        EditText valueInput = new EditText(this);
        valueInput.setSingleLine(true);
        valueInput.setTextColor(Color.WHITE);
        valueInput.setHintTextColor(0xFF8C939D);
        valueInput.setHint("Value");
        layout.addView(valueInput);

        new AlertDialog.Builder(this)
                .setTitle("Custom VICE Resource")
                .setView(layout)
                .setNegativeButton("Read", (dialog, which) -> {
                    String resource = resourceInput.getText().toString().trim();
                    showViceResourceResult(parentResultView, C64Native.getViceResource(resource));
                })
                .setNeutralButton("Set Text", (dialog, which) -> {
                    String resource = resourceInput.getText().toString().trim();
                    boolean ok = C64Native.setViceResourceString(resource, valueInput.getText().toString());
                    showViceResourceResult(parentResultView, ok
                            ? C64Native.getViceResource(resource)
                            : "Set failed: " + resource);
                })
                .setPositiveButton("Set Int", (dialog, which) -> {
                    String resource = resourceInput.getText().toString().trim();
                    try {
                        applyViceResourceInt(resource,
                                Integer.parseInt(valueInput.getText().toString().trim()),
                                parentResultView);
                    } catch (NumberFormatException e) {
                        showViceResourceResult(parentResultView, "Value is not an integer");
                    }
                })
                .show();
    }

    private void applyViceResourceInt(String resource, int value, TextView resultView) {
        boolean ok = C64Native.setViceResourceInt(resource, value);
        showViceResourceResult(resultView, ok
                ? C64Native.getViceResource(resource)
                : "Set failed: " + resource);
    }

    private void showViceResourceResult(TextView resultView, String result) {
        String message = result == null || result.isEmpty() ? "No result" : result;
        resultView.setText(message);
        updateStatus(message);
    }

    private void showDebugInfo() {
        String message = "App folder: " + (appFolderUri == null ? "not selected" : folderLabel(appFolderUri))
                + "\nGames folder: " + (gamesFolderUri == null ? "not selected" : folderLabel(gamesFolderUri))
                + "\nGames: " + mediaLibrary.size()
                + "\nIGDB: " + (igdbService != null && igdbService.hasCredentials() ? "ready" : "credentials missing")
                + "\nC64 data: " + c64DataDir().getAbsolutePath()
                + "\nC64 runtime: " + (c64Runtime == null ? "not initialized" : c64Runtime.toString())
                + "\nPRG loading: autostart PRG path is first priority";
        new AlertDialog.Builder(this)
                .setTitle("Debug Info")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showDownloadBezelsDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(12), dp(24), 0);
        layout.setBackgroundColor(0xFF101418);

        TextView hint = new TextView(this);
        hint.setText("Download per-game C64 bezel PNGs into the selected app folder.");
        hint.setTextColor(0xFFE8EAED);
        hint.setTextSize(13.0f);
        layout.addView(hint);

        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF8C939D);
        input.setText(prefs.getString(PREF_BEZEL_GITHUB_URL, BezelDownloader.DEFAULT_GITHUB_ZIP_URL));
        layout.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Download Bezels")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Download", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (url.isEmpty()) {
                        Toast.makeText(this, "GitHub ZIP URL is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs.edit().putString(PREF_BEZEL_GITHUB_URL, url).apply();
                    downloadBezelsFromGithub(url);
                })
                .show();
    }

    private void downloadBezelsFromGithub(String url) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(18), dp(24), dp(8));
        layout.setBackgroundColor(0xFF101418);

        TextView status = new TextView(this);
        status.setText("Starting download...");
        status.setTextColor(Color.WHITE);
        status.setTextSize(14.0f);
        layout.addView(status);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(18));
        progressParams.setMargins(0, dp(14), 0, dp(8));
        layout.addView(progress, progressParams);

        TextView detail = new TextView(this);
        detail.setTextColor(0xFFBAC2CC);
        detail.setTextSize(12.0f);
        layout.addView(detail);

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Downloading Bezels")
                .setView(layout)
                .setCancelable(false)
                .create();
        progressDialog.setOnShowListener(dialog -> {
            if (progressDialog.getWindow() != null) {
                progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        progressDialog.show();

        new Thread(() -> {
            try {
                BezelDownloader.Result result = BezelDownloader.downloadGithubZip(this, url,
                        (phase, current, total, indeterminate) -> runOnUiThread(() ->
                                updateBezelDownloadProgress(status, detail, progress, phase, current, total, indeterminate)));
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    invalidateBezelCache();
                    applyDisplayPreferences();
                    Toast.makeText(this, "Downloaded " + result.pngCount + " bezels", Toast.LENGTH_LONG).show();
                    refreshStatusPanel();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Bezel download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "ViceC64BezelDownload").start();
    }

    private void updateBezelDownloadProgress(TextView status, TextView detail, ProgressBar progress,
                                             String phase, long current, long total, boolean indeterminate) {
        status.setText(phase);
        progress.setIndeterminate(indeterminate);
        if (!indeterminate && total > 0) {
            progress.setMax((int) Math.min(total, Integer.MAX_VALUE));
            progress.setProgress((int) Math.min(current, Integer.MAX_VALUE));
            detail.setText(formatProgressValue(current, total));
        } else if (current > 0) {
            detail.setText(formatBytes(current));
        } else {
            detail.setText("Working...");
        }
    }

    private String formatProgressValue(long current, long total) {
        if (total <= 0) {
            return formatBytes(current);
        }
        if (total < 2048) {
            return current + " / " + total;
        }
        return formatBytes(current) + " / " + formatBytes(total);
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        }
        return bytes + " B";
    }

    private void refreshStatusPanel() {
        if (statusView == null) {
            return;
        }
        String gamesFolder = gamesFolderUri == null ? "not selected" : folderLabel(gamesFolderUri);
        String appFolder = appFolderUri == null ? "not selected" : folderLabel(appFolderUri);
        statusView.setText("App folder: " + appFolder
                + "\nGames folder: " + gamesFolder
                + "\nIGDB: " + (igdbService != null && igdbService.hasCredentials() ? "ready" : "credentials missing")
                + "\nC64 data: " + c64DataDir().getAbsolutePath()
                + "\nC64 runtime: " + (c64Runtime != null && c64Runtime.hasExecutable() ? "available" : "missing")
                + "\nPriority path: PRG autostart first, then disk/tape/cart attach.");
    }

    private void updateStatus(String message) {
        if (statusView != null) {
            statusView.setText(message);
        }
        if (mediaStatusView != null) {
            mediaStatusView.setText(message);
        }
    }

    private Button makeCompactButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12.0f);
        button.setMinHeight(dp(36));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(cardBackground(0xFF242A31, 0xFF46505C));
        if (listener != null) {
            button.setOnClickListener(listener);
        }
        return button;
    }

    private Button makeTopButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(12.0f);
        button.setMinWidth(dp(56));
        button.setMinimumWidth(dp(56));
        button.setMinHeight(dp(36));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(8), 0, dp(8), 0);
        if (listener != null) {
            button.setOnClickListener(listener);
        }
        return button;
    }

    private View makeSettingsAction(String icon, String title, String detail, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBackground(0xFF22272E, 0xFF3D4652));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(listener);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextColor(Color.WHITE);
        iconView.setTextSize(22.0f);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(cardBackground(0xFF303844, 0xFF596474));
        card.addView(iconView, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(14.0f);
        titleView.setMaxLines(1);
        texts.addView(titleView);
        TextView detailView = new TextView(this);
        detailView.setText(detail);
        detailView.setTextColor(0xFFBAC2CC);
        detailView.setTextSize(11.0f);
        detailView.setMaxLines(2);
        texts.addView(detailView);
        card.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private GradientDrawable cardBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(6));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private boolean matchesSearch(String title, String query) {
        return query == null || query.isBlank()
                || title.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US).trim());
    }

    private void updateBezelOverlay(File activeBezel) {
        if (bezelOverlay == null) {
            return;
        }
        if (activeBezel == null || !activeBezel.isFile()) {
            bezelOverlay.setVisibility(View.GONE);
            bezelOverlay.setImageDrawable(null);
            bezelOverlay.setTag(null);
            return;
        }
        String path = activeBezel.getAbsolutePath();
        Object tag = bezelOverlay.getTag();
        if (!(tag instanceof String) || !path.equals(tag)) {
            bezelOverlay.setImageURI(Uri.fromFile(activeBezel));
            bezelOverlay.setTag(path);
        }
        bezelOverlay.setVisibility(View.VISIBLE);
        bezelOverlay.bringToFront();
    }

    private File findMatchingBezel(String mediaName) {
        String target = normalizeBezelName(displayBaseName(mediaName == null ? "" : mediaName));
        if (target.isEmpty()) {
            return null;
        }
        File root = downloadedBezelRoot();
        ensureBezelIndex(root);
        File exact = bezelIndex.get(target);
        if (exact != null && exact.isFile()) {
            return exact;
        }

        File best = null;
        int bestScore = 0;
        for (Map.Entry<String, File> entry : bezelIndex.entrySet()) {
            File candidateFile = entry.getValue();
            if (candidateFile == null || !candidateFile.isFile()) {
                continue;
            }
            int score = bezelMatchScore(target, entry.getKey());
            if (score > bestScore) {
                bestScore = score;
                best = candidateFile;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private File downloadedBezelRoot() {
        return new File(new File(BezelDownloader.resolveAppStorage(this), "bezels/github"), "latest");
    }

    private void ensureBezelIndex(File root) {
        String path = root == null ? "" : root.getAbsolutePath();
        if (path.equals(bezelIndexRootPath)) {
            return;
        }
        bezelIndex.clear();
        bezelIndexRootPath = path;
        if (root == null || !root.isDirectory()) {
            return;
        }
        collectBezelPngs(root, 0);
    }

    private void collectBezelPngs(File directory, int depth) {
        if (directory == null || depth > MAX_SCAN_DEPTH) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectBezelPngs(child, depth + 1);
                continue;
            }
            String name = child.getName();
            if (!name.toLowerCase(Locale.US).endsWith(".png")) {
                continue;
            }
            String key = normalizeBezelName(displayBaseName(name));
            if (!key.isEmpty() && !bezelIndex.containsKey(key)) {
                bezelIndex.put(key, child);
            }
        }
    }

    private void invalidateBezelCache() {
        bezelIndex.clear();
        bezelIndexRootPath = "";
        if (bezelOverlay != null) {
            bezelOverlay.setTag(null);
        }
    }

    private static int bezelMatchScore(String target, String candidate) {
        if (target.equals(candidate)) {
            return 10000;
        }
        if (target.length() < 4 || candidate.length() < 4) {
            return 0;
        }
        String paddedTarget = " " + target + " ";
        String paddedCandidate = " " + candidate + " ";
        int distance = Math.abs(target.length() - candidate.length());
        if (paddedCandidate.contains(paddedTarget)) {
            return 8000 - distance;
        }
        if (paddedTarget.contains(paddedCandidate)) {
            return 7000 - distance;
        }
        return 0;
    }

    private static String normalizeBezelName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.toLowerCase(Locale.US)
                .replaceAll("\\[[^\\]]*\\]", " ")
                .replaceAll("\\([^)]*\\)", " ")
                .replace('&', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private String metadataDetail(MediaEntry entry) {
        if (entry.igdbGame == null) {
            return fileExtensionLabel(entry.displayName);
        }
        StringBuilder out = new StringBuilder();
        if (entry.igdbGame.releaseDate != null && !entry.igdbGame.releaseDate.isBlank()) {
            out.append(entry.igdbGame.releaseDate);
        }
        if (entry.igdbGame.publisher != null && !entry.igdbGame.publisher.isBlank()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            out.append(entry.igdbGame.publisher);
        }
        return out.length() == 0 ? fileExtensionLabel(entry.displayName) : out.toString();
    }

    private static String displayBaseName(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private static String fileExtensionLabel(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toUpperCase(Locale.US) : "C64";
    }

    private static String normalizeTitleKey(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.length() == 0 ? Integer.toHexString(value.hashCode()) : out.toString();
    }

    private static String sanitizeFileName(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "c64_media" : cleaned;
    }


    private static C64Native.MediaType mediaTypeForName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".prg") || lower.endsWith(".p00")) {
            return C64Native.MediaType.PRG;
        }
        if (lower.endsWith(".d64") || lower.endsWith(".g64") || lower.endsWith(".d71") || lower.endsWith(".d81")
                || lower.endsWith(".x64") || lower.endsWith(".d80") || lower.endsWith(".d82")) {
            return C64Native.MediaType.DISK;
        }
        if (lower.endsWith(".tap")) {
            return C64Native.MediaType.TAPE;
        }
        if (lower.endsWith(".crt") || lower.endsWith(".bin")) {
            return C64Native.MediaType.CARTRIDGE;
        }
        return C64Native.MediaType.UNKNOWN;
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            enterImmersiveMode();
            showTransientOverlayControls();
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean handleOverlayTouch(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            enterImmersiveMode();
            showTransientOverlayControls();
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isGameControllerEvent(event)) {
            if (isExternalControllerMode()) {
                int action = event.getAction();
                if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
                    C64Native.C64Key key = externalMappedKey(event.getKeyCode());
                    if (key != null) {
                        C64Native.setKey(key, action == KeyEvent.ACTION_DOWN);
                        return true;
                    }
                    if (isExternalExtraButton(event.getKeyCode())) {
                        return true;
                    }
                }
            }
            int bit = isExternalControllerMode()
                    ? mapExternalGamepadKeyToJoystickBit(event.getKeyCode())
                    : mapGamepadKeyToJoystickBit(event.getKeyCode());
            if (bit != 0 && (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)) {
                setGamepadButtonBit(bit, event.getAction() == KeyEvent.ACTION_DOWN);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && event.getAction() == MotionEvent.ACTION_MOVE) {
            int mask = 0;
            float x = getCenteredAxis(event, MotionEvent.AXIS_X);
            float y = getCenteredAxis(event, MotionEvent.AXIS_Y);
            float hatX = getCenteredAxis(event, MotionEvent.AXIS_HAT_X);
            float hatY = getCenteredAxis(event, MotionEvent.AXIS_HAT_Y);
            if (Math.abs(hatX) > Math.abs(x)) {
                x = hatX;
            }
            if (Math.abs(hatY) > Math.abs(y)) {
                y = hatY;
            }
            if (x < -0.45f) {
                mask |= JOY_LEFT;
            } else if (x > 0.45f) {
                mask |= JOY_RIGHT;
            }
            if (y < -0.45f) {
                mask |= JOY_UP;
            } else if (y > 0.45f) {
                mask |= JOY_DOWN;
            }
            if (!isExternalControllerMode()) {
                if (getCenteredAxis(event, MotionEvent.AXIS_RTRIGGER) > 0.45f
                        || getCenteredAxis(event, MotionEvent.AXIS_GAS) > 0.45f) {
                    mask |= JOY_FIRE;
                }
                if (getCenteredAxis(event, MotionEvent.AXIS_LTRIGGER) > 0.45f
                        || getCenteredAxis(event, MotionEvent.AXIS_BRAKE) > 0.45f) {
                    mask |= JOY_FIRE_2;
                }
            }
            gamepadAxisMask = mask;
            updateJoystickState();
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private void setGamepadButtonBit(int bit, boolean pressed) {
        if (pressed) {
            gamepadButtonMask |= bit;
        } else {
            gamepadButtonMask &= ~bit;
        }
        updateJoystickState();
    }

    private void updateJoystickState() {
        int nextMask = virtualJoystickMask | gamepadAxisMask | gamepadButtonMask;
        if (nextMask == joystickMask) {
            return;
        }
        joystickMask = nextMask;
        C64Native.setJoystick(2, joystickMask);
    }

    private C64Native.C64Key c64KeyForOrdinal(int ordinal) {
        C64Native.C64Key[] keys = C64Native.C64Key.values();
        return ordinal >= 0 && ordinal < keys.length ? keys[ordinal] : null;
    }

    private boolean isExternalControllerMode() {
        return prefs != null && prefs.getInt(PREF_CONTROLLER_MODE, CONTROLLER_MODE_VIRTUAL) == CONTROLLER_MODE_EXTERNAL;
    }

    private C64Native.C64Key externalMappedKey(int keyCode) {
        int value = prefs == null ? KEY_MAPPING_NONE
                : prefs.getInt(externalGameMappingPref(keyCode), KEY_MAPPING_NONE);
        return c64KeyForOrdinal(value);
    }

    private boolean isExternalExtraButton(int keyCode) {
        for (ControllerButtonMapping button : EXTERNAL_EXTRA_BUTTONS) {
            if (button.keyCode == keyCode) {
                return true;
            }
        }
        return false;
    }

    private boolean isGameControllerEvent(KeyEvent event) {
        int source = event.getSource();
        return ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                || ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
                || ((source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD);
    }

    private float getCenteredAxis(MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        float value = event.getAxisValue(axis);
        if (device == null) {
            return value;
        }
        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range != null && Math.abs(value) <= range.getFlat()) {
            return 0.0f;
        }
        return value;
    }

    private int mapGamepadKeyToJoystickBit(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return JOY_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return JOY_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return JOY_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return JOY_RIGHT;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return JOY_FIRE;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                return JOY_FIRE_2;
            default:
                return 0;
        }
    }

    private int mapExternalGamepadKeyToJoystickBit(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return JOY_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return JOY_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return JOY_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return JOY_RIGHT;
            case KeyEvent.KEYCODE_BUTTON_A:
                return JOY_FIRE;
            case KeyEvent.KEYCODE_BUTTON_B:
                return JOY_FIRE_2;
            default:
                return 0;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int sidebarWidth() {
        return Math.min(dp(380), Math.round(getResources().getDisplayMetrics().widthPixels * 0.5f));
    }

    private File c64DataDir() {
        return new File(getFilesDir(), "c64");
    }

    private boolean needsSetup() {
        if (!prefs.getBoolean(PREF_SETUP_COMPLETED, false)) {
            return true;
        }
        return prefs.getString(PREF_APP_FOLDER_URI, "").isEmpty()
                || prefs.getString(PREF_GAMES_FOLDER_URI, "").isEmpty();
    }

    private String folderLabel(Uri uri) {
        File file = StoragePathUtils.fileForTreeUri(uri);
        return file != null ? file.getAbsolutePath() : uri.toString();
    }

    private void installC64RuntimeData() {
        if (c64RuntimeInstalled()) {
            return;
        }
        new Thread(() -> {
            try {
                ensureC64RuntimeDataInstalled();
                uiHandler.post(() -> refreshStatusPanel());
            } catch (Exception ex) {
                uiHandler.post(() -> updateStatus("C64 data install failed: " + ex.getMessage()));
            }
        }, "C64RuntimeDataInstall").start();
    }

    private boolean c64RuntimeInstalled() {
        return new File(c64DataDir(), ".c64-3.10-installed").isFile();
    }

    private void ensureC64RuntimeDataInstalled() throws IOException {
        File marker = new File(c64DataDir(), ".c64-3.10-installed");
        if (marker.isFile()) {
            return;
        }
        File out = c64DataDir();
        if (!out.exists() && !out.mkdirs()) {
            throw new IOException("Could not create " + out);
        }
        copyAssetTree("vice", out);
        if (!marker.createNewFile() && !marker.isFile()) {
            throw new IOException("Could not write install marker");
        }
    }

    private void copyAssetTree(String assetPath, File targetDir) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            File parent = targetDir.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            try (InputStream input = getAssets().open(assetPath);
                 OutputStream output = new FileOutputStream(targetDir)) {
                byte[] buffer = new byte[1024 * 32];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            }
            return;
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Could not create " + targetDir);
        }
        for (String child : children) {
            copyAssetTree(assetPath + "/" + child, new File(targetDir, child));
        }
    }

    private static final class MediaEntry {
        final String displayName;
        final Uri uri;
        final C64Native.MediaType mediaType;
        IgdbService.IgdbGame igdbGame;
        Bitmap coverBitmap;
        boolean igdbLookupStarted;

        MediaEntry(String displayName, Uri uri, C64Native.MediaType mediaType) {
            this.displayName = displayName;
            this.uri = uri;
            this.mediaType = mediaType;
        }
    }

    private static final class LaunchTarget {
        final File file;
        final C64Native.MediaType mediaType;

        LaunchTarget(File file, C64Native.MediaType mediaType) {
            this.file = file;
            this.mediaType = mediaType;
        }
    }

    private static final class AdvancedResourceOption {
        final String label;
        final String resource;
        final ResourceChoice[] choices;

        AdvancedResourceOption(String label, String resource, ResourceChoice[] choices) {
            this.label = label;
            this.resource = resource;
            this.choices = choices;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class ResourceChoice {
        final String label;
        final int value;

        ResourceChoice(String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    private static final class ControllerKeyChoice {
        final String label;
        final int value;

        ControllerKeyChoice(String label, int value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class ControllerButtonMapping {
        final String label;
        final int keyCode;

        ControllerButtonMapping(String label, int keyCode) {
            this.label = label;
            this.keyCode = keyCode;
        }
    }

    private static KeySpec key(String label, int row, int column) {
        return key(label, row, column, 1.0f);
    }

    private static KeySpec key(String label, int row, int column, float weight) {
        return new KeySpec(label, row, column, weight, null);
    }

    private static KeySpec actionKey(String label, Runnable action, float weight) {
        return new KeySpec(label, 0, 0, weight, action);
    }

    private static final class KeySpec {
        final String label;
        final int row;
        final int column;
        final float weight;
        final Runnable action;

        KeySpec(String label, int row, int column, float weight, Runnable action) {
            this.label = label;
            this.row = row;
            this.column = column;
            this.weight = weight;
            this.action = action;
        }
    }

    private final class ScanlineView extends View {
        private final Paint paint = new Paint();

        ScanlineView(Activity activity) {
            super(activity);
            paint.setColor(0x33000000);
            paint.setStrokeWidth(Math.max(1.0f, getResources().getDisplayMetrics().density));
            setClickable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float gap = dp(4);
            for (float y = 0; y < getHeight(); y += gap) {
                canvas.drawLine(0, y, getWidth(), y, paint);
            }
        }
    }
}
