package ar.appvideo.player;

import java.util.regex.*;

/** Decodifica cadenas de empaquetado publico sin ejecutar JavaScript. */
final class ScriptDecoder {
    private static final String DIGITS="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern PACK=Pattern.compile(
        "\\}\\(\\s*'((?:\\\\.|[^'\\\\])*+)'\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*'((?:\\\\.|[^'\\\\])*+)'\\.split\\('\\|'\\)",
        Pattern.DOTALL);
    private static final Pattern WORD=Pattern.compile("\\b[0-9A-Za-z]+\\b");
    private ScriptDecoder() {}
    static String expand(String html) {
        StringBuilder out=new StringBuilder(html);
        Matcher match=PACK.matcher(html);int blocks=0;
        while(match.find() && blocks++<6) {
            try {
                int radix=Integer.parseInt(match.group(2));
                int count=Integer.parseInt(match.group(3));
                if(radix<2||radix>62||count>10000)continue;
                String payload=literal(match.group(1));
                String[] symbols=literal(match.group(4)).split("\\|",-1);
                Matcher words=WORD.matcher(payload);StringBuffer decoded=new StringBuffer();
                while(words.find()) {
                    String word=words.group();long index=0;boolean valid=true;
                    for(int i=0;i<word.length();i++) {
                        int digit=DIGITS.indexOf(word.charAt(i));
                        if(digit<0||digit>=radix){valid=false;break;}
                        index=index*radix+digit;
                        if(index>=symbols.length){valid=false;break;}
                    }
                    String replacement=valid&&!symbols[(int)index].isEmpty()?symbols[(int)index]:word;
                    words.appendReplacement(decoded,Matcher.quoteReplacement(replacement));
                }
                words.appendTail(decoded);out.append('\n').append(decoded);
                if(out.length()>2097152)break;
            }catch(RuntimeException ignored){}
        }
        return out.toString();
    }
    private static String literal(String input) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<input.length();i++) {
            char value=input.charAt(i);
            if(value!='\\'||i+1>=input.length()){out.append(value);continue;}
            char next=input.charAt(++i);
            if(next=='n')out.append('\n');else if(next=='r')out.append('\r');
            else if(next=='t')out.append('\t');
            else if((next=='x'&&i+2<input.length())||(next=='u'&&i+4<input.length())) {
                int size=next=='x'?2:4;
                try {out.append((char)Integer.parseInt(input.substring(i+1,i+1+size),16));i+=size;}
                catch(NumberFormatException error){out.append(next);}
            } else out.append(next);
        }
        return out.toString();
    }
}
