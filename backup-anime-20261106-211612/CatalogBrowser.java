package ar.appvideo.player;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import android.util.LruCache;

/** Interfaz nativa de catalogo, busqueda y episodios, con consultas acotadas. */
public final class CatalogBrowser {
    public interface Host {
        boolean active();
        void play(CatalogData.Entry entry, int season, int episode, String title);
    }
    public static final class State {
        String kind = "movie", query = "";
        int page = 1, season = 0;
        CatalogData.Entry selected;
    }
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(2);
    private static final ExecutorService IMAGES = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8*1024*1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private final Activity activity;
    private final Host host;
    private final VideoProvider provider;
    private final State state;
    private final LinearLayout results;
    private final TextView message;
    private final EditText search;
    private boolean disposed;
    private int generation;
    private Future<?> pending;
    private final int violet = Color.rgb(129,107,245);
    private final int panel = Color.rgb(27,33,52);

    public CatalogBrowser(Activity owner, LinearLayout body, State saved, VideoProvider source, Host events) {
        activity=owner; host=events; state=saved; provider=source;
        LinearLayout searchRow = row();
        search = new EditText(owner);
        search.setTextColor(Color.WHITE); search.setHintTextColor(Color.LTGRAY);
        search.setTextSize(18); search.setSingleLine(true);
        search.setHint("Buscar peliculas, series o novelas"); search.setText(state.query);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchRow.addView(search, new LinearLayout.LayoutParams(0,80,1));
        button(searchRow,"Buscar",this::submitSearch,180);
        body.addView(searchRow);
        search.setOnEditorActionListener((v,action,event) -> {
            if (action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { submitSearch(); return true; }
            return false;
        });
        LinearLayout filters=row();
        button(filters,"Peliculas",()-> category("movie"),240);
        if(provider.supportsSeries())button(filters,"Series",()-> category("serie"),240);
        if(provider.supportsNovels())button(filters,"Novelas",()-> category("novel"),240);
        body.addView(filters);
        message=label("",17); body.addView(message);
        results=new LinearLayout(owner);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
        if (state.selected != null) {
            if (state.season > 0) episodes(state.selected,state.season,1);
            else detail(state.selected);
        } else catalog();
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);return row;
    }
    private TextView label(String value,int size) {
        TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(Color.LTGRAY);
        text.setPadding(4,12,4,12);return text;
    }
    private GradientDrawable background(boolean selected) {
        GradientDrawable d=new GradientDrawable();d.setColor(selected?violet:panel);
        d.setCornerRadius(14);d.setStroke(2,selected?Color.WHITE:Color.rgb(70,80,113));return d;
    }
    private Button button(LinearLayout parent,String text,Runnable action,int width) {
        Button b=new Button(activity);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(18);
        b.setAllCaps(false);b.setFocusable(true);b.setBackground(background(false));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,88);lp.setMargins(0,8,12,8);
        parent.addView(b,lp);b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,focused)->b.setBackground(background(focused)));return b;
    }
    private void submitSearch() {
        String query=search.getText().toString().trim();
        if (query.length()>120) {message.setText("La busqueda es demasiado larga.");return;}
        ((android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(search.getWindowToken(),0);
        state.query=query;state.page=1;state.selected=null;state.season=0;catalog();
    }
    private void category(String kind) {
        state.kind=kind;state.query="";state.page=1;state.selected=null;state.season=0;
        search.setText("");catalog();
    }
    private boolean current(int token) {return !disposed && host.active() && generation==token;}
    private interface Work { Object run() throws Exception; }
    private interface Render { void run(Object result) throws Exception; }
    private void load(String loading,Work work,Render render) {
        final int token=++generation;
        if(pending!=null) pending.cancel(true);
        results.removeAllViews();message.setText(loading);
        pending=NETWORK.submit(()-> {
            try {
                Object data=work.run();
                activity.runOnUiThread(()-> {
                    if(!current(token))return;
                    try {render.run(data);}catch(Exception error){message.setText("No se pudo mostrar el resultado.");}
                });
            }catch(Exception error) {
                activity.runOnUiThread(()-> {if(current(token))message.setText("No se pudo cargar. Reintenta con Buscar o cambia de categoria.");});
            }
        });
    }
    private void catalog() {
        final String kind=state.kind,query=state.query;final int page=state.page;
        load("Cargando catalogo de "+provider.name()+"...",()->provider.catalog(kind,query,page),data-> {
            CatalogData.CatalogPage response=(CatalogData.CatalogPage)data;
            message.setText((query.isEmpty()?"Catalogo":"Resultados de \""+query+"\"")
                +" · Pagina "+response.page+" · "+(response.totalKnown ? response.total+" titulos informados por la fuente" : response.entries.size()+" titulos en esta pagina"));
            if(response.entries.isEmpty()) {results.addView(label("No se encontraron titulos.",19));return;}
            int columns=activity.getResources().getDisplayMetrics().widthPixels>=1200?4:2;
            int size=(activity.getResources().getDisplayMetrics().widthPixels-96-(columns-1)*18)/columns;
            GridLayout grid=new GridLayout(activity);grid.setColumnCount(columns);results.addView(grid);
            for(CatalogData.Entry entry:response.entries) card(grid,entry,size);
            LinearLayout pages=row();
            if(page>1)button(pages,"Pagina anterior",()->{state.page=page-1;catalog();},300);
            if(response.next)button(pages,"Cargar mas · Pagina "+(page+1),()->{state.page=page+1;catalog();},400);
            results.addView(pages);
        });
    }
    private void card(GridLayout grid,CatalogData.Entry entry,int width) {
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=width;lp.height=(int)(width*1.25)+120;
        lp.setMargins(0,0,18,18);grid.addView(card,lp);
        ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(panel);card.addView(image,new LinearLayout.LayoutParams(-1,(int)(width*1.25)));
        loadImage(image,entry.poster);
        Button title=button(card,entry.title+(entry.year>0?" ("+entry.year+")":"")
            +(entry.isMovie()?"":" · Serie"),()->detail(entry),-1);
        title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLayoutParams(new LinearLayout.LayoutParams(-1,112));
        image.setOnClickListener(v->detail(entry));
    }
    private void loadImage(ImageView target,String address) {
        if(address.isEmpty())return;
        Bitmap cached=CACHE.get(address);if(cached!=null){target.setImageBitmap(cached);return;}
        IMAGES.submit(()-> {
            HttpURLConnection conn=null;
            try {
                conn=(HttpURLConnection)new URL(address).openConnection();
                conn.setConnectTimeout(6000);conn.setReadTimeout(8000);
                if(conn.getResponseCode()!=200)return;
                Bitmap bitmap;
                try(InputStream in=conn.getInputStream()){bitmap=BitmapFactory.decodeStream(in);}
                if(bitmap==null)return;CACHE.put(address,bitmap);
                activity.runOnUiThread(()->{if(!disposed)target.setImageBitmap(bitmap);});
            }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
        });
    }
    private void detail(CatalogData.Entry entry) {
        state.selected=entry;state.season=0;
        ++generation;if(pending!=null)pending.cancel(true);
        results.removeAllViews();message.setText(entry.title+(entry.year>0?" ("+entry.year+")":""));
        button(results,"Volver a resultados",()->{state.selected=null;catalog();},-1);
        if(!entry.overview.isEmpty())results.addView(label(entry.overview,19));
        results.addView(label("La disponibilidad del video se comprueba al reproducir.",17));
        if(entry.isMovie()) {
            button(results,"Reproducir "+entry.title,()->host.play(entry,0,0,entry.title),-1).requestFocusFromTouch();
        }else {
            final int token=generation;
            message.setText(entry.title+" · Cargando temporadas...");
            pending=NETWORK.submit(()-> {
                try{
                    JSONArray seasons=provider.seasons(entry);
                    activity.runOnUiThread(()->{
                        if(!current(token))return;message.setText(entry.title+" · "+seasons.length()+" temporadas");
                        for(int i=0;i<seasons.length();i++){
                            JSONObject season=seasons.optJSONObject(i);if(season==null)continue;
                            int number=season.optInt("season");
                            button(results,season.optString("name","Temporada "+number)+(season.has("count") ? " · "+season.optInt("count")+" episodios" : ""),()->episodes(entry,number,1),-1);
                        }
                        if(seasons.length()==0)results.addView(label("No hay temporadas disponibles.",19));
                    });
                }catch(Exception error){activity.runOnUiThread(()->{if(current(token))message.setText("No se pudieron cargar las temporadas.");});}
            });
        }
    }
    private void episodes(CatalogData.Entry entry,int season,int page) {
        state.selected=entry;state.season=season;
        load("Cargando "+entry.title+" · Temporada "+season,()->provider.episodes(entry,season,page),data-> {
            JSONObject response=(JSONObject)data;
            message.setText(entry.title+" · Temporada "+season);
            button(results,"Volver a temporadas",()->detail(entry),-1);
            JSONArray episodes=response.optJSONArray("episodes");
            if(episodes==null||episodes.length()==0){results.addView(label("No hay episodios disponibles.",19));return;}
            for(int i=0;i<episodes.length();i++){
                JSONObject episode=episodes.optJSONObject(i);if(episode==null)continue;
                int number=episode.optInt("episode");
                String title=entry.title+" · T"+season+" E"+number+" · "+episode.optString("title","");
                Button b=button(results,title,()->host.play(entry,season,number,title),-1);
                if(!episode.optBoolean("playable",false)){b.setEnabled(false);b.setText(title+" · No disponible");}
            }
            JSONObject pagination=response.optJSONObject("pagination");
            if(page>1)button(results,"Episodios anteriores",()->episodes(entry,season,page-1),-1);
            if(pagination!=null&&pagination.optBoolean("has_next"))button(results,"Mas episodios",()->episodes(entry,season,page+1),-1);
        });
    }
    public boolean back() {
        if(state.selected==null)return false;
        if(state.season>0)detail(state.selected);
        else {state.selected=null;catalog();}
        return true;
    }
    public void dispose() {
        disposed=true;++generation;if(pending!=null)pending.cancel(true);
    }
}
