package com.viceandroid.c64;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public final class SetupWizardActivity extends Activity {
    static final String PREFS_NAME = "vice_c64_android";
    static final String PREF_SETUP_COMPLETED = "setup_completed";
    static final String PREF_APP_FOLDER_URI = "app_folder_uri";
    static final String PREF_GAMES_FOLDER_URI = "games_folder_uri";

    private static final int REQUEST_APP_FOLDER = 3001;
    private static final int REQUEST_GAMES_FOLDER = 3002;

    private static final int STEP_WELCOME = 0;
    private static final int STEP_APP = 1;
    private static final int STEP_GAMES = 2;

    private SharedPreferences prefs;
    private Uri appFolderUri;
    private Uri gamesFolderUri;
    private int currentStep;

    private LinearLayout stepWelcome;
    private LinearLayout stepApp;
    private LinearLayout stepGames;
    private TextView appStatus;
    private TextView gamesStatus;
    private Button backButton;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String app = prefs.getString(PREF_APP_FOLDER_URI, "");
        String games = prefs.getString(PREF_GAMES_FOLDER_URI, "");
        if (!app.isEmpty()) {
            appFolderUri = Uri.parse(app);
        }
        if (!games.isEmpty()) {
            gamesFolderUri = Uri.parse(games);
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enterImmersiveMode();
        setContentView(createContentView());
        showStep(STEP_WELCOME);
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(22), dp(28), dp(18));
        root.setBackgroundColor(0xFF050607);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("C64 Setup");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22.0f);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView badge = new TextView(this);
        badge.setText("C64");
        badge.setTextColor(0xFFE8EAED);
        badge.setTextSize(12.0f);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
        badge.setBackground(cardBackground(0xFF242A31, 0xFF46505C));
        header.addView(badge);
        root.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText("Pick the folders the launcher uses for downloads and C64 media.");
        subtitle.setTextColor(0xFFBAC2CC);
        subtitle.setTextSize(13.0f);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        root.addView(subtitle);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        stepWelcome = createWelcomeStep();
        stepApp = createFolderStep(
                "App Folder",
                "Used for bezel downloads, cached artwork, exports, and future per-game assets.",
                "Select App Folder",
                v -> openFolderPicker(REQUEST_APP_FOLDER),
                true);
        stepGames = createFolderStep(
                "Games Folder",
                "Scanned for PRG first, then disk, tape, and cartridge files.",
                "Select Games Folder",
                v -> openFolderPicker(REQUEST_GAMES_FOLDER),
                false);
        body.addView(stepWelcome);
        body.addView(stepApp);
        body.addView(stepGames);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        backButton = makeButton("Back", v -> showStep(currentStep - 1));
        nextButton = makeButton("Next", v -> goNext());
        footer.addView(backButton);
        footer.addView(nextButton);
        root.addView(footer);

        refreshStatuses();
        return root;
    }

    private LinearLayout createWelcomeStep() {
        LinearLayout step = new LinearLayout(this);
        step.setOrientation(LinearLayout.VERTICAL);
        step.setPadding(dp(16), dp(14), dp(16), dp(14));
        step.setBackground(cardBackground(0xFF151A20, 0xFF303945));

        TextView title = stepTitle("Launcher setup");
        step.addView(title);
        step.addView(stepText("Choose one app folder for managed downloads and one games folder for the C64 library."));
        step.addView(stepText("Core runtime data is packaged from the official source and installed inside app data on launch."));
        return step;
    }

    private LinearLayout createFolderStep(String title, String detail, String buttonText,
                                          View.OnClickListener listener, boolean appStep) {
        LinearLayout step = new LinearLayout(this);
        step.setOrientation(LinearLayout.VERTICAL);
        step.setPadding(dp(16), dp(14), dp(16), dp(14));
        step.setBackground(cardBackground(0xFF151A20, 0xFF303945));

        step.addView(stepTitle(title));
        step.addView(stepText(detail));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        TextView status = new TextView(this);
        status.setTextColor(0xFFDDE5EE);
        status.setTextSize(12.0f);
        row.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(makeButton(buttonText, listener));
        step.addView(row);

        if (appStep) {
            appStatus = status;
        } else {
            gamesStatus = status;
        }
        return step;
    }

    private TextView stepTitle(String value) {
        TextView title = new TextView(this);
        title.setText(value);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18.0f);
        return title;
    }

    private TextView stepText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(0xFFB8C1CD);
        text.setTextSize(13.0f);
        text.setPadding(0, dp(6), 0, 0);
        return text;
    }

    private void showStep(int step) {
        currentStep = Math.max(STEP_WELCOME, Math.min(STEP_GAMES, step));
        if (stepWelcome != null) {
            stepWelcome.setVisibility(currentStep == STEP_WELCOME ? View.VISIBLE : View.GONE);
        }
        if (stepApp != null) {
            stepApp.setVisibility(currentStep == STEP_APP ? View.VISIBLE : View.GONE);
        }
        if (stepGames != null) {
            stepGames.setVisibility(currentStep == STEP_GAMES ? View.VISIBLE : View.GONE);
        }
        if (backButton != null) {
            backButton.setVisibility(currentStep == STEP_WELCOME ? View.GONE : View.VISIBLE);
        }
        if (nextButton != null) {
            nextButton.setText(currentStep == STEP_GAMES ? "Finish" : "Next");
            nextButton.setEnabled(canAdvance());
            nextButton.setAlpha(canAdvance() ? 1.0f : 0.45f);
        }
        refreshStatuses();
    }

    private boolean canAdvance() {
        if (currentStep == STEP_APP) {
            return appFolderUri != null;
        }
        if (currentStep == STEP_GAMES) {
            return gamesFolderUri != null;
        }
        return true;
    }

    private void goNext() {
        if (currentStep == STEP_APP && appFolderUri == null) {
            Toast.makeText(this, "Select the app folder first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentStep == STEP_GAMES) {
            if (gamesFolderUri == null) {
                Toast.makeText(this, "Select the games folder first", Toast.LENGTH_SHORT).show();
                return;
            }
            finishSetup();
            return;
        }
        showStep(currentStep + 1);
    }

    private void openFolderPicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
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
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
        }

        if (requestCode == REQUEST_APP_FOLDER) {
            File root = StoragePathUtils.fileForTreeUri(uri);
            if (root != null && !StoragePathUtils.isWritableDirectory(root)) {
                Toast.makeText(this, "Selected app folder is not writable", Toast.LENGTH_SHORT).show();
                return;
            }
            appFolderUri = uri;
            prefs.edit().putString(PREF_APP_FOLDER_URI, uri.toString()).apply();
            showStep(STEP_GAMES);
        } else if (requestCode == REQUEST_GAMES_FOLDER) {
            gamesFolderUri = uri;
            prefs.edit().putString(PREF_GAMES_FOLDER_URI, uri.toString()).apply();
            showStep(STEP_GAMES);
        }
        refreshStatuses();
    }

    private void refreshStatuses() {
        if (appStatus != null) {
            appStatus.setText(appFolderUri == null ? "No app folder selected" : folderLabel(appFolderUri));
        }
        if (gamesStatus != null) {
            gamesStatus.setText(gamesFolderUri == null ? "No games folder selected" : folderLabel(gamesFolderUri));
        }
        if (nextButton != null) {
            nextButton.setEnabled(canAdvance());
            nextButton.setAlpha(canAdvance() ? 1.0f : 0.45f);
        }
    }

    private String folderLabel(Uri uri) {
        File file = StoragePathUtils.fileForTreeUri(uri);
        return file != null ? file.getAbsolutePath() : uri.toString();
    }

    private void finishSetup() {
        prefs.edit()
                .putBoolean(PREF_SETUP_COMPLETED, true)
                .putString(PREF_APP_FOLDER_URI, appFolderUri.toString())
                .putString(PREF_GAMES_FOLDER_URI, gamesFolderUri.toString())
                .apply();
        setResult(RESULT_OK);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private Button makeButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12.0f);
        button.setMinWidth(dp(76));
        button.setMinimumWidth(dp(76));
        button.setMinHeight(dp(38));
        button.setMinimumHeight(dp(38));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(cardBackground(0xFF242A31, 0xFF526173));
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable cardBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(6));
        drawable.setStroke(dp(1), stroke);
        return drawable;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
