package random;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.ctype.UnlockableContent;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.world.Block;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Swaps only the main visual regions and keeps all gameplay/content fields intact. */
public final class RandomTextureController{
    private final RandomStateStore store;
    private final IdentityHashMap<UnlockableContent, Visual> visuals = new IdentityHashMap<>();
    private final LinkedHashMap<String, Visual> byId = new LinkedHashMap<>();
    private Map<String, String> mapping = new LinkedHashMap<>();
    private boolean active;

    public RandomTextureController(RandomStateStore store){
        this.store = store;
    }

    public void begin(String cacheKey, boolean enabled){
        reset();
        if(!enabled || Vars.content == null || Core.atlas == null) return;

        collectVisuals();
        List<String> ids = new ArrayList<>(byId.keySet());
        if(ids.isEmpty()) return;

        mapping = store.load(cacheKey, "texture");
        if(!RandomStateStore.mappingComplete(ids, mapping)){
            mapping = RandomPermutationController.mapping(ids,
                RandomPermutationController.seed(store.clientSeed() + "|" + cacheKey + "|texture"));
            store.save(cacheKey, "texture", mapping);
        }

        for(Visual target : visuals.values()){
            Visual source = byId.get(mapping.get(id(target.content)));
            if(source != null) apply(target, source);
        }
        active = true;
    }

    private void collectVisuals(){
        add(Vars.content.blocks());
        add(Vars.content.items());
        add(Vars.content.units());
    }

    private void add(Iterable<? extends UnlockableContent> contents){
        for(UnlockableContent content : contents){
            if(!eligible(content)) continue;
            Visual visual = Visual.capture(content);
            if(visual == null) continue;
            visuals.put(content, visual);
            byId.put(id(content), visual);
        }
    }

    private boolean eligible(UnlockableContent content){
        if(content == null || content.removed || content.isHidden() || content.hideDatabase || !content.generateIcons) return false;
        if(content instanceof Block block){
            if(block == Blocks.air || !valid(block.region)) return false;
            return valid(content.fullIcon) || valid(content.uiIcon);
        }
        if(content instanceof Item item){
            return !item.hidden && (valid(content.fullIcon) || valid(content.uiIcon));
        }
        if(content instanceof UnitType unit){
            return !unit.internal && (valid(unit.region) || valid(content.fullIcon) || valid(content.uiIcon));
        }
        return false;
    }

    private void apply(Visual target, Visual source){
        if(target.block != null){
            int variants = target.originalVariantRegions == null ? 0 : target.originalVariantRegions.length;
            if(target.block.variants > 0 && variants > 0){
                TextureRegion[] replacement = new TextureRegion[variants];
                for(int i = 0; i < variants; i++){
                    TextureRegion targetRegion = target.originalVariantRegions[i];
                    replacement[i] = fitted(source.bodyFrame(i), targetRegion);
                }
                target.block.variantRegions = replacement;
                target.block.region = replacement[0];
            }else{
                target.block.region = fitted(source.bodyFrame(0), target.originalBody);
            }
        }else if(target.item != null){
            target.appliedFull = fitted(source.iconFrame(false, 0), target.originalFull);
            target.appliedUi = fitted(source.iconFrame(true, 0), target.originalUi);
            target.item.fullIcon = target.appliedFull;
            target.item.uiIcon = target.appliedUi;
        }else if(target.unit != null){
            target.unit.region = fitted(source.bodyFrame(0), target.originalBody);
        }

        if(target.block != null || target.unit != null){
            target.appliedFull = fitted(source.iconFrame(false, 0), target.originalFull);
            target.appliedUi = fitted(source.iconFrame(true, 0), target.originalUi);
            target.content.fullIcon = target.appliedFull;
            target.content.uiIcon = target.appliedUi;
        }
    }

    /** Called after Mindustry's own animated item listeners on every update tick. */
    public void update(){
        if(!active) return;
        for(Visual target : visuals.values()){
            if(target.item == null || target.item.frames <= 0 || target.appliedFull == null || target.appliedUi == null) continue;
            int frameCount = Math.max(1, target.item.frames * (target.item.transitionFrames + 1));
            float frameTime = Math.max(0.001f, target.item.frameTime);
            int frame = (int)(Time.globalTime / frameTime) % frameCount;
            Visual source = byId.get(mapping.get(id(target.content)));
            if(source == null) continue;
            setFitted(target.appliedFull, source.iconFrame(false, frame), target.originalFull);
            setFitted(target.appliedUi, source.iconFrame(true, frame), target.originalUi);
            // Item.loadIcon() assigns its own animation frame directly to these fields.
            // Re-attach the independent randomized regions after that listener runs.
            target.item.fullIcon = target.appliedFull;
            target.item.uiIcon = target.appliedUi;
        }
    }

    public void reset(){
        for(Visual visual : visuals.values()) visual.restore();
        visuals.clear();
        byId.clear();
        mapping = new LinkedHashMap<>();
        active = false;
    }

