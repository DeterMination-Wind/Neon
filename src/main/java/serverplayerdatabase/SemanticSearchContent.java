package serverplayerdatabase;

import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

public final class SemanticSearchContent{
    private static final String queryPlaceholder = "输入查询，支持 +加分 -减分 ---排除，例如 pvp +辱骂 ---萌新";

    public interface Host{
        boolean compactUi();
        String formatTime(long millis);
        String escapeMarkup(String text);
        String safeLine(String text, int maxLen);
        void copy(String value);
        void openUid(String uid);
        void showInfo(String message);
    }

    public final Table root = new Table();
    private final Host host;
    private final EmbeddingIndex index;
    private final Table result = new Table();
    private TextField queryField;

    public SemanticSearchContent(Host host, EmbeddingIndex index){
        this.host = host;
        this.index = index;
        build();
    }

    public void build(){
        root.clear();
        root.left().top().defaults().left().pad(4f);
        boolean compact = host.compactUi();

        root.table(Styles.black3, box -> {
            box.left().top().defaults().left().pad(4f).growX();
            box.add("语义搜索").left().row();
            box.add(statusLine()).left().wrap().row();

            if(compact){
                box.table(line -> {
                    line.left().defaults().left().padRight(6f);
                    queryField = line.field("", text -> {}).growX().get();
                    queryField.setMessageText(queryPlaceholder);
                }).growX().row();
                box.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    line.button("搜索", this::runSearch).height(38f).growX();
                    line.button("刷新状态", this::refreshStatus).height(38f).growX();
                }).growX().row();
            }else{
                box.table(line -> {
                    line.left().defaults().left().padRight(6f);
                    queryField = line.field("", text -> {}).growX().get();
                    queryField.setMessageText(queryPlaceholder);
                    line.button("搜索", this::runSearch).height(38f);
                    line.button("刷新状态", this::refreshStatus).height(38f);
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
            result.add("请输入查询内容。").left();
            return;
        }

        if(index == null || !index.isAvailable()){
            result.add(host.escapeMarkup(index == null ? "语义搜索未初始化。" : index.status())).left().wrap();
            return;
        }
        if(index.isRebuilding()){
            result.add(host.escapeMarkup(index.status())).left().wrap();
            return;
        }
        if(!index.isReady()){
            result.add(host.escapeMarkup(index.status())).left().wrap();
            return;
        }

        Seq<EmbeddingIndex.SearchResult> hits = index.search(query, 40);
        if(hits.isEmpty()){
            result.add("没有找到匹配结果。").left();
            return;
        }

        for(EmbeddingIndex.SearchResult hit : hits){
            result.table(Styles.black3, card -> {
                card.left().top().defaults().left().pad(3f).growX();
                card.add(host.escapeMarkup(
                    host.formatTime(hit.chat.time)
                        + " | "
                        + host.safeLine(hit.chat.senderName, 26)
                        + " | 分数 "
                        + String.format(java.util.Locale.ROOT, "%.3f", hit.score)
                )).left().wrap().row();
                card.add(host.escapeMarkup("UID: " + hit.chat.uid + " | 服: " + host.safeLine(hit.chat.server, 30))).left().wrap().row();
                card.add(host.escapeMarkup("内容: " + host.safeLine(hit.chat.message, 160))).left().wrap().row();
                card.table(line -> {
                    line.left().defaults().left().padRight(6f).growX();
                    line.button(hit.chat.uid, Styles.defaultt, () -> host.openUid(hit.chat.uid)).height(32f).growX();
                    line.button(Icon.copySmall, Styles.emptyi, () -> host.copy(hit.chat.message)).size(32f);
                }).growX().row();
            }).growX().padTop(3f).row();
        }
    }

    public void refreshStatus(){
        result.clear();
        result.left().top();
        result.add(host.escapeMarkup(statusLine())).left().wrap();
    }

    private String statusLine(){
        if(index == null) return "语义搜索未初始化。";
        return index.status();
    }
}
