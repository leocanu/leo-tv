package ar.appvideo.player;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LiveTvEngine {
    private static final String PREF_NAME = "leo_tv_live_data";
    private static final String KEY_FAVS = "tv_favs_json";
    private static final String KEY_RECENTS = "tv_recents_json";

    public static final class LiveChannel {
        public final String id;
        public final String name;
        public final String category;
        public final String streamUrl;
        public final String backupUrl;
        public final String logoUrl;
        public final String quality;
        public final String epgTitle;
        public final String epgNext;

        public LiveChannel(String id, String name, String category, String streamUrl, String backupUrl, String logoUrl, String quality, String epgTitle, String epgNext) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.streamUrl = streamUrl;
            this.backupUrl = backupUrl;
            this.logoUrl = logoUrl != null ? logoUrl : "";
            this.quality = quality != null ? quality : "HD";
            this.epgTitle = epgTitle != null ? epgTitle : "Transmisión Oficial en Vivo";
            this.epgNext = epgNext != null ? epgNext : "Programación Continua 24/7";
        }

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("name", name);
                o.put("category", category);
                o.put("streamUrl", streamUrl);
                o.put("backupUrl", backupUrl);
                o.put("logoUrl", logoUrl);
                o.put("quality", quality);
                o.put("epgTitle", epgTitle);
                o.put("epgNext", epgNext);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static LiveChannel fromJson(JSONObject o) {
            return new LiveChannel(
                    o.optString("id"),
                    o.optString("name", "Canal de TV"),
                    o.optString("category", "argentina"),
                    o.optString("streamUrl", ""),
                    o.optString("backupUrl", ""),
                    o.optString("logoUrl", ""),
                    o.optString("quality", "HD"),
                    o.optString("epgTitle", "En emisión"),
                    o.optString("epgNext", "Siguiente programa")
            );
        }
    }

    public static final class TvCategory {
        public final String id;
        public final String label;
        public final String icon;

        public TvCategory(String id, String label, String icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }
    }

    public static List<TvCategory> getCategories() {
        List<TvCategory> cats = new ArrayList<>();
        cats.add(new TvCategory("favs", "⭐ Favoritos de TV", "⭐"));
        cats.add(new TvCategory("recents", "🕒 Canales Recientes", "🕒"));
        cats.add(new TvCategory("iptv_org_explorer", "🌍 IPTV-org (Filtros por País y Género)", "🌍"));
        cats.add(new TvCategory("iptv_org_ar", "🇦🇷 IPTV-org Argentina (186 Canales)", "🇦🇷"));
        cats.add(new TvCategory("tdtchannels", "🇪🇸 TDTChannels TV (574 Canales)", "🇪🇸"));
        cats.add(new TvCategory("freetv", "🌐 Free-TV Global IPTV (2.200 Canales)", "🌐"));
        cats.add(new TvCategory("argentina", "🇦🇷 Argentina (Aire y Noticias)", "🇦🇷"));
        cats.add(new TvCategory("deportes", "⚽ Deportes en Directo", "⚽"));
        cats.add(new TvCategory("noticias", "📰 Noticias 24/7", "📰"));
        cats.add(new TvCategory("cine", "🎬 Cine y Series", "🎬"));
        cats.add(new TvCategory("kids", "🧸 Infantiles y Animación", "🧸"));
        cats.add(new TvCategory("musica", "🎸 Música y Recitales 24/7", "🎸"));
        cats.add(new TvCategory("espana", "🇪🇸 España TDT Abierta", "🇪🇸"));
        cats.add(new TvCategory("latam", "🌐 Latinoamérica Variedades", "🌐"));
        cats.add(new TvCategory("adults", "🔞 Adultos +18 (PIN Requerido)", "🔞"));
        return cats;
    }

    public static List<LiveChannel> getChannelsForCategory(Context ctx, String catId) {
        if ("favs".equals(catId)) {
            return getFavorites(ctx);
        } else if ("recents".equals(catId)) {
            return getRecents(ctx);
        } else if ("iptv_org_ar".equals(catId)) {
            return getIptvOrgArgentinaChannels(ctx);
        }
        return getPresetChannels(catId);
    }

    public static List<LiveChannel> getPresetChannels(String catId) {
        List<LiveChannel> list = new ArrayList<>();

        if ("argentina".equals(catId)) {
            list.add(new LiveChannel("ar_telefe", "Telefe (Buenos Aires)", "argentina",
                    "https://57j4mtvcjjf7siqnnvkixb5d3i0uvjei.lambda-url.us-east-1.on.aws/?stream_id=6a024684fd4ca6a938f3a118",
                    "http://45.134.141.161:2200/ARG/TELEFE_HD/tracks-v1a1/mono.m3u8",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Telefe_%282020%29.svg/500px-Telefe_%282020%29.svg.png",
                    "HD 1080p", "Telefe Noticias · En Vivo", "Ficción y Entretenimiento Familiar"));

            list.add(new LiveChannel("ar_eltrece", "El Trece (Canal 13 Artear)", "argentina",
                    "https://livetrx01.vodgc.net/eltrecetv/index.m3u8",
                    "https://live-01-01-tn.vodgc.net/eltrecetv/index.m3u8",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/El_Trece_2016.svg/500px-El_Trece_2016.svg.png",
                    "HD 1080p", "Telenoche · Noticiero Central", "Los 8 Escalones del Millón"));

            list.add(new LiveChannel("ar_america", "América TV HD", "argentina",
                    "https://prepublish.f.qaotic.net/a07/americahls-100056/playlist_720p.m3u8",
                    "https://stream1.sersat.com/hls/americatv.m3u8",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/Am%C3%A9rica_TV_%28Nuevo_logo_Junio_2020%29.png/500px-Am%C3%A9rica_TV_%28Nuevo_logo_Junio_2020%29.png",
                    "HD 720p", "América Noticias · Emisión Central", "LAM / Intrusos en el Espectáculo"));

            list.add(new LiveChannel("ar_tn", "TN (Todo Noticias 24H)", "argentina",
                    "https://live-01-01-tn.vodgc.net/TN_DAI_Beta/index.m3u8",
                    "http://158.172.155.130:8000/play/a0dp/index.m3u8",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4f/TN_todo_noticias_logo.svg/500px-TN_todo_noticias_logo.svg.png",
                    "HD 1080p", "TN Central · Noticias en Vivo", "Desde el Llano / La Rosca"));

            list.add(new LiveChannel("ar_c5n", "C5N (Noticias 24/7)", "argentina",
                    "http://45.134.141.161:2200/ARG/C5N/index.m3u8",
                    "http://138.59.227.20:8000/play/a05v/index.m3u8",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/C5N_logo_2019.svg/500px-C5N_logo_2019.svg.png",
                    "HD 1080p", "Minuto Uno con Gustavo Sylvestre", "Sobredosis de TV"));

            list.add(new LiveChannel("ar_c26", "Canal 26 (Argentina)", "argentina",
                    "https://stream-gtlc.telecentro.net.ar/hls/canal26hls/main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Canal_26_logo_%282022%29.svg/500px-Canal_26_logo_%282022%29.svg.png",
                    "HD 1080p", "Las Noticias de las 26 · En directo", "Resumen Internacional 24H"));

            list.add(new LiveChannel("ar_a24", "A24 Noticias", "argentina",
                    "http://138.59.227.20:8000/play/a05u/index.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/A24_logo_2021.svg/500px-A24_logo_2021.svg.png",
                    "HD 1080p", "El Noticiero de A24 · En Vivo", "Debate Abierto"));

            list.add(new LiveChannel("ar_c9", "Canal 9 (El Nueve)", "argentina",
                    "https://617c5175c970b.streamlock.net:4444/9linklivert/smil:9linkmultibr.smil/playlist.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6b/El_Nueve_logo_2021.svg/500px-El_Nueve_logo_2021.svg.png",
                    "HD 720p", "Telenueve Central", "Bendita TV"));

            list.add(new LiveChannel("ar_nettv", "Net TV Argentina", "argentina",
                    "https://unlimited1-us.dps.live/nettv/nettv.smil/playlist.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7c/Net_TV_logo.svg/500px-Net_TV_logo.svg.png",
                    "HD 720p", "Entrometidos en la Tarde", "Cine Net"));

            list.add(new LiveChannel("ar_tvpublica", "TV Pública Argentina", "argentina",
                    "https://nd106.republicaservers.com/hls/c8094/index.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c2/TV_P%C3%BAblica_Argentina_%282021%29.svg/500px-TV_P%C3%BAblica_Argentina_%282021%29.svg.png",
                    "HD 1080p", "Noticias y Ficción Nacional", "Deportes y Festival País"));

            list.add(new LiveChannel("ar_telemax", "Telemax Noticias", "argentina",
                    "https://stream-gtlc.telecentro.net.ar/hls/telemaxhls/main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/Telemax_Argentina_%282018%29.png/500px-Telemax_Argentina_%282018%29.png",
                    "HD 1080p", "Telemax Noticias y Actualidad", "Música y Deportes"));

            list.add(new LiveChannel("ar_perfil", "+Perfil TV", "argentina",
                    "https://unlimited1-us.dps.live/perfiltv/perfiltv.smil/perfiltv/livestream2/chunks.m3u8", "",
                    "https://i.imgur.com/3wZdPwN.png",
                    "HD 720p", "Perfil Periodismo Puro", "Noticias de Fondo"));

            list.add(new LiveChannel("ar_cinear", "Cine.AR (Cine Nacional)", "argentina",
                    "https://5fb24b460df87.streamlock.net/live-cont.ar/cinear/playlist.m3u8", "",
                    "https://i.imgur.com/RPLyrIC.png",
                    "HD 720p", "Cine Argentino sin Cortes", "Estrenos INCAA"));

            list.add(new LiveChannel("ar_deportv", "DeporTV Argentina", "argentina",
                    "http://158.172.155.130:8000/play/a09y/index.m3u8", "",
                    "https://i.imgur.com/iyYLNRt.png",
                    "HD 720p", "Deporte Inclusivo y Federal", "Torneos y Magazine"));

            list.add(new LiveChannel("ar_aire", "Aire de Santa Fe HD", "argentina",
                    "https://mdstrm.com/live-stream-playlist/6931a0eb06778645348008e0.m3u8", "",
                    "https://i.imgur.com/60vSWW0.png",
                    "HD 1080p", "Ahora Vengo · Conducción en directo", "Informe Central"));

            list.add(new LiveChannel("ar_c247", "24/7 Canal de Noticias", "argentina",
                    "https://panel.host-live.com:19360/cn247tv/cn247tv.m3u8", "",
                    "https://i.imgur.com/4hDCB1M.png",
                    "SD 540p", "Noticias Federales 24 Horas", "Panorama Económico"));

            list.add(new LiveChannel("ar_5tv", "5TV Corrientes", "argentina",
                    "https://617c5175c970b.streamlock.net:4444/tvcinco/live1/playlist.m3u8", "",
                    "https://i.imgur.com/mSn7ACs.png",
                    "SD 540p", "Noticiero Regional", "Cultura y Deportes del Litoral"));

            list.add(new LiveChannel("ar_argentinisima", "Argentinísima Satelital", "argentina",
                    "https://stream1.sersat.com/hls/argentinisima.m3u8", "",
                    "https://i.imgur.com/xFgJawa.png",
                    "SD 540p", "Tradición y Música Argentina", "Folklore y Paisajes"));
        } else if ("deportes".equals(catId)) {
            list.add(new LiveChannel("dep_espn", "ESPN HD Argentina", "deportes",
                    "https://tvf90.com/1.php?stream=espn", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/500px-ESPN_wordmark.svg.png",
                    "HD 1080p", "SportsCenter en Directo", "Fútbol Internacional & Polideportivo"));

            list.add(new LiveChannel("dep_espn2", "ESPN 2 HD", "deportes",
                    "https://tvf90.com/1.php?stream=espn2", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/500px-ESPN_wordmark.svg.png",
                    "HD 1080p", "UEFA Champions League & Tenis ATP", "Fútbol Europeo en Vivo"));

            list.add(new LiveChannel("dep_espn3", "ESPN 3 HD", "deportes",
                    "https://tvf90.com/1.php?stream=espn3", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/500px-ESPN_wordmark.svg.png",
                    "HD 1080p", "Rugby, Ciclismo & Deportes Alternativos", "Programación Polideportiva"));

            list.add(new LiveChannel("dep_espn4", "ESPN 4 HD", "deportes",
                    "https://tvf90.com/1.php?stream=espn4", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/500px-ESPN_wordmark.svg.png",
                    "HD 1080p", "Ligas Internacionales & Motores", "Fútbol en Directo"));

            list.add(new LiveChannel("dep_espnpremium", "ESPN Premium (Fútbol Argentino)", "deportes",
                    "https://tvf90.com/1.php?stream=espnpremium", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/500px-ESPN_wordmark.svg.png",
                    "HD 1080p", "Liga Profesional de Fútbol AFA", "Partidos en Directo & Pasión"));

            list.add(new LiveChannel("dep_foxsports", "Fox Sports Argentina HD", "deportes",
                    "https://tvf90.com/1.php?stream=foxsports", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Fox_Sports_logo_2020.svg/500px-Fox_Sports_logo_2020.svg.png",
                    "HD 1080p", "Fórmula 1 & Copa Libertadores", "La Tarde de Fox Sports"));

            list.add(new LiveChannel("dep_foxsports2", "Fox Sports 2 HD", "deportes",
                    "https://tvf90.com/1.php?stream=foxsports2", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Fox_Sports_logo_2020.svg/500px-Fox_Sports_logo_2020.svg.png",
                    "HD 1080p", "WWE, Béisbol MLB & Copa Libertadores", "Deportes en Directo"));

            list.add(new LiveChannel("dep_foxsports3", "Fox Sports 3 HD", "deportes",
                    "https://tvf90.com/1.php?stream=foxsports3", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Fox_Sports_logo_2020.svg/500px-Fox_Sports_logo_2020.svg.png",
                    "HD 1080p", "MotoGP, Automovilismo & Deportes Extremos", "Velocidad Total"));

            list.add(new LiveChannel("dep_tyc", "TyC Sports HD", "deportes",
                    "https://tvf90.com/1.php?stream=tycsports",
                    "https://amg26268-amg26268c14-freelivesports-emea-10267.playouts.now.amagi.tv/ts-us-e2-n2/playlist/amg26268-sportsstudio-tycsports-freelivesportsemea/playlist.m3u8",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/54/TyC_Sports_logo.svg/500px-TyC_Sports_logo.svg.png",
                    "HD 1080p", "Sportia Noticiero & Fútbol de Primera", "Copa Argentina y Polideportivo"));

            list.add(new LiveChannel("dep_tntsports", "TNT Sports Argentina", "deportes",
                    "https://tvf90.com/1.php?stream=tntsports",
                    "http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/86.m3u8",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d3/TNT_Sports_2021_logo.svg/500px-TNT_Sports_2021_logo.svg.png",
                    "HD 1080p", "Liga Profesional & Todos Al Gol", "Fútbol en Directo"));

            list.add(new LiveChannel("dep_dsports", "DSports HD (DirecTV)", "deportes",
                    "https://tvf90.com/1.php?stream=dsports", "",
                    "https://i.imgur.com/iyYLNRt.png",
                    "HD 1080p", "Copa Sudamericana & Fútbol Exclusivo", "Debate Fútbol"));

            list.add(new LiveChannel("dep_goltv", "GolTV Latino HD", "deportes",
                    "https://tvf90.com/1.php?stream=goltv", "",
                    "https://i.imgur.com/iyYLNRt.png",
                    "HD 1080p", "Ligas de Sudamérica & GolTV Noticias", "Fútbol 24/7"));

            list.add(new LiveChannel("dep_rmtv", "Real Madrid TV HD (Español)", "deportes",
                    "https://rmtv.akamaized.net/hls/live/2043153/rmtv-es-web/master.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/56/Real_Madrid_CF.svg/600px-Real_Madrid_CF.svg.png",
                    "HD 1080p", "Hoy Jugamos · Actualidad del Club Blanco", "La Fábrica y Partidos Históricos"));

            list.add(new LiveChannel("dep_tdeporte", "Teledeporte RTVE (España)", "deportes",
                    "https://rtvelivestream.akamaized.net/rtvesec/tdp/tdp_main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c2/Teledeporte_2008.svg/600px-Teledeporte_2008.svg.png",
                    "HD 720p", "Estudio Estadio · En Vivo", "Polideportivo y Ligas Internacionales"));

            list.add(new LiveChannel("dep_deportv", "DeporTV Argentina", "deportes",
                    "https://5fb24b460df87.streamlock.net/live-cont.ar/deportv/playlist.m3u8", "",
                    "https://i.imgur.com/iyYLNRt.png",
                    "HD 720p", "Deporte Inclusivo y Federal", "Torneos y Magazine"));

            list.add(new LiveChannel("dep_redbull", "Red Bull TV Deportes Extremos", "deportes",
                    "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/9/94/Red_Bull_logo.svg/600px-Red_Bull_logo.svg.png",
                    "HD 1080p", "Red Bull Cliff Diving & Motorsport", "Ciclismo Downhill Extremo"));

            list.add(new LiveChannel("dep_elchiringuito", "El Chiringuito TV (Mega Deportes)", "deportes",
                    "https://atresmedia-live.secure.footprint.net/live/mega/master.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c2/Mega_logo_2014.svg/500px-Mega_logo_2014.svg.png",
                    "HD 720p", "El Chiringuito de Jugones", "Debate Futbolero y Exclusivas"));
        } else if ("noticias".equals(catId)) {
            list.add(new LiveChannel("not_tn", "TN (Todo Noticias 24H)", "noticias",
                    "https://live-01-01-tn.vodgc.net/TN_DAI_Beta/index.m3u8",
                    "http://158.172.155.130:8000/play/a0dp/index.m3u8",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4f/TN_todo_noticias_logo.svg/500px-TN_todo_noticias_logo.svg.png",
                    "HD 1080p", "TN Central · Noticias en Vivo", "Desde el Llano / La Rosca"));

            list.add(new LiveChannel("not_c5n", "C5N (Noticias 24/7)", "noticias",
                    "http://45.134.141.161:2200/ARG/C5N/index.m3u8",
                    "http://138.59.227.20:8000/play/a05v/index.m3u8",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/C5N_logo_2019.svg/500px-C5N_logo_2019.svg.png",
                    "HD 1080p", "Minuto Uno con Gustavo Sylvestre", "Sobredosis de TV"));

            list.add(new LiveChannel("not_c26", "Canal 26 HD Noticias", "noticias",
                    "https://stream-gtlc.telecentro.net.ar/hls/canal26hls/main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Canal_26_logo_%282022%29.svg/500px-Canal_26_logo_%282022%29.svg.png",
                    "HD 1080p", "La Mañana de Canal 26", "Guerra y Geopolítica Global"));

            list.add(new LiveChannel("not_a24", "A24 Noticias", "noticias",
                    "http://138.59.227.20:8000/play/a05u/index.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/A24_logo_2021.svg/500px-A24_logo_2021.svg.png",
                    "HD 1080p", "El Noticiero de A24", "Debate Abierto"));

            list.add(new LiveChannel("not_telemax", "Telemax Noticias", "noticias",
                    "https://stream-gtlc.telecentro.net.ar/hls/telemaxhls/main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/Telemax_Argentina_%282018%29.png/500px-Telemax_Argentina_%282018%29.png",
                    "HD 1080p", "Telemax Noticias y Actualidad", "Música y Deportes"));

            list.add(new LiveChannel("not_rtve24", "RTVE 24 Horas (España)", "noticias",
                    "https://rtvelivestream.akamaized.net/rtvesec/24h/24h_main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/63/Canal_24_horas_2008.svg/600px-Canal_24_horas_2008.svg.png",
                    "HD 720p", "Diario 24 Horas · Edición Internacional", "La Tarde en 24 Horas"));

            list.add(new LiveChannel("not_dw", "Deutsche Welle (Español)", "noticias",
                    "https://dwstream4-lh.akamaihd.net/i/dwtv_spa@313214/master.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Deutsche_Welle_logo.svg/600px-Deutsche_Welle_logo.svg.png",
                    "HD 720p", "El Noticiero DW · Europa y el Mundo", "Economía Global y Tecnología"));

            list.add(new LiveChannel("not_euronews", "Euronews en Español", "noticias",
                    "https://rakuten-euronews-7-es.samsung.wurl.tv/playlist.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/05/Euronews_logo_2016.svg/600px-Euronews_logo_2016.svg.png",
                    "HD 1080p", "Euronews Hoy · La Mirada Europea", "No Comment & Actualidad"));

            list.add(new LiveChannel("not_24h_rtve", "Canal 24 Horas RTVE", "noticias",
                    "https://rtvelivestream.akamaized.net/rtvesec/24h/24h_main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/63/Canal_24_horas_2008.svg/600px-Canal_24_horas_2008.svg.png",
                    "HD 720p", "Diario 24 Horas · Edición Internacional", "La Tarde en 24 Horas"));

            list.add(new LiveChannel("not_dw_es", "DW Español Noticias (Alemania)", "noticias",
                    "https://dwstream4-lh.akamaihd.net/i/dwtv_spa@313214/master.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Deutsche_Welle_logo.svg/600px-Deutsche_Welle_logo.svg.png",
                    "HD 720p", "El Noticiero DW · Europa y el Mundo", "Economía Global y Tecnología"));

            list.add(new LiveChannel("not_euronews", "Euronews en Español", "noticias",
                    "https://rakuten-euronews-7-es.samsung.wurl.tv/playlist.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/05/Euronews_logo_2016.svg/600px-Euronews_logo_2016.svg.png",
                    "HD 1080p", "Euronews Hoy · La Mirada Europea", "No Comment & Actualidad"));
        } else if ("cine".equals(catId)) {
            list.add(new LiveChannel("cine_alterna", "Alterna TV Cine y Series", "cine",
                    "https://tv.alterna.ar/stream/hls/live.m3u8", "",
                    "https://tv.alterna.ar/alternatv.png",
                    "HD 720p", "Cine Independiente y Series", "Maratón Nocturno de Ficción"));

            list.add(new LiveChannel("cine_comedy", "Comedy Central Latino HD", "cine",
                    "https://bantel-cdn1.iptvperu.tv:1936/btnscrtn/Comedy_Central/playlist.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/aa/Comedy_Central_2018.svg/960px-Comedy_Central_2018.svg.png",
                    "HD 1080p", "South Park · Temporadas dobladas", "Especiales de Comedia y Risas"));

            list.add(new LiveChannel("cine_canela", "Canela TV Cine Latino", "cine",
                    "https://stream.ads.ottera.tv/playlist.m3u8?network_id=2376", "",
                    "https://i.imgur.com/8QO9n8R.png",
                    "HD 1080p", "Cine Mexicano, Clásico y Estrenos", "Festival de Películas Latinas"));

            list.add(new LiveChannel("cine_rtve", "RTVE Somos Cine y Ficción", "cine",
                    "https://rtvelivestream.akamaized.net/rtvesec/somos/somos_main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d7/RTVE.es_Logo.svg/600px-RTVE.es_Logo.svg.png",
                    "HD 1080p", "Cine en Español 24/7", "Grandes Producciones y Festivales"));
        } else if ("kids".equals(catId)) {
            list.add(new LiveChannel("kid_cn", "Cartoon Network Latino HD", "kids",
                    "https://bantel-cdn1.iptvperu.tv:1936/btnscrtn/cartoonnetwork.stream/chunks.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Cartoon_Network_2010_logo.svg/960px-Cartoon_Network_2010_logo.svg.png",
                    "HD 1080p", "El Increíble Mundo de Gumball", "Hora de Aventura y Teen Titans Go"));

            list.add(new LiveChannel("kid_pluto_nick", "Pluto TV Nick Clásico Latino", "kids",
                    "https://service-stitcher.clusters.pluto.tv/stitch/hls/channel/5f971ba48e4265000780f2d3/master.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7a/Nickelodeon_2009_logo.svg/600px-Nickelodeon_2009_logo.svg.png",
                    "HD 1080p", "Animación Clásica y Aventuras", "Rugrats, Bob Esponja y Más"));

            list.add(new LiveChannel("kid_clan", "Clan TV RTVE (Infantil)", "kids",
                    "https://rtvelivestream.akamaized.net/rtvesec/clan/clan_main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Clan_logo_2015.svg/600px-Clan_logo_2015.svg.png",
                    "HD 720p", "Bob Esponja y Peppa Pig", "Patrulla Canina y Dibujos Educativos"));
        } else if ("espana".equals(catId)) {
            list.add(new LiveChannel("es_la1", "La 1 RTVE HD", "espana",
                    "https://rtvelivestream.akamaized.net/rtvesec/la1/la1_main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/La_1_logo_2008.svg/600px-La_1_logo_2008.svg.png",
                    "HD 1080p", "Telediario 2 · Noticias de España y Europa", "Cine de La 1 en Prime Time"));

            list.add(new LiveChannel("es_la2", "La 2 RTVE Documentales y Cultura", "espana",
                    "https://rtvelivestream.akamaized.net/rtvesec/la2/la2_main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/29/La_2_logo_2008.svg/600px-La_2_logo_2008.svg.png",
                    "HD 1080p", "Grandes Documentales de la Naturaleza", "Saber y Ganar · Programa de Concurso"));

            list.add(new LiveChannel("es_antena3", "Antena 3 Internacional", "espana",
                    "https://antena3-live.akamaized.net/hls/live/572186/antena3/master.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Antena_3_logo_2017.svg/600px-Antena_3_logo_2017.svg.png",
                    "HD 720p", "Antena 3 Noticias", "La Ruleta de la Suerte"));
        } else if ("latam".equals(catId)) {
            list.add(new LiveChannel("lat_telesur", "teleSUR Latino", "latam",
                    "https://telesur.livecastv.com/telesur/telesur/playlist.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/Telesur_logo.svg/600px-Telesur_logo.svg.png",
                    "HD 720p", "Noticias de la Patria Grande", "Documentales y Análisis"));

            list.add(new LiveChannel("lat_france24", "France 24 en Español", "latam",
                    "https://f24hls-i.akamaihd.net/hls/live/221193/F24_ES_LO_HLS/master_500.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/France_24_logo_2013.svg/600px-France_24_logo_2013.svg.png",
                    "SD 540p", "El Noticiero Internacional", "Debate Latinoamericano"));
        } else if ("musica".equals(catId)) {
            list.add(new LiveChannel("mus_mtv", "MTV Hits & Pop Mundial HD", "musica",
                    "https://stream-gtlc.telecentro.net.ar/hls/mtvhls/main.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/68/MTV_Hits_logo.svg/600px-MTV_Hits_logo.svg.png",
                    "HD 1080p", "Non-Stop Hits & Pop Mundial", "Top 20 Hitlist"));

            list.add(new LiveChannel("mus_cm", "CM El Canal de la Música", "musica",
                    "https://stream1.sersat.com/hls/cm.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/CM_El_Canal_de_la_M%C3%BAsica_logo.png/500px-CM_El_Canal_de_la_M%C3%BAsica_logo.png",
                    "HD 720p", "Rock Nacional y Música Latina", "Especiales Acústicos y Recitales"));

            list.add(new LiveChannel("mus_qmusica", "Quiero Música en Mi Idioma", "musica",
                    "https://live-01-02-ar-claro.clarovideo.net/live/eds/QuieroMusica/HLS_01/QuieroMusica.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/MuchMusic_logo.svg/600px-MuchMusic_logo.svg.png",
                    "HD 720p", "Música Latina y Entrevistas", "Lo Mejor del Pop y Rock en Español"));

            list.add(new LiveChannel("mus_stingray", "Stingray CMusic Conciertos", "musica",
                    "https://rakuten-stingraycmusic-4-es.samsung.wurl.tv/playlist.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/60/Stingray_C_Music_TV_logo.svg/600px-Stingray_C_Music_TV_logo.svg.png",
                    "HD 1080p", "Bandas Sonoras y Recitales Sinfónicos", "Conciertos del Mundo"));

            list.add(new LiveChannel("mus_trace", "Trace Urban Hits", "musica",
                    "https://trace-traceurban-1-eu.rakuten.wurl.tv/playlist.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Trace_Urban_logo.png/500px-Trace_Urban_logo.png",
                    "HD 1080p", "Urban & Hip-Hop en Directo", "Ranking Semanal"));
        } else if ("adults".equals(catId)) {
            list.add(new LiveChannel("ad_venus", "Venus Latino (+18)", "adults",
                    "http://15.204.246.24:8080/VenusHD/index.m3u8", "",
                    "https://i.imgur.com/8QO9n8R.png",
                    "HD 1080p", "Estrenos Eróticos Explícitos", "Cine para Adultos 24/7"));

            list.add(new LiveChannel("ad_playboy", "Playboy TV HD (+18)", "adults",
                    "http://15.204.246.24:8080/PlayboyHD/index.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b5/Playboy_TV_Logo.svg/600px-Playboy_TV_Logo.svg.png",
                    "HD 1080p", "Playboy Club & Sensualidad", "Estrenos Internacionales"));

            list.add(new LiveChannel("ad_hustler", "Hustler TV HD (+18)", "adults",
                    "http://15.204.246.24:8080/HustlerHD/index.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/Hustler_TV_Logo.svg/600px-Hustler_TV_Logo.svg.png",
                    "HD 1080p", "Hustler Hardcore Exclusivo", "Producciones Premium"));

            list.add(new LiveChannel("ad_private", "Private Gold TV (+18)", "adults",
                    "http://15.204.246.24:8080/PrivateHD/index.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/Private_Media_Group_logo.svg/600px-Private_Media_Group_logo.svg.png",
                    "HD 1080p", "Private Clásicos y Superproducciones", "Cine Explícito Europeo"));

            list.add(new LiveChannel("ad_redtraffic", "RedTraffic Live (+18)", "adults",
                    "https://live.redtraffic.xyz/hls/stream.m3u8", "",
                    "https://i.imgur.com/Xw5N5xY.png",
                    "HD 720p", "Transmisión en Vivo Continuo +18", "Hardcore y Escenas Explícitas"));

            list.add(new LiveChannel("ad_brazz", "Brazzers TV HD (+18)", "adults",
                    "http://15.204.246.24:8080/BrazzersHD/index.m3u8", "",
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Brazzers_Logo.svg/600px-Brazzers_Logo.svg.png",
                    "HD 1080p", "Brazzers Originals Exclusivo", "Maratón Explícito 24 Horas"));
        }

        return list;
    }

    public static List<LiveChannel> getFavorites(Context ctx) {
        List<LiveChannel> favs = new ArrayList<>();
        if (ctx == null) return favs;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY_FAVS, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                favs.add(LiveChannel.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return favs;
    }

    public static boolean isFavorite(Context ctx, String channelId) {
        if (ctx == null || channelId == null) return false;
        List<LiveChannel> favs = getFavorites(ctx);
        for (LiveChannel c : favs) {
            if (channelId.equals(c.id)) return true;
        }
        return false;
    }

    public static boolean toggleFavorite(Context ctx, LiveChannel ch) {
        if (ctx == null || ch == null) return false;
        List<LiveChannel> favs = getFavorites(ctx);
        boolean found = false;
        Iterator<LiveChannel> it = favs.iterator();
        while (it.hasNext()) {
            if (it.next().id.equals(ch.id)) {
                it.remove();
                found = true;
                break;
            }
        }
        if (!found) {
            favs.add(0, ch);
        }
        saveFavorites(ctx, favs);
        return !found;
    }

    private static void saveFavorites(Context ctx, List<LiveChannel> favs) {
        try {
            JSONArray arr = new JSONArray();
            for (LiveChannel c : favs) {
                arr.put(c.toJson());
            }
            SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_FAVS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<LiveChannel> getRecents(Context ctx) {
        List<LiveChannel> recents = new ArrayList<>();
        if (ctx == null) return recents;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY_RECENTS, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                recents.add(LiveChannel.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return recents;
    }

    public static void addRecent(Context ctx, LiveChannel ch) {
        if (ctx == null || ch == null) return;
        List<LiveChannel> list = getRecents(ctx);
        Iterator<LiveChannel> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().id.equals(ch.id)) {
                it.remove();
            }
        }
        list.add(0, ch);
        while (list.size() > 20) {
            list.remove(list.size() - 1);
        }
        try {
            JSONArray arr = new JSONArray();
            for (LiveChannel c : list) {
                arr.put(c.toJson());
            }
            SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_RECENTS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public interface M3uCallback {
        void onSuccess(List<LiveChannel> channels);
        void onError(String message);
    }

    public static void parseRemoteM3uAsync(final String sourceUrl, final String categoryTag, final M3uCallback cb) {
        new Thread(() -> {
            List<LiveChannel> channels = new ArrayList<>();
            String err = null;
            try {
                URL u = new URL(sourceUrl);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", HttpSupport.USER_AGENT);
                c.setInstanceFollowRedirects(true);
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    String title = null;
                    String logo = "";
                    String group = categoryTag;
                    int count = 0;
                    while ((line = r.readLine()) != null && count++ < 30000 && channels.size() < 120) {
                        line = line.trim();
                        if (line.startsWith("#EXTINF")) {
                            int comma = line.lastIndexOf(',');
                            title = comma >= 0 ? line.substring(comma + 1).trim() : "Canal TV";

                            Matcher mLogo = Pattern.compile("tvg-logo=\"([^\"]*)\"").matcher(line);
                            logo = mLogo.find() ? mLogo.group(1) : "";

                            Matcher mGroup = Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
                            group = mGroup.find() ? mGroup.group(1) : categoryTag;
                        } else if (title != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                            String id = "m3u_" + Math.abs((title + line).hashCode());
                            channels.add(new LiveChannel(id, title, group, line, "", logo, "HD", "En directo", "Guía 24/7"));
                            title = null;
                            logo = "";
                        }
                    }
                } finally {
                    c.disconnect();
                }
            } catch (Exception ex) {
                err = ex.getMessage();
            }

            final String finalErr = err;
            if (cb != null) {
                if (finalErr != null) cb.onError(finalErr);
                else cb.onSuccess(channels);
            }
        }, "live-m3u-parse").start();
    }

    public static final class LiveSportEvent {
        public final String id;
        public final String title;
        public final String tournament;
        public final String timeStatus;
        public final boolean isLiveNow;
        public final String streamUrl;
        public final String icon;
        public final String logoUrl;

        public LiveSportEvent(String id, String title, String tournament, String timeStatus, boolean isLiveNow, String streamUrl, String icon, String logoUrl) {
            this.id = id;
            this.title = title;
            this.tournament = tournament;
            this.timeStatus = timeStatus;
            this.isLiveNow = isLiveNow;
            this.streamUrl = streamUrl;
            this.icon = icon != null ? icon : "⚽";
            this.logoUrl = logoUrl != null ? logoUrl : "";
        }

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("title", title);
                o.put("tournament", tournament);
                o.put("timeStatus", timeStatus);
                o.put("isLiveNow", isLiveNow);
                o.put("streamUrl", streamUrl);
                o.put("icon", icon);
                o.put("logoUrl", logoUrl);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static LiveSportEvent fromJson(JSONObject o) {
            return new LiveSportEvent(
                    o.optString("id"),
                    o.optString("title"),
                    o.optString("tournament"),
                    o.optString("timeStatus"),
                    o.optBoolean("isLiveNow", true),
                    o.optString("streamUrl"),
                    o.optString("icon", "⚽"),
                    o.optString("logoUrl", "")
            );
        }
    }

    private static final String KEY_SPORTS_DIARY = "sports_diary_cache_json";

    public static List<LiveSportEvent> fetchSportsDiarySync(Context ctx, boolean forceNetwork) {
        List<LiveSportEvent> list = new ArrayList<>();
        SharedPreferences sp = ctx != null ? ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) : null;

        if (forceNetwork || sp == null || !sp.contains(KEY_SPORTS_DIARY)) {
            try {
                URL u = new URL("https://api.wqxag.com/diaries.json");
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setRequestProperty("Referer", "https://futbol-libre.app/");

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    conn.disconnect();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray data = root.optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        for (int i = 0; i < data.length() && list.size() < 25; i++) {
                            JSONObject item = data.optJSONObject(i);
                            if (item == null) continue;
                            JSONObject attr = item.optJSONObject("attributes");
                            if (attr == null) continue;

                            String desc = attr.optString("diary_description", "Partido en Vivo").replace("\n", " ").trim();
                            String hour = attr.optString("diary_hour", "20:00");
                            if (hour.length() >= 5) hour = hour.substring(0, 5);

                            String countryName = "Fútbol";
                            String icon = "⚽";
                            JSONObject country = attr.optJSONObject("country");
                            if (country != null) {
                                JSONObject cData = country.optJSONObject("data");
                                if (cData != null) {
                                    JSONObject cAttr = cData.optJSONObject("attributes");
                                    if (cAttr != null) {
                                        countryName = cAttr.optString("name", "Deporte");
                                    }
                                }
                            }
                            String lowDesc = desc.toLowerCase();
                            String lowCountry = countryName.toLowerCase();
                            if (lowCountry.contains("nba") || lowDesc.contains("nba") || lowDesc.contains("basketball")) icon = "🏀";
                            else if (lowCountry.contains("f1") || lowDesc.contains("f1") || lowDesc.contains("formula")) icon = "🏎️";
                            else if (lowCountry.contains("nfl") || lowDesc.contains("nfl")) icon = "🏈";
                            else if (lowCountry.contains("tenis") || lowDesc.contains("atp")) icon = "🎾";
                            else if (lowCountry.contains("nhl") || lowDesc.contains("hockey")) icon = "🏒";

                            String channelTitle = countryName;
                            String streamTarget = "";

                            JSONObject embeds = attr.optJSONObject("embeds");
                            if (embeds != null) {
                                JSONArray eData = embeds.optJSONArray("data");
                                if (eData != null && eData.length() > 0) {
                                    JSONObject firstEmb = eData.optJSONObject(0);
                                    if (firstEmb != null) {
                                        JSONObject embAttr = firstEmb.optJSONObject("attributes");
                                        if (embAttr != null) {
                                            channelTitle = embAttr.optString("embed_name", countryName);
                                            streamTarget = embAttr.optString("embed_iframe", "");
                                        }
                                    }
                                }
                            }

                            if (!streamTarget.isEmpty()) {
                                String id = "diary_" + item.optInt("id", i);
                                list.add(new LiveSportEvent(id, desc, countryName + " · " + channelTitle, "⏰ " + hour + " · En Vivo", true, streamTarget, icon, ""));
                            }
                        }

                        if (!list.isEmpty() && sp != null) {
                            JSONArray cacheArr = new JSONArray();
                            for (LiveSportEvent ev : list) {
                                cacheArr.put(ev.toJson());
                            }
                            sp.edit().putString(KEY_SPORTS_DIARY, cacheArr.toString()).apply();
                        }
                    }
                }
            } catch (Exception ex) {
                android.util.Log.w("LiveTvEngine", "Error actualizando agenda de deportes: " + ex.getMessage());
            }
        }

        if (list.isEmpty() && sp != null && sp.contains(KEY_SPORTS_DIARY)) {
            try {
                JSONArray arr = new JSONArray(sp.getString(KEY_SPORTS_DIARY, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    list.add(LiveSportEvent.fromJson(arr.getJSONObject(i)));
                }
            } catch (Exception ignored) {}
        }

        return list;
    }

    public static String resolveDynamicStream(String rawUrl, Map<String, String> outHeaders) {
        if (rawUrl == null || rawUrl.isEmpty()) return "";
        if (rawUrl.endsWith(".m3u8") && !rawUrl.contains("tvf90.com")) {
            return rawUrl;
        }

        String target = rawUrl;
        if (target.contains("r=")) {
            int rIdx = target.indexOf("r=");
            String b64 = target.substring(rIdx + 2);
            int amp = b64.indexOf('&');
            if (amp > 0) b64 = b64.substring(0, amp);
            try {
                byte[] decoded = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                target = new String(decoded, "UTF-8").trim();
            } catch (Exception ignored) {}
        }

        if (target.contains("tvf90.com")) {
            try {
                String streamName = "";
                Matcher mStream = Pattern.compile("stream=([a-zA-Z0-9_-]+)").matcher(target);
                if (mStream.find()) {
                    streamName = mStream.group(1);
                }
                if (!streamName.isEmpty()) {
                    String resolveUrl = "https://tvf90.com/5.php?stream=" + streamName;
                    URL u = new URL(resolveUrl);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setRequestProperty("Referer", "https://tvf90.com/1.php?stream=" + streamName);
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line);
                    }
                    r.close();
                    conn.disconnect();

                    Matcher mM3u8 = Pattern.compile("(https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*)").matcher(sb.toString());
                    if (mM3u8.find()) {
                        String realM3u8 = mM3u8.group(1);
                        if (outHeaders != null) {
                            outHeaders.put("Referer", "https://tvf90.com/");
                            outHeaders.put("Origin", "https://tvf90.com");
                            outHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                        }
                        return realM3u8;
                    }
                }
            } catch (Exception ex) {
                android.util.Log.w("LiveTvEngine", "Error resolviendo stream dinámico: " + ex.getMessage());
            }
        }
        return target;
    }

    public static final String DEFAULT_IPTV_ORG_AR_URL = "https://iptv-org.github.io/iptv/countries/ar.m3u";
    private static final String PREF_IPTV_ORG = "leo_tv_iptv_org";
    private static final String KEY_IPTV_URL = "iptv_org_ar_url";

    public static String getIptvOrgArUrl(Context ctx) {
        return ctx.getSharedPreferences(PREF_IPTV_ORG, Context.MODE_PRIVATE)
                .getString(KEY_IPTV_URL, DEFAULT_IPTV_ORG_AR_URL);
    }

    public static void setIptvOrgArUrl(Context ctx, String url) {
        ctx.getSharedPreferences(PREF_IPTV_ORG, Context.MODE_PRIVATE)
                .edit().putString(KEY_IPTV_URL, url).apply();
    }

    public static List<LiveChannel> getIptvOrgArgentinaChannels(Context ctx) {
        List<LiveChannel> out = new ArrayList<>();
        try {
            String jsonStr = null;
            java.io.File customFile = new java.io.File(ctx.getFilesDir(), "iptv_org_ar_custom.json");
            if (customFile.exists() && customFile.length() > 100) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(customFile);
                     BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    jsonStr = sb.toString();
                }
            }
            if (jsonStr == null) {
                try (java.io.InputStream is = ctx.getAssets().open("iptv_org_ar.json");
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    jsonStr = sb.toString();
                }
            }

            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("id", "iptv_ar_" + i);
                String name = obj.optString("name", "Canal " + (i + 1));
                String logo = obj.optString("logo", "");
                String group = obj.optString("group", "General");
                JSONArray streams = obj.optJSONArray("streams");
                String streamUrl = "";
                String backupUrl = "";
                if (streams != null && streams.length() > 0) {
                    streamUrl = streams.getJSONObject(0).optString("url", "");
                    if (streams.length() > 1) {
                        backupUrl = streams.getJSONObject(1).optString("url", "");
                    }
                }
                if (!streamUrl.isEmpty()) {
                    String epgSub = (streams != null && streams.length() > 1) 
                            ? ("Alternativas: " + streams.length() + " señales") 
                            : "Señal Oficial en Directo";
                    out.add(new LiveChannel(id, name, "iptv_org_ar", streamUrl, backupUrl, logo, "HD", group, epgSub));
                }
            }
        } catch (Exception e) {
            android.util.Log.w("LiveTvEngine", "Error cargando iptv_org_ar: " + e.getMessage());
        }
        return out;
    }

    public static int updateIptvOrgArFromNetwork(Context ctx) throws Exception {
        String m3uUrl = getIptvOrgArUrl(ctx);
        URL endpoint = new URL(m3uUrl);
        HttpURLConnection c = (HttpURLConnection) endpoint.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "VideoPropia-TV/0.4");
        c.setInstanceFollowRedirects(true);

        LinkedHashMap<String, JSONObject> channels = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            String line, title = null, group = "General", logo = "", id = "", ua = "", ref = "";
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXTINF:")) {
                    int comma = line.lastIndexOf(',');
                    title = comma >= 0 ? line.substring(comma + 1).trim() : "Sin nombre";
                    Matcher mId = Pattern.compile("tvg-id=\"([^\"]*)\"").matcher(line);
                    id = mId.find() ? mId.group(1) : "";
                    Matcher mLogo = Pattern.compile("tvg-logo=\"([^\"]*)\"").matcher(line);
                    logo = mLogo.find() ? mLogo.group(1) : "";
                    Matcher mGrp = Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
                    group = mGrp.find() ? mGrp.group(1) : "General";
                    Matcher mUa = Pattern.compile("user-agent=\"([^\"]*)\"").matcher(line);
                    ua = mUa.find() ? mUa.group(1) : "";
                    Matcher mRef = Pattern.compile("referrer=\"([^\"]*)\"").matcher(line);
                    ref = mRef.find() ? mRef.group(1) : "";
                } else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                    ua = line.substring(line.indexOf('=') + 1);
                } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                    ref = line.substring(line.indexOf('=') + 1);
                } else if (title != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                    String baseId = id.contains("@") ? id.split("@")[0].trim() : id;
                    String cleanName = title.replaceAll("\\s*\\([^)]*\\)", "").trim();
                    String key = (!baseId.isEmpty()) ? baseId.toLowerCase() : cleanName.toLowerCase();

                    JSONObject chObj = channels.get(key);
                    if (chObj == null) {
                        chObj = new JSONObject();
                        chObj.put("id", id);
                        chObj.put("name", cleanName.isEmpty() ? title : cleanName);
                        chObj.put("logo", logo);
                        chObj.put("group", group);
                        chObj.put("streams", new JSONArray());
                        channels.put(key, chObj);
                    } else {
                        if (chObj.optString("logo").isEmpty() && !logo.isEmpty()) chObj.put("logo", logo);
                    }
                    JSONObject sObj = new JSONObject();
                    sObj.put("url", line);
                    sObj.put("label", title);
                    sObj.put("ua", ua);
                    sObj.put("ref", ref);
                    chObj.getJSONArray("streams").put(sObj);

                    title = null;
                    id = "";
                    logo = "";
                    group = "General";
                    ua = "";
                    ref = "";
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    title = null;
                }
            }
        } finally {
            c.disconnect();
        }

        if (!channels.isEmpty()) {
            JSONArray outArr = new JSONArray();
            for (JSONObject chObj : channels.values()) {
                outArr.put(chObj);
            }
            java.io.File customFile = new java.io.File(ctx.getFilesDir(), "iptv_org_ar_custom.json");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(customFile);
                 java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(fos, "UTF-8")) {
                osw.write(outArr.toString());
            }
        }
        return channels.size();
    }

    public static final Map<String, String> CATEGORY_LABELS = new LinkedHashMap<>();
    static {
        CATEGORY_LABELS.put("general", "General");
        CATEGORY_LABELS.put("news", "Noticias");
        CATEGORY_LABELS.put("sports", "Deportes");
        CATEGORY_LABELS.put("movies", "Películas y cine");
        CATEGORY_LABELS.put("series", "Series");
        CATEGORY_LABELS.put("documentary", "Documentales");
        CATEGORY_LABELS.put("kids", "Infantil");
        CATEGORY_LABELS.put("animation", "Animación");
        CATEGORY_LABELS.put("music", "Música");
        CATEGORY_LABELS.put("entertainment", "Entretenimiento");
        CATEGORY_LABELS.put("comedy", "Comedia");
        CATEGORY_LABELS.put("cooking", "Cocina");
        CATEGORY_LABELS.put("culture", "Cultura");
        CATEGORY_LABELS.put("science", "Ciencia y tecnología");
        CATEGORY_LABELS.put("travel", "Viajes");
        CATEGORY_LABELS.put("classic", "Clásicos");
        CATEGORY_LABELS.put("family", "Familiar");
        CATEGORY_LABELS.put("education", "Educación");
        CATEGORY_LABELS.put("uncategorized", "Sin categoría");
    }

    public static final class IptvOrgChannel {
        public final String id;
        public final String name;
        public final String logo;
        public final String country;
        public final String countryName;
        public final List<String> categories;
        public final List<String> streamUrls;

        public IptvOrgChannel(String id, String name, String logo, String country, String countryName, List<String> categories, List<String> streamUrls) {
            this.id = id;
            this.name = name;
            this.logo = logo != null ? logo : "";
            this.country = country != null ? country : "";
            this.countryName = countryName != null ? countryName : country;
            this.categories = categories != null ? categories : new ArrayList<>();
            this.streamUrls = streamUrls != null ? streamUrls : new ArrayList<>();
        }

        public String getPrimaryStream() {
            return !streamUrls.isEmpty() ? streamUrls.get(0) : "";
        }

        public String getBackupStream() {
            return streamUrls.size() > 1 ? streamUrls.get(1) : "";
        }
    }

    private static List<IptvOrgChannel> iptvCatalogCache = null;

    public static synchronized List<IptvOrgChannel> loadIptvOrgCatalog(Context ctx) {
        if (iptvCatalogCache != null && !iptvCatalogCache.isEmpty()) {
            return iptvCatalogCache;
        }

        List<IptvOrgChannel> list = new ArrayList<>();
        try {
            String jsonStr = null;
            java.io.File customFile = new java.io.File(ctx.getFilesDir(), "iptv_catalog_updated.json");
            if (customFile.exists() && customFile.length() > 500) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(customFile);
                     BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    jsonStr = sb.toString();
                }
            }
            if (jsonStr == null) {
                try (java.io.InputStream is = ctx.getAssets().open("iptv_catalog.json");
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    jsonStr = sb.toString();
                }
            }

            if (jsonStr != null) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String id = obj.optString("id", "ch_" + i);
                    String name = obj.optString("name", "Canal " + (i + 1));
                    String logo = obj.optString("logo", "");
                    String country = obj.optString("country", "");
                    String countryName = obj.optString("country_name", country);

                    List<String> cats = new ArrayList<>();
                    JSONArray cArr = obj.optJSONArray("categories");
                    if (cArr != null) {
                        for (int j = 0; j < cArr.length(); j++) {
                            cats.add(cArr.getString(j));
                        }
                    }

                    List<String> streams = new ArrayList<>();
                    JSONArray sArr = obj.optJSONArray("streams");
                    if (sArr != null) {
                        for (int j = 0; j < sArr.length(); j++) {
                            JSONObject sObj = sArr.optJSONObject(j);
                            if (sObj != null) {
                                String u = sObj.optString("url", "");
                                if (!u.isEmpty()) streams.add(u);
                            }
                        }
                    }

                    if (!streams.isEmpty()) {
                        list.add(new IptvOrgChannel(id, name, logo, country, countryName, cats, streams));
                    }
                }
            }
            iptvCatalogCache = list;
        } catch (Exception ex) {
            android.util.Log.w("LiveTvEngine", "Error cargando iptv_catalog: " + ex.getMessage());
        }
        return list;
    }
}

