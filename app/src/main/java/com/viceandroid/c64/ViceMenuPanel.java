package com.viceandroid.c64;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class ViceMenuPanel {
    public interface Host {
        void onRerunSetup();
        void onGameLibrary();
        void onDownloadBezels();
        void onController();
        void onAdvancedSettings();
        void onReset();
        void onDebugInfo();
        void onClose();
    }

    private ViceMenuPanel() {}

    public static int panelWidth(Context ctx) {
        return Math.min(dp(ctx, 380),
                Math.round(ctx.getResources().getDisplayMetrics().widthPixels * 0.5f));
    }

    public static View build(Context ctx, Host host) {
        ScrollView scroll = new ScrollView(ctx);
        scroll.setBackgroundColor(0xFF0B0D10);
        scroll.setClickable(true);

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(ctx);
        title.setText("Settings");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18.0f);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Button close = new Button(ctx);
        close.setText("✕");
        close.setAllCaps(false);
        close.setTextColor(0xFFFFFFFF);
        close.setBackgroundColor(0x00000000);
        close.setOnClickListener(v -> host.onClose());
        header.addView(close);
        panel.addView(header);

        panel.addView(card(ctx, "↻", "Rerun Setup", "Set app and games folders again", v -> host.onRerunSetup()));
        panel.addView(card(ctx, "▦", "Game Library", "Back to your C64 games", v -> host.onGameLibrary()));
        panel.addView(card(ctx, "⬇", "Download Bezels", "Fetch per-game C64 bezels", v -> host.onDownloadBezels()));
        panel.addView(card(ctx, "🎮", "Controller", "Touch pad and C64 key controls", v -> host.onController()));
        panel.addView(card(ctx, "⚙", "Advanced VICE", "Runtime resources and core options", v -> host.onAdvancedSettings()));
        panel.addView(card(ctx, "↺", "Reset C64", "Reset the running core", v -> host.onReset()));
        panel.addView(card(ctx, "ⓘ", "Debug Info", "Paths, IGDB, runtime status", v -> host.onDebugInfo()));

        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private static View card(Context ctx, String icon, String title, String subtitle,
                             View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        card.setBackground(cardBackground(ctx, 0xFF22272E, 0xFF3D4652));
        card.setClickable(true);
        card.setOnClickListener(listener);

        TextView iconView = new TextView(ctx);
        iconView.setText(icon);
        iconView.setTextColor(0xFFFFFFFF);
        iconView.setTextSize(22.0f);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(cardBackground(ctx, 0xFF303844, 0xFF596474));
        card.addView(iconView, new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44)));

        LinearLayout textColumn = new LinearLayout(ctx);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(dp(ctx, 12), 0, 0, 0);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(14.0f);
        titleView.setMaxLines(1);
        textColumn.addView(titleView);

        TextView subtitleView = new TextView(ctx);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(0xFFBAC2CC);
        subtitleView.setTextSize(11.0f);
        subtitleView.setMaxLines(2);
        textColumn.addView(subtitleView);
        card.addView(textColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(ctx, 12), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable cardBackground(Context ctx, int fillColor, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(dp(ctx, 8));
        d.setStroke(dp(ctx, 1), strokeColor);
        return d;
    }
}
