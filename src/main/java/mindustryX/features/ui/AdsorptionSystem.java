package mindustryX.features.ui;

import arc.Core;
import arc.func.Cons;
import arc.math.geom.Rect;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;

import java.util.Locale;

public class AdsorptionSystem {
    public static final AdsorptionSystem INSTANCE = new AdsorptionSystem();
    public static final float ADSORPTION_DISTANCE = 16f;

    public enum Axis {
        X, Y
    }

    public enum Anchor {
        Leading,
        Center,
        Trailing
    }

    public enum ConstraintType {
        AlignLeading(Anchor.Leading, Anchor.Leading),
        AlignTrailing(Anchor.Trailing, Anchor.Trailing),
        AlignCenter(Anchor.Center, Anchor.Center),
        AttachTrailing(Anchor.Leading, Anchor.Trailing),
        AttachLeading(Anchor.Trailing, Anchor.Leading);

        public final Anchor sourceAnchor;
        public final Anchor targetAnchor;

        ConstraintType(Anchor sourceAnchor, Anchor targetAnchor) {
            this.sourceAnchor = sourceAnchor;
            this.targetAnchor = targetAnchor;
        }
    }

    public static class Constraint {
        public Axis axis;
        public String target;
        public ConstraintType type;

        public Constraint() {
            this(Axis.X, "", ConstraintType.AlignLeading);
        }

        public Constraint(Axis axis, String target, ConstraintType type) {
            this.axis = axis;
            this.target = target;
            this.type = type;
        }

        public Element getTargetPoint() {
            return INSTANCE.all.get(target);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Constraint{%s -> %s (%s)}", axis, target, type);
        }
    }

    private static class Candidate {
        final Constraint constraint;
        final float distance;

        Candidate(Constraint constraint, float distance) {
            this.constraint = constraint;
            this.distance = distance;
        }
    }

    public static class Constraints {
        public final Constraint constraintX;
        public final Constraint constraintY;

        public Constraints(Constraint constraintX, Constraint constraintY) {
            this.constraintX = constraintX;
            this.constraintY = constraintY;
        }
    }

    public static class Element {
        public final String name;
        public final Rect rect = new Rect();
        public final ObjectSet<Element> dependencies = new ObjectSet<>();
        public long lastUpdate;

        public Element(String name) {
            this.name = name;
        }

        public void reset(float x, float y, float width, float height) {
            lastUpdate = Core.graphics.getFrameId();
            rect.set(x, y, width, height);
            INSTANCE.all.put(name, this);
            dependencies.clear();
        }

        public float computeAnchor(Axis axis, Anchor anchor) {
            switch (axis) {
                case X:
                    switch (anchor) {
                        case Leading:
                            return rect.x;
                        case Center:
                            return rect.x + rect.width / 2f;
                        case Trailing:
                            return rect.x + rect.width;
                        default:
                            return rect.x;
                    }
                case Y:
                    switch (anchor) {
                        case Leading:
                            return rect.y;
                        case Center:
                            return rect.y + rect.height / 2f;
                        case Trailing:
                            return rect.y + rect.height;
                        default:
                            return rect.y;
                    }
                default:
                    return 0f;
            }
        }

        public void applyConstraint(Constraint constraint) {
            if (constraint == null) return;
            Element target = constraint.getTargetPoint();
            if (target == null) return;

            dependencies.add(target);
            float cur = computeAnchor(constraint.axis, constraint.type.sourceAnchor);
            float tar = target.computeAnchor(constraint.axis, constraint.type.targetAnchor);
            float delta = tar - cur;

            if (constraint.axis == Axis.X) {
                rect.x += delta;
            } else {
                rect.y += delta;
            }
        }

        public Constraints findBestConstraints() {
            Seq<Element> available = INSTANCE.filterCandidates(this);

            Constraint bestX = null;
            float bestXDistance = Float.MAX_VALUE;
            Constraint bestY = null;
            float bestYDistance = Float.MAX_VALUE;

            for (Element target : available) {
                Candidate cx = findBestConstraint(target, Axis.X);
                if (cx != null && cx.distance < bestXDistance) {
                    bestXDistance = cx.distance;
                    bestX = cx.constraint;
                }

                Candidate cy = findBestConstraint(target, Axis.Y);
                if (cy != null && cy.distance < bestYDistance) {
                    bestYDistance = cy.distance;
                    bestY = cy.constraint;
                }
            }

            return new Constraints(bestX, bestY);
        }

        private Candidate findBestConstraint(Element target, Axis axis) {
            Candidate best = null;
            for (ConstraintType type : ConstraintType.values()) {
                float dis = Math.abs(computeAnchor(axis, type.sourceAnchor) - target.computeAnchor(axis, type.targetAnchor));
                if (dis >= ADSORPTION_DISTANCE) continue;

                Candidate c = new Candidate(new Constraint(axis, target.name, type), dis);
                if (best == null || c.distance < best.distance) {
                    best = c;
                }
            }
            return best;
        }

        public void remove() {
            Element current = INSTANCE.all.get(name);
            if (current == this) {
                INSTANCE.all.remove(name);
            }
        }
    }

    final ObjectMap<String, Element> all = new ObjectMap<>();
    private final Seq<Runnable> updaters = new Seq<>();

    private final Element scene = new Element("scene");
    private final Element placementRect = new Element("placementRect");

    private AdsorptionSystem() {
        updaters.add(() -> scene.reset(0f, 0f, Core.scene.getWidth(), Core.scene.getHeight()));
    }

    public Element getScene() {
        return scene;
    }

    public Element getPlacementRect() {
        return placementRect;
    }

    public void addDynamic(String name, Cons<Element> updater) {
        Element elem = new Element(name);
        updaters.add(() -> updater.get(elem));
    }

    public void update() {
        for (Runnable updater : updaters) {
            updater.run();
        }
    }

    private Seq<Element> filterCandidates(Element forPoint) {
        ObjectMap<Element, Seq<Element>> reverseDeps = new ObjectMap<>();
        for (ObjectMap.Entry<String, Element> entry : all) {
            Element point = entry.value;
            for (Element dep : point.dependencies) {
                Seq<Element> seq = reverseDeps.get(dep);
                if (seq == null) {
                    seq = new Seq<>();
                    reverseDeps.put(dep, seq);
                }
                seq.add(point);
            }
        }

        ObjectSet<Element> excluded = new ObjectSet<>();
        dfsExclude(forPoint, reverseDeps, excluded);

        Rect around = new Rect().set(forPoint.rect).grow(2f * ADSORPTION_DISTANCE);
        Seq<Element> result = new Seq<>();
        long frame = Core.graphics.getFrameId();

        for (ObjectMap.Entry<String, Element> entry : all) {
            Element it = entry.value;
            if (it.lastUpdate != frame) continue;
            if (!around.overlaps(it.rect)) continue;
            if (excluded.contains(it)) continue;
            result.add(it);
        }

        return result;
    }

    private void dfsExclude(Element current, ObjectMap<Element, Seq<Element>> reverseDeps, ObjectSet<Element> excluded) {
        if (!excluded.add(current)) return;
        Seq<Element> next = reverseDeps.get(current);
        if (next == null) return;
        for (Element dep : next) {
            dfsExclude(dep, reverseDeps, excluded);
        }
    }
}
