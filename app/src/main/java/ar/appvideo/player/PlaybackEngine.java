package ar.appvideo.player;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Motor de reproducción avanzada para Leo TV:
 * - Persistencia de posición exacta para 'Continuar viendo'.
 * - Gestión de pistas de audio (Español Latino, Original, etc.).
 * - Gestión de pistas de subtítulos (Activación, desactivación y selección de idioma).
 * - Control de saltos (+10s, -10s) y formateo de tiempo.
 */
public final class PlaybackEngine {
    private static final String PREF_NAME = "leo_playback_history";
    private static final String KEY_ITEMS = "continue_watching_items";
    private static final long MIN_RESUME_TIME_MS = 10000; // Al menos 10 segundos para reanudar

    public static class PlaybackProgress {
        public final String id;
        public final String title;
        public final String poster;
        public final long positionMs;
        public final long durationMs;
        public final String provider;
        public final String kind;
        public final long updatedAt;

        public PlaybackProgress(String id, String title, String poster, long positionMs, long durationMs,
                                String provider, String kind, long updatedAt) {
            this.id = id;
            this.title = title != null ? title : "";
            this.poster = poster != null ? poster : "";
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.provider = provider != null ? provider : "CineHax";
            this.kind = kind != null ? kind : "movie";
            this.updatedAt = updatedAt;
        }

        public int getPercentage() {
            if (durationMs <= 0) return 0;
            return (int) Math.min(100, Math.max(0, (positionMs * 100) / durationMs));
        }

        public String getFormattedPosition() {
            return formatTime(positionMs);
        }

        public String getFormattedDuration() {
            return formatTime(durationMs);
        }
    }

    public static class TrackInfo {
        public final int groupIndex;
        public final int trackIndex;
        public final String label;
        public final String language;
        public final boolean isSelected;
        public final Tracks.Group group;

        public TrackInfo(int groupIndex, int trackIndex, String label, String language, boolean isSelected, Tracks.Group group) {
            this.groupIndex = groupIndex;
            this.trackIndex = trackIndex;
            this.label = label;
            this.language = language;
            this.isSelected = isSelected;
            this.group = group;
        }
    }

    private PlaybackEngine() {}

