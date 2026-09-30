package betterterraingen.v2.filters;

/** Standalone structured ruin generator exposed beside the legacy scatter generator. */
public class StructuredRuinGenerateFilter extends RuinGenerateFilter {
    public StructuredRuinGenerateFilter() {
        generationMode = GenerationMode.structured;
        structurePreset = StructurePreset.large;
    }
}
