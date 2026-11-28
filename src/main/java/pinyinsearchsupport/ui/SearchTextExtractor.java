package pinyinsearchsupport.ui;

import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.EventListener;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.Tooltip;
import arc.struct.Seq;

import java.util.IdentityHashMap;

public final class SearchTextExtractor{
    private static final int MAX_DEPTH = 16;
    private static final int MAX_NODES = 128;
    private static final int MAX_TEXT_LENGTH = 512;

    private SearchTextExtractor(){}

    public static String extract(Element element){
        if(element == null) return null;
        try{
            StringBuilder sb = new StringBuilder(64);
            IdentityHashMap<Element, Boolean> visited = new IdentityHashMap<Element, Boolean>(16);
            int[] budget = new int[]{0};
            collect(element, 0, visited, budget, sb);
            return sb.length() > 0 ? sb.toString() : null;
        }catch(Throwable ignored){
            return null;
        }
    }

    private static void collect(Element element, int depth, IdentityHashMap<Element, Boolean> visited,
                                int[] budget, StringBuilder sb){
        if(element == null || depth > MAX_DEPTH || sb.length() >= MAX_TEXT_LENGTH) return;
        if(budget[0] >= MAX_NODES) return;
        if(visited.put(element, Boolean.TRUE) != null) return;
        budget[0]++;

        if(element instanceof Label){
            CharSequence text = ((Label)element).getText();
            if(text != null && text.length() > 0){
                appendText(sb, text);
            }
            collectTooltips(element, depth + 1, visited, budget, sb);
            return;
        }

        if(element instanceof Image){
            // Standalone image has no direct text; inspect tooltip only.
            collectTooltips(element, depth + 1, visited, budget, sb);
            return;
        }

        if(element instanceof Group){
            Seq<Element> children = ((Group)element).getChildren();
            if(children != null){
                for(int i = 0; i < children.size; i++){
                    if(sb.length() >= MAX_TEXT_LENGTH || budget[0] >= MAX_NODES) break;
                    collect(children.get(i), depth + 1, visited, budget, sb);
                }
            }
            collectTooltips(element, depth + 1, visited, budget, sb);
            return;
        }

        collectTooltips(element, depth + 1, visited, budget, sb);
    }

    private static void collectTooltips(Element element, int depth, IdentityHashMap<Element, Boolean> visited,
                                        int[] budget, StringBuilder sb){
        if(depth > MAX_DEPTH || sb.length() >= MAX_TEXT_LENGTH || budget[0] >= MAX_NODES) return;
        Seq<EventListener> listeners = element.getListeners();
        if(listeners == null || listeners.isEmpty()) return;

        for(int i = 0; i < listeners.size; i++){
            if(sb.length() >= MAX_TEXT_LENGTH || budget[0] >= MAX_NODES) break;
            EventListener l = listeners.get(i);
            if(l instanceof Tooltip){
                Element cont = ((Tooltip)l).getContainer();
                if(cont != null){
                    collect(cont, depth, visited, budget, sb);
                }
            }
        }
    }

    private static void appendText(StringBuilder sb, CharSequence text){
        if(text == null || text.length() == 0 || sb.length() >= MAX_TEXT_LENGTH) return;
        int remaining = MAX_TEXT_LENGTH - sb.length();
        if(sb.length() > 0){
            if(remaining <= 1) return;
            sb.append(' ');
            remaining--;
        }
        int copyLen = Math.min(text.length(), remaining);
        sb.append(text, 0, copyLen);
    }
}