    /** Guarda el progreso actual de visualización */
    public static void saveProgress(Context ctx, String id, String title, String poster,
                                    long positionMs, long durationMs, String provider, String kind) {
        if (ctx == null || id == null || id.trim().isEmpty()) return;

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // Si ya vio más del 95% o faltan menos de 60 segundos, se considera terminado y se remueve
        if (durationMs > 0 && (positionMs > (durationMs * 0.95) || (durationMs - positionMs) < 60000)) {
            removeProgress(ctx, id);
            return;
        }

        // Solo guardar si vio más de 10 segundos
        if (positionMs < MIN_RESUME_TIME_MS) return;

        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_ITEMS, "[]"));
            JSONArray updated = new JSONArray();

            // Nuevo objeto
            JSONObject cur = new JSONObject();
            cur.put("id", id);
            cur.put("title", title);
            cur.put("poster", poster);
            cur.put("positionMs", positionMs);
            cur.put("durationMs", durationMs);
            cur.put("provider", provider);
            cur.put("kind", kind);
            cur.put("updatedAt", System.currentTimeMillis());

            updated.put(cur);

            // Mantener hasta 30 elementos sin duplicar
            for (int i = 0; i < arr.length() && updated.length() < 30; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (!id.equals(o.optString("id"))) {
                    updated.put(o);
                }
            }

            sp.edit().putString(KEY_ITEMS, updated.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Obtiene la posición guardada previa en ms */
    public static long getSavedPosition(Context ctx, String id) {
        if (ctx == null || id == null || id.trim().isEmpty()) return 0;
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && id.equals(o.optString("id"))) {
                    return o.optLong("positionMs", 0);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /** Remueve un elemento del historial de continuar viendo */
    public static void removeProgress(Context ctx, String id) {
        if (ctx == null || id == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_ITEMS, "[]"));
            JSONArray updated = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !id.equals(o.optString("id"))) {
                    updated.put(o);
                }
            }
            sp.edit().putString(KEY_ITEMS, updated.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Lista completa de elementos en 'Continuar viendo' */
    public static List<PlaybackProgress> getContinueWatchingList(Context ctx) {
        List<PlaybackProgress> list = new ArrayList<>();
        if (ctx == null) return list;
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                list.add(new PlaybackProgress(
                        o.optString("id"),
                        o.optString("title"),
                        o.optString("poster"),
                        o.optLong("positionMs"),
                        o.optLong("durationMs"),
                        o.optString("provider"),
                        o.optString("kind"),
                        o.optLong("updatedAt")
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }

    /** Obtiene pistas de audio disponibles en el ExoPlayer */
    public static List<TrackInfo> getAudioTracks(ExoPlayer player) {
        List<TrackInfo> tracks = new ArrayList<>();
        if (player == null) return tracks;
        Tracks currentTracks = player.getCurrentTracks();

        int groupIndex = 0;
        for (Tracks.Group group : currentTracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_AUDIO) {
                for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                    Format format = group.getTrackFormat(trackIndex);
                    boolean isSelected = group.isTrackSelected(trackIndex);

                    String lang = format.language != null ? format.language : "";
                    String displayName = format.label != null && !format.label.isEmpty()
                            ? format.label
                            : formatLanguageName(lang);

                    if (format.channelCount > 0) {
                        displayName += " (" + (format.channelCount == 6 ? "5.1" : (format.channelCount == 2 ? "Stereo" : format.channelCount + "ch")) + ")";
                    }

                    tracks.add(new TrackInfo(groupIndex, trackIndex, displayName, lang, isSelected, group));
                }
            }
            groupIndex++;
        }
        return tracks;
    }

    /** Selecciona una pista de audio específica */
    public static void selectAudioTrack(ExoPlayer player, TrackInfo track) {
        if (player == null || track == null || track.group == null) return;
        TrackSelectionParameters params = player.getTrackSelectionParameters()
                .buildUpon()
                .setOverrideForType(new TrackSelectionOverride(track.group.getMediaTrackGroup(), track.trackIndex))
                .build();
        player.setTrackSelectionParameters(params);
    }

    /** Obtiene pistas de subtítulos disponibles */
    public static List<TrackInfo> getSubtitleTracks(ExoPlayer player) {
        List<TrackInfo> tracks = new ArrayList<>();
        if (player == null) return tracks;
        Tracks currentTracks = player.getCurrentTracks();

        int groupIndex = 0;
        for (Tracks.Group group : currentTracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_TEXT) {
                for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                    Format format = group.getTrackFormat(trackIndex);
                    boolean isSelected = group.isTrackSelected(trackIndex);

                    String lang = format.language != null ? format.language : "";
                    String displayName = format.label != null && !format.label.isEmpty()
                            ? format.label
                            : formatLanguageName(lang);

                    tracks.add(new TrackInfo(groupIndex, trackIndex, displayName, lang, isSelected, group));
                }
            }
            groupIndex++;
        }
        return tracks;
    }

    /** Selecciona una pista de subtítulos */
    public static void selectSubtitleTrack(ExoPlayer player, TrackInfo track) {
        if (player == null || track == null || track.group == null) return;
        TrackSelectionParameters params = player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(new TrackSelectionOverride(track.group.getMediaTrackGroup(), track.trackIndex))
                .build();
        player.setTrackSelectionParameters(params);
    }

    /** Desactiva los subtítulos */
    public static void disableSubtitles(ExoPlayer player) {
        if (player == null) return;
        TrackSelectionParameters params = player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build();
        player.setTrackSelectionParameters(params);
    }

    /** Formatea un código de idioma ISO a un nombre legible en español */
    public static String formatLanguageName(String code) {
        if (code == null || code.trim().isEmpty()) return "Audio principal";
        String low = code.toLowerCase(Locale.ROOT).trim();
        if (low.startsWith("es") || low.contains("spa")) return "Español (Latino / Castellano)";
        if (low.startsWith("en") || low.contains("eng")) return "Inglés (Original)";
        if (low.startsWith("tr") || low.contains("tur")) return "Turco (Original)";
        if (low.startsWith("ja") || low.contains("jpn")) return "Japonés (Original)";
        if (low.startsWith("ko") || low.contains("kor")) return "Coreano (Original)";
        if (low.startsWith("pt") || low.contains("por")) return "Portugués";
        if (low.startsWith("fr") || low.contains("fra")) return "Francés";
        if (low.startsWith("it") || low.contains("ita")) return "Italiano";
        if (low.startsWith("de") || low.contains("deu")) return "Alemán";
        return "Idioma: " + code.toUpperCase(Locale.ROOT);
    }

    /** Formatea milisegundos en HH:MM:SS o MM:SS */
    public static String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        } else {
            return String.format(Locale.ROOT, "%02d:%02d", m, s);
        }
    }
}

