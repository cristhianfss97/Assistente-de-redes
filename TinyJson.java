package com.assistente;

import java.util.*;

/**
 * TinyJson
 * ----------
 * Parser/serializador mínimo de JSON (apenas string->string).
 * Evita dependência externa só para salvar o histórico.
 *
 * Formato: {"k":"v","k2":"v2"} (sem arrays/objetos aninhados).
 */
public class TinyJson {
    public static Map<String,String> parse(String s){
        Map<String,String> m = new LinkedHashMap<>();
        if (s == null) return m;
        s = s.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) return m;
        s = s.substring(1, s.length()-1).trim();
        if (s.isEmpty()) return m;

        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i=0;i<s.length();i++){
            char c = s.charAt(i);
            if (c=='"' && (i==0 || s.charAt(i-1)!='\\')) inQ = !inQ;
            if (c==',' && !inQ){
                parts.add(cur.toString());
                cur.setLength(0);
            } else cur.append(c);
        }
        parts.add(cur.toString());

        for (String p: parts){
            int k = p.indexOf(':');
            if (k<0) continue;
            String key = unq(p.substring(0,k).trim());
            String val = unq(p.substring(k+1).trim());
            m.put(key, val);
        }
        return m;
    }

    public static String stringify(Map<String,String> m){
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String,String> e: m.entrySet()){
            if (!first) sb.append(",");
            first = false;
            sb.append(q(e.getKey())).append(":").append(q(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String q(String s){
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","") + "\"";
    }
    private static String unq(String s){
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length()>=2){
            s = s.substring(1, s.length()-1);
        }
        s = s.replace("\\n","\n").replace("\\\"","\"").replace("\\\\","\\");
        return s;
    }
}
