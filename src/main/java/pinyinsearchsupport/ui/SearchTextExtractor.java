package pinyinsearchsupport.ui;

import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.EventListener;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.Tooltip;
import arc.struct.ObjectMap;
import arc.struct.Seq;

public final class SearchTextExtractor{
    private static final ObjectMap<Element, String> cache = new ObjectMap<>();

    private SearchTextExtractor(){}

    public static String extract(Element element){
        if(element == null) return null;

        String cached = cache.get(element);
        if(cached != null) return cached;

        String result = doExtract(element);
        if(result != null){
            cache.put(element, result);
        }
        return result;
    }

    public static void invalidate(){
        cache.clear();
    }

    private static String doExtract(Element element){
        if(element == null) return null;

        if(element instanceof Label){
            String t = ((Label)element).getText().toString();
            StringBuilder sb = new StringBuilder();
            appendText(sb, t);
            appendText(sb, extractTooltip(element));
            return sb.length() > 0 ? sb.toString() : null;
        }

        if(element instanceof Image){
            // standalone image has no searchable text; check tooltip only
            return extractTooltip(element);
        }

        if(element instanceof Button){
            // collect child text + tooltip
            StringBuilder sb = new StringBuilder();
            appendGroupText((Group)element, sb);
            appendText(sb, extractTooltip(element));
            return sb.length() > 0 ? sb.toString() : null;
        }

        if(element instanceof Group){
            StringBuilder sb = new StringBuilder();
            appendGroupText((Group)element, sb);
            appendText(sb, extractTooltip(element));
            return sb.length() > 0 ? sb.toString() : null;
        }

        // fallback: try tooltip
        return extractTooltip(element);
    }

    private static void appendGroupText(Group group, StringBuilder sb){
        Seq<Element> children = group.getChildren();
        for(int i = 0; i < children.size; i++){
            appendText(sb, extract(children.get(i)));
        }
    }

    private static void appendText(StringBuilder sb, String text){
        if(text == null || text.isEmpty()) return;
        if(sb.length() > 0) sb.append(' ');
        sb.append(text);
    }

    private static String extractTooltip(Element element){
        Seq<EventListener> listeners = element.getListeners();
        for(int i = 0; i < listeners.size; i++){
            EventListener l = listeners.get(i);
            if(l instanceof Tooltip){
                Tooltip tooltip = (Tooltip)l;
                Element cont = tooltip.getContainer();
                if(cont instanceof Group){
                    StringBuilder sb = new StringBuilder();
                    appendGroupText((Group)cont, sb);
                    if(sb.length() > 0) return sb.toString();
                }else if(cont instanceof Label){
                    String t = ((Label)cont).getText().toString();
                    if(t != null && !t.isEmpty()) return t;
                }
            }
        }
        return null;
    }
}
