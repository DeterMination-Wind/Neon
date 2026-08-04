package serverplayerdatabase;

import arc.Core;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

public final class SemanticSearchContent{
    public interface Host{
        boolean compactUi();
        String formatTime(long millis);
        String escapeMarkup(String text);
        String safeLine(String text, int maxLen);
        void copy(String value);
        void openUid(String uid);
        void showInfo(String message);
        void ensureSemanticSearchReady(String query);
        boolean canRunSemanticSearch();
        String semanticSearchStatus();
        Seq<EmbeddingIndex.SearchResult> searchSemantic(String query, int limit);
    }

    public final Table root = new Table();
    private final Host host;
    private final Table result = new Table();
    private TextField queryField;
    private boolean showingStatusOnly = true;
    private String lastStatusLine = "";

    public SemanticSearchContent(Host host){
        this.host = host;
        build();
    }

    public void build(){
        root.clear();
        root.left().top().defaults().left().pad(4f);
        boolean compact = host.compactUi();

        root.table(Styles.black3, box -> {
            box.left().top().defaults().left().pad(4f).growX();
            box.add(bundle("spdb.semantic.title", "Semantic search")).left().row();
            box.add(statusLine()).left().wrap().row();

            if(compact){
                box.table(line -> {
                    line.left().defaults().left().padRight(6f);
                    queryField = line.field("", text -> {}).growX().get();
                    queryField.setMessageText(bundle("spdb.semantic.query.placeholder", "Enter a query. Use +include, -include, and ---exclude, e.g. pvp +insult ---newbie"));
                }).growX().row();
                box.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    line.button(bundle("spdb.semantic.action.search", "Search"), this::runSearch).height(38f).growX();
                    line.button(bundle("spdb.semantic.action.refresh", "Refresh status"), this::refreshStatus).height(38f).growX();
                }).growX().row();
            }else{
                box.table(line -> {
                    line.left().defaults().left().padRight(6f);
                    queryField = line.field("", text -> {}).growX().get();
                    queryField.setMessageText(bundle("spdb.semantic.query.placeholder", "Enter a query. Use +include, -include, and ---exclude, e.g. pvp +insult ---newbie"));
                    line.button(bundle("spdb.semantic.action.search", "Search"), this::runSearch).height(38f);
                    line.button(bundle("spdb.semantic.action.refresh", "Refresh status"), this::refreshStatus).height(38f);
                }).growX().row();
            }

            box.pane(result).scrollX(false).growX().row();
        }).grow().minWidth(0f).minHeight(0f);

        refreshStatus();
    }

    public void setQueryText(String query){
        if(queryField != null) queryField.setText(query == null ? "" : query);
    }

    public void runSearch(){
        result.clear();
        result.left().top();

        String query = queryField == null ? null : queryField.getText();
        if(query == null || query.trim().isEmpty()){
            showingStatusOnly = false;
            result.add(bundle("spdb.semantic.query.required", "Enter a query first.")).left();
            return;
        }

        if(!host.canRunSemanticSearch()){
            host.ensureSemanticSearchReady(query);
            showStatusOnly(host.semanticSearchStatus());
            return;
        }

        Seq<EmbeddingIndex.SearchResult> hits = host.searchSemantic(query, 40);
        if(hits.isEmpty()){
            showingStatusOnly = false;
            result.add(bundle("spdb.semantic.result.empty", "No matching results found.")).left();
            return;
        }

        showingStatusOnly = false;
        for(EmbeddingIndex.SearchResult hit : hits){
            result.table(Styles.black3, card -> {
                card.left().top().defaults().left().pad(3f).growX();
                card.add(host.escapeMarkup(
                    host.formatTime(hit.chat.time)
                        + " | "
                        + host.safeLine(hit.chat.senderName, 26)
                        + " | Score "
                        + String.format(java.util.Locale.ROOT, "%.3f", hit.score)
                )).left().wrap().row();
                card.add(host.escapeMarkup(bundle("spdb.semantic.result.uid", "UID: ") + hit.chat.uid + " | " + bundle("spdb.semantic.result.server", "Server: ") + host.safeLine(hit.chat.server, 30))).left().wrap().row();
                card.add(host.escapeMarkup(bundle("spdb.semantic.result.message", "Content: ") + host.safeLine(hit.chat.message, 160))).left().wrap().row();
                card.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    line.button(hit.chat.uid, Styles.defaultt, () -> host.openUid(hit.chat.uid)).height(32f).growX();
                    line.button(Icon.copySmall, Styles.emptyi, () -> host.copy(hit.chat.message)).size(32f);
                }).growX().row();
            }).growX().padTop(3f).row();
        }
    }

    public void refreshStatus(){
        showStatusOnly(statusLine());
    }

    public void tick(){
        String status = statusLine();
        if(showingStatusOnly && !status.equals(lastStatusLine)){
            showStatusOnly(status);
        }
    }

    private String statusLine(){
        return host.semanticSearchStatus();
    }

    private void showStatusOnly(String status){
        showingStatusOnly = true;
        lastStatusLine = status == null ? "" : status;
        result.clear();
        result.left().top();
        result.add(host.escapeMarkup(lastStatusLine)).left().wrap();
    }

    private static String bundle(String key, String fallback){
        return Core.bundle == null ? fallback : Core.bundle.get(key, fallback);
    }
}
