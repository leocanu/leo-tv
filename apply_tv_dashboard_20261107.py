from pathlib import Path
import shutil
p=Path(r'C:\workspace\app-video-propia-android-v1\app\src\main\java\ar\appvideo\player\MainActivity.java')
backup=p.with_name('MainActivity.pre-tv-dashboard-20261107.java.bak')
if not backup.exists():shutil.copy2(p,backup)
s=p.read_text(encoding='utf-8')
assert 'private void allSources()' not in s, 'Dashboard already installed'
s=s.replace('    private void home() {','    private void allSources() {',1)
needle='''        page = "home"; channel = "";
        LinearLayout body = newPage("inicio", "Tus canales, en un solo lugar",'''
assert needle in s
s=s.replace(needle,'''        page = "sources"; channel = "";
        LinearLayout body = newPage("fuentes", "Todas las fuentes",
'''.rstrip('\n') ,1)
# The newline above is already present in source; validate syntax below.
needle='''    private void openChannel(String name) {'''
assert needle in s
dashboard=r'''
    private GradientDrawable tvShape(int fill, int border, int corner) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(corner); d.setStroke(2,border);
        return d;
    }
    private Button tvAction(String label, String detail, Runnable action, int width, int height) {
        Button b=new Button(this);
        b.setText(label+(detail.isEmpty()?"":"\n"+detail));
        b.setAllCaps(false);b.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        b.setPadding(20,10,12,10);b.setTextSize(18);b.setTextColor(Color.WHITE);
        b.setFocusable(true);
        b.setBackground(tvShape(Color.rgb(31,41,64),Color.rgb(64,78,110),20));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,height);
        lp.setMargins(0,0,15,12);b.setLayoutParams(lp);
        b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,selected)->{
            b.setBackground(tvShape(selected?Color.rgb(68,88,155):Color.rgb(31,41,64),
                selected?Color.WHITE:Color.rgb(64,78,110),20));
            b.setScaleX(selected?1.025f:1f);b.setScaleY(selected?1.025f:1f);
        });
        return b;
    }
    private void tvNav(LinearLayout side, String label, Runnable action) {
        Button b=tvAction(label,"",action,-1,70);
        b.setTextSize(18);b.setPadding(18,8,8,8);
        side.addView(b);
    }
    private boolean tvAnime(String name) {
        return name.equals("TioAnime")||name.equals("LatAnime")||name.equals("AnimeAV1")
            ||name.equals("JKAnime")||name.equals("MonosChinos")
            ||name.equals("MundoDonghua")||name.equals("VerAni.me")
            ||name.equals("VerAnimes")||name.equals("BeatZ Anime");
    }
    private String tvBadge(String name) {
        if(name.equals("Demo"))return "Muestra de video";
        if(name.equals("Blender Open Movies")||name.equals("Internet Archive"))return "Videos autorizados";
        for(int i=0;i<channels.length();i++){
            JSONObject entry=channels.optJSONObject(i);
            if(entry==null||!name.equals(entry.optString("name")))continue;
            String state=entry.optString("status");
            if("previously_playback_tested".equals(state))return "Reproducción probada";
            if("catalog_and_episodes_tested_playback_pending".equals(state))return "Anime · video pendiente";
            if(entry.optBoolean("enabled",false))return "Adaptador en revisión";
            return "No integrado";
        }
        return "Explorar";
    }
    private void tvRow(LinearLayout body,String heading,String subtitle,String[] names) {
        TextView title=text(heading,25,Color.WHITE);title.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);
        tlp.setMargins(0,25,0,3);body.addView(title,tlp);
        TextView hint=text(subtitle,15,Color.rgb(173,184,206));
        body.addView(hint);
        android.widget.HorizontalScrollView scroller=new android.widget.HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(1,17,16,10);
        for(String name:names){
            Button card=tvAction(name,tvBadge(name),()->openChannel(name),283,132);
            rail.addView(card);
        }
        scroller.addView(rail);body.addView(scroller);
    }
    private void tvSection(String group) {
        page="section";channel="";
        String title=group.equals("anime")?"Anime":"Películas y series";
        LinearLayout body=newPage(title,title,
            "Elegí una fuente. La disponibilidad del video se verifica al reproducir.");
        Button back=item("← Volver al inicio",body,this::home);
        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(3);
        body.addView(grid);
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int width=Math.max(240,(dm.widthPixels-120)/3-24);
        for(int i=0;i<channels.length();i++){
            JSONObject entry=channels.optJSONObject(i);
            if(entry==null||!entry.optBoolean("enabled",false))continue;
            String name=entry.optString("name");
            if(tvAnime(name)!=group.equals("anime"))continue;
            Button b=tile(name,tvBadge(name),grid,width,()->openChannel(name));
        }
        back.requestFocus();
    }
    private void home() {
        if(catalogBrowser!=null){catalogBrowser.dispose();catalogBrowser=null;}
        if(resolutionTask!=null)resolutionTask.cancel(true);
        ++providerPlayRequest;providerLoading=false;
        page="home";channel="";
        root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(11,16,29));
        LinearLayout side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(18,28,18,15);
        side.setBackgroundColor(Color.rgb(20,29,48));
        root.addView(side,new LinearLayout.LayoutParams(255,-1));
        TextView brand=text("VIDEO\nPROPIA",27,Color.WHITE);
        brand.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);
        bp.setMargins(14,0,0,20);side.addView(brand,bp);
        TextView menu=text("EXPLORAR",13,Color.rgb(143,166,221));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);
        mp.setMargins(14,0,0,12);side.addView(menu,mp);
        tvNav(side,"⌂  Inicio",this::home);
        tvNav(side,"▶  Películas y series",()->tvSection("movies"));
        tvNav(side,"☆  Anime",()->tvSection("anime"));
        tvNav(side,"▦  Todas las fuentes",this::allSources);
        tvNav(side,"▷  Video de prueba",()->openChannel("Demo"));
        tvNav(side,"ⓘ  Estado",()->openChannel("Estado"));
        android.widget.Space filler=new android.widget.Space(this);
        side.addView(filler,new LinearLayout.LayoutParams(1,0,1));
        TextView footer=text("Usá las flechas del control\npara explorar el contenido",13,Color.rgb(147,160,183));
        side.addView(footer);
        LinearLayout main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(29,24,25,10);
        root.addView(main,new LinearLayout.LayoutParams(0,-1,1));
        TextView eyebrow=text("TU ENTRETENIMIENTO, A UN CLIC",14,Color.rgb(166,158,250));
        main.addView(eyebrow);
        TextView welcome=text("¿Qué querés ver hoy?",34,Color.WHITE);
        welcome.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);
        wp.setMargins(0,5,0,8);main.addView(welcome,wp);
        TextView note=text("Elegí una categoría o descubrí una fuente. Las opciones en revisión están identificadas.",16,Color.rgb(190,200,219));
        main.addView(note);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);pageScroll=scroll;
        main.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        tvRow(body,"Empezá por acá","Videos de muestra y fuentes con reproducción previamente probada",
            new String[]{"Demo","Blender Open Movies","Internet Archive","CineHax","LaMovie","SeriesMetro","Pelispedia"});
        tvRow(body,"Películas y series","Otras fuentes para explorar · adaptadores todavía en revisión",
            new String[]{"CineCalidad","Cuevana","Pelisplus","FlixLatam","VerPelisTop",
                "HenaoJara","Seriesflix","AresHD","CineHDPlus","CineHax+",
                "DeTodo Peliculas","Cineplus123","DoramasFlix"});
        tvRow(body,"Anime","Catálogos de anime · disponibilidad del video pendiente de verificar",
            new String[]{"TioAnime","LatAnime","AnimeAV1","JKAnime","MonosChinos",
                "MundoDonghua","VerAni.me","VerAnimes","BeatZ Anime"});
        Button viewAll=tvAction("▦  Ver las 32 fuentes","Incluye las 6 que todavía no tienen adaptador",this::allSources,470,100);
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(470,100);
        alp.setMargins(0,17,0,35);body.addView(viewAll,alp);
        setContentView(root);
        side.post(()->{if(side.getChildCount()>3)side.getChildAt(3).requestFocus();});
    }
'''
s=s.replace(needle,dashboard+'\n'+needle,1)
# Make legacy source cards short rectangles and add home handling to back.
s=s.replace('        params.height = size;','        params.height = 150;',1)
s=s.replace('''        else super.onBackPressed();''','''        else if(page.equals("sources")||page.equals("section"))home();
        else super.onBackPressed();''',1)
p.write_text(s,encoding='utf-8')
print('PATCHED',len(s),'BACKUP',backup)