    public boolean active(){
        return active;
    }

    public static float fitScale(int sourceWidth, int sourceHeight, float sourceScale,
                                 int targetWidth, int targetHeight, float targetScale){
        if(sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return targetScale;
        return targetScale * Math.min(targetWidth / (float)sourceWidth, targetHeight / (float)sourceHeight) * sourceScale;
    }

    private static TextureRegion fitted(TextureRegion source, TextureRegion target){
        if(source == null || !valid(source)) return target == null ? null : new TextureRegion(target);
        TextureRegion result = new TextureRegion(source);
        if(target != null){
            result.scale = fitScale(source.width, source.height, source.scale, target.width, target.height, target.scale);
        }
        return result;
    }

    private static void setFitted(TextureRegion destination, TextureRegion source, TextureRegion target){
        if(destination == null || source == null) return;
        destination.set(source);
        if(target != null){
            destination.scale = fitScale(source.width, source.height, source.scale, target.width, target.height, target.scale);
        }
    }

    private static boolean valid(TextureRegion region){
        return region != null && region.found() && region.width > 0 && region.height > 0;
    }

    private static String id(UnlockableContent content){
        return content.getContentType().name() + ":" + content.name;
    }

    private static final class Visual{
        final UnlockableContent content;
        final Block block;
        final Item item;
        final UnitType unit;
        final TextureRegion originalBody;
        final TextureRegion[] originalVariantRegions;
        final TextureRegion originalFull;
        final TextureRegion originalUi;
        final TextureRegion[] bodyFrames;
        TextureRegion appliedFull;
        TextureRegion appliedUi;

        private Visual(UnlockableContent content, Block block, Item item, UnitType unit,
                       TextureRegion originalBody, TextureRegion[] originalVariantRegions,
                       TextureRegion originalFull, TextureRegion originalUi, TextureRegion[] bodyFrames){
            this.content = content;
            this.block = block;
            this.item = item;
            this.unit = unit;
            this.originalBody = originalBody;
            this.originalVariantRegions = originalVariantRegions;
            this.originalFull = originalFull;
            this.originalUi = originalUi;
            this.bodyFrames = bodyFrames;
        }

        static Visual capture(UnlockableContent content){
            TextureRegion full = content.fullIcon;
            TextureRegion ui = content.uiIcon;
            if(content instanceof Block block){
                TextureRegion[] variants = block.variantRegions;
                TextureRegion[] body = variants != null && variants.length > 0 ? variants.clone() : new TextureRegion[]{block.region};
                return new Visual(content, block, null, null, block.region, variants == null ? null : variants.clone(), full, ui, body);
            }
            if(content instanceof Item item){
                TextureRegion[] frames = itemFrames(item);
                if(frames.length == 0) frames = new TextureRegion[]{full != null ? full : ui};
                return new Visual(content, null, item, null, full != null ? full : ui, null, full, ui, frames);
            }
            if(content instanceof UnitType unit){
                TextureRegion body = unit.region != null ? unit.region : full != null ? full : ui;
                return new Visual(content, null, null, unit, body, null, full, ui, new TextureRegion[]{body});
            }
            return null;
        }

        private static TextureRegion[] itemFrames(Item item){
            if(item.frames <= 0 || Core.atlas == null) return new TextureRegion[0];
            ArrayList<TextureRegion> result = new ArrayList<>();
            int total = item.frames * (item.transitionFrames + 1);
            for(int i = 0; i < total; i++){
                String name;
                if(item.transitionFrames <= 0){
                    name = item.name + (i + 1);
                }else if(i % (item.transitionFrames + 1) == 0){
                    name = item.name + (i / (item.transitionFrames + 1) + 1);
                }else{
                    name = item.name + "-t" + i;
                }
                TextureRegion frame = Core.atlas.find(name);
                if(valid(frame)) result.add(frame);
            }
            return result.toArray(new TextureRegion[0]);
        }

        TextureRegion bodyFrame(int index){
            if(bodyFrames.length == 0) return originalBody;
            return bodyFrames[Math.floorMod(index, bodyFrames.length)];
        }

        TextureRegion iconFrame(boolean ui, int index){
            if(item != null && bodyFrames.length > 0) return bodyFrame(index);
            TextureRegion icon = ui ? originalUi : originalFull;
            return valid(icon) ? icon : originalBody;
        }

        void restore(){
            if(block != null){
                block.region = originalBody;
                block.variantRegions = originalVariantRegions;
            }else if(item != null){
                item.fullIcon = originalFull;
                item.uiIcon = originalUi;
            }else if(unit != null){
                unit.region = originalBody;
                unit.fullIcon = originalFull;
                unit.uiIcon = originalUi;
            }

            if(block != null || unit != null){
                content.fullIcon = originalFull;
                content.uiIcon = originalUi;
            }
        }
    }
}
