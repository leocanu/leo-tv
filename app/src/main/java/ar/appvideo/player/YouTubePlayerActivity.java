package ar.appvideo.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Reproductor a pantalla completa de YouTube+ para Android TV y Leo TV.
 * Optimizado para navegación mediante control remoto (D-Pad).
 */
public class YouTubePlayerActivity extends Activity {
    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_TITLE = "video_title";
    public static final String EXTRA_CHANNEL = "channel_name";

    private WebView webView;
    private LinearLayout topHud;
    private final Handler hudHandler = new Handler(Looper.getMainLooper());
    private boolean isHudVisible = true;

    public static void start(Context ctx, String videoId, String title, String channel) {
        if (ctx == null || videoId == null || videoId.isEmpty()) return;
        Intent i = new Intent(ctx, YouTubePlayerActivity.class);
        i.putExtra(EXTRA_VIDEO_ID, videoId);
        i.putExtra(EXTRA_TITLE, title != null ? title : "");
        i.putExtra(EXTRA_CHANNEL, channel != null ? channel : "");
        if (!(ctx instanceof Activity)) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Ocultar barras del sistema para experiencia inmersiva TV
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);

        String videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String channel = getIntent().getStringExtra(EXTRA_CHANNEL);

        if (videoId == null || videoId.isEmpty()) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        // WebView configurado para YouTube IFrame TV
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(false);
        ws.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && (url.contains("youtube.com") || url.contains("youtu.be"))) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
        });

        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        // HUD Superior informativo con título y canal
        topHud = buildTopHud(title, channel);
        FrameLayout.LayoutParams hudLp = new FrameLayout.LayoutParams(-1, -2);
        hudLp.gravity = Gravity.TOP;
        root.addView(topHud, hudLp);

        scheduleHideHud();

        // Cargar reproductor HTML con la API de IFrame
        loadPlayerHtml(videoId);
    }

    private LinearLayout buildTopHud(String title, String channel) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(28), dp(20), dp(28), dp(24));

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(230, 0, 0, 0), Color.TRANSPARENT});
        bar.setBackground(bg);

        // Badge YouTube
        TextView ytBadge = new TextView(this);
        ytBadge.setText("🔴 YouTube+");
        ytBadge.setTextColor(Color.WHITE);
        ytBadge.setTextSize(13);
        ytBadge.setTypeface(null, Typeface.BOLD);
        GradientDrawable bBg = new GradientDrawable();
        bBg.setColor(Color.rgb(204, 0, 0));
        bBg.setCornerRadius(dp(4));
        ytBadge.setBackground(bBg);
        ytBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        bar.addView(ytBadge);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(16), 0, dp(16), 0);
        bar.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView titleTv = new TextView(this);
        titleTv.setText(title != null && !title.isEmpty() ? title : "Reproduciendo video");
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(18);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(titleTv);

        if (channel != null && !channel.isEmpty()) {
            TextView chanTv = new TextView(this);
            chanTv.setText(channel);
            chanTv.setTextColor(Color.rgb(200, 205, 215));
            chanTv.setTextSize(14);
            chanTv.setSingleLine(true);
            textCol.addView(chanTv);
        }

        TextView hintTv = new TextView(this);
        hintTv.setText("◄/► Salto 10s · [OK] Pausa · [VOLVER] Salir");
        hintTv.setTextColor(Color.rgb(160, 165, 175));
        hintTv.setTextSize(13);
        bar.addView(hintTv);

        return bar;
    }

    private void scheduleHideHud() {
        hudHandler.removeCallbacksAndMessages(null);
        hudHandler.postDelayed(() -> {
            if (topHud != null && isHudVisible) {
                topHud.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                    topHud.setVisibility(View.GONE);
                    isHudVisible = false;
                }).start();
            }
        }, 3500);
    }

    private void showHudBriefly() {
        if (topHud == null) return;
        topHud.setVisibility(View.VISIBLE);
        topHud.animate().alpha(1f).setDuration(200).start();
        isHudVisible = true;
        scheduleHideHud();
    }

    private void loadPlayerHtml(String videoId) {
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
                "  <style>\n" +
                "    body, html { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }\n" +
                "    #player { width:100%; height:100%; border:0; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div id=\"player\"></div>\n" +
                "  <script src=\"https://www.youtube.com/iframe_api\"></script>\n" +
                "  <script>\n" +
                "    var player;\n" +
                "    function onYouTubeIframeAPIReady() {\n" +
                "      player = new YT.Player('player', {\n" +
                "        height: '100%',\n" +
                "        width: '100%',\n" +
                "        playerVars: {\n" +
                "          'autoplay': 1,\n" +
                "          'controls': 1,\n" +
                "          'rel': 0,\n" +
                "          'fs': 0,\n" +
                "          'origin': 'https://www.youtube.com',\n" +
                "          'enablejsapi': 1,\n" +
                "          'modestbranding': 1,\n" +
                "          'playsinline': 1,\n" +
                "          'iv_load_policy': 3\n" +
                "        },\n" +
                "        events: {\n" +
                "          'onReady': function(e) { e.target.loadVideoById('" + videoId + "', 0); }\n" +
                "        }\n" +
                "      });\n" +
                "    }\n" +
                "    function togglePlayPause() {\n" +
                "      if (!player) return;\n" +
                "      var st = player.getPlayerState();\n" +
                "      if (st === 1) { player.pauseVideo(); } else { player.playVideo(); }\n" +
                "    }\n" +
                "    function seekRel(sec) {\n" +
                "      if (!player) return;\n" +
                "      var c = player.getCurrentTime();\n" +
                "      player.seekTo(c + sec, true);\n" +
                "    }\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";

        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", "https://www.youtube.com");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        showHudBriefly();

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (webView != null) {
                    webView.evaluateJavascript("togglePlayPause();", null);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (webView != null) {
                    webView.evaluateJavascript("seekRel(-10);", null);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (webView != null) {
                    webView.evaluateJavascript("seekRel(10);", null);
                }
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.evaluateJavascript("if (player) { player.pauseVideo(); }", null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        hudHandler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int v) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (v * density + 0.5f);
    }
}
