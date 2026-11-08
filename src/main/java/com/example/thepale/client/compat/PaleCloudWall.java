package com.example.thepale.client.compat;

import com.example.thepale.client.PaleShaderFog;
import com.example.thepale.client.PaleWallRenderer;
import com.example.thepale.config.ClientConfig;
import com.example.thepale.worldgen.PaleShape;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The fog wall as Distant Horizons draws it, so it shows out to the LOD horizon. Built like DH's own clouds: flat
 * slabs of cloud, merged from a cloud map into big rectangles and shaded the same way, here stacked into a bank.
 * Each slab layer gets its own map: sparse at innerRadius, solid at outerRadius, thinning out toward the top, so the
 * bank thickens smoothly with no rings. Close to the camera it gives way to the shader wall, fading in only where that
 * wall fades out (PaleWallRenderer#fadeEnd).
 */
final class PaleCloudWall {
    private static final String NAME = "thepale:cloud_wall";

    /** Size of one cloud map cell, and the thickness and number of the slab layers. */
    private static final int CELL = 32;
    private static final int LAYER_THICKNESS = 28;
    private static final int LAYERS = 9;
    /** Longest merged slab side, in cells, so no single slab reaches from the far bank into the shader wall's zone. */
    private static final int MAX_MERGE = 6;
    /** The bottom slab sits just below sea level so the bank meets the water. */
    private static final double BASE_Y = 58;

    /** The band is split into sections, so the part of it near the camera can be handed to the shader wall. */
    private static final int SECTORS = 48;
    private static final int RADIAL_BANDS = 4;
    // The opaque curtain on the band's outer edge (buildCurtain): where in the band, and from how low.
    private static final double CURTAIN_FROM = 0.86;
    private static final double CURTAIN_TO = 1.0;
    private static final int CURTAIN_BOTTOM = 40;
    /** Inside the sea of clouds (DECK_LAYERS), so the top never shows as an edge. */
    private static final int CURTAIN_TOP = 380;

    // The sea of clouds as DH draws it (buildDeck).
    private static final double[] DECK_LAYERS = {350, 400};
    private static final int DECK_THICKNESS = 30;
    private static final int DECK_CELL = 128;
    /** Cells per side of one deck section (a DH group, shown or hidden as a whole). */
    private static final int DECK_SECTION = 6;
    /** How far past the islands' bands the deck goes. */
    private static final double DECK_MARGIN = 2500;
    private static final double DECK_ALPHA = 0.5;
    private static final int DECK_SECTION_KEY = 1 << 28;
    /** The vanilla deck reaches this far at most (PaleCloudSea); DH's takes over around its edge. */
    private static final double VANILLA_DECK_EXTENT = 1600;

    /** Fades are applied in steps, so DH only re-uploads a section when its fade visibly changes. */
    private static final int FADE_STEPS = 10;

    /**
     * Everything DH draws for the Pale (cloud bank, curtain, sea of clouds) is the colour of the vanilla sea of clouds:
     * the Pale grey in sunlight (PaleCloudSea), so near and far read as the same clouds. DH darkens it at night itself.
     */
    private static final int RED = Math.round(Math.min(1f, PaleWallRenderer.RED * 1.27f) * 255);
    private static final int GREEN = Math.round(Math.min(1f, PaleWallRenderer.GREEN * 1.27f) * 255);
    private static final int BLUE = Math.round(Math.min(1f, PaleWallRenderer.BLUE * 1.27f) * 255);

    private final List<Section> sections;

    private PaleCloudWall(List<Section> sections) {
        this.sections = sections;
    }

    static PaleCloudWall build(ClientConfig.Settings config) {
        double inner = config.innerRadius();
        double outer = config.outerRadius();
        Map<Integer, List<DhApiRenderableBox>> boxesBySection = new HashMap<>();
        Map<Integer, List<Integer>> alphasBySection = new HashMap<>();

        // One cloud map over every island's band; each band follows its island's coast.
        PaleShape outline = PaleShape.client();
        double gridMinX = Double.MAX_VALUE;
        double gridMinZ = Double.MAX_VALUE;
        double gridMaxX = -Double.MAX_VALUE;
        double gridMaxZ = -Double.MAX_VALUE;
        for (PaleShape.Island island : outline.islands()) {
            double reach = island.reach(outer, outline.landEnd()) + CELL;
            gridMinX = Math.min(gridMinX, island.centerX() - reach);
            gridMinZ = Math.min(gridMinZ, island.centerZ() - reach);
            gridMaxX = Math.max(gridMaxX, island.centerX() + reach);
            gridMaxZ = Math.max(gridMaxZ, island.centerZ() + reach);
        }
        double originX = Math.floor(gridMinX / CELL) * CELL;
        double originZ = Math.floor(gridMinZ / CELL) * CELL;
        int sizeX = Mth.ceil((gridMaxX - originX) / CELL);
        int sizeZ = Mth.ceil((gridMaxZ - originZ) / CELL);
        for (int layer = 0; layer < LAYERS; layer++) {
            double height = layer / (double) (LAYERS - 1);
            // This layer's cloud map: which cells hold cloud.
            boolean[][] cloud = new boolean[sizeX][sizeZ];
            for (int i = 0; i < sizeX; i++) {
                for (int j = 0; j < sizeZ; j++) {
                    double x = originX + (i + 0.5) * CELL;
                    double z = originZ + (j + 0.5) * CELL;
                    double depth = (outline.effectiveRadius(x, z) - inner) / (outer - inner);
                    if (depth < -0.1 || depth > 1.0) {
                        continue;
                    }
                    // Coherent between layers (towers and overhangs, not noise), ragged at the edges.
                    double shape = valueNoise(x / 150, z / 150) * 0.7 + valueNoise(x / 60 + layer * 0.37, z / 60 - layer * 0.53) * 0.3;
                    double fill = smoothstep(-0.1, 0.55, depth) * (1 - smoothstep(0.2, 1.05, height + (1 - depth) * 0.45));
                    cloud[i][j] = shape < fill;
                }
            }
            double minY = BASE_Y + layer * LAYER_THICKNESS;
            // Merge into rectangles the way DH merges its cloud texture: a run along z, then grown along x.
            for (int i = 0; i < sizeX; i++) {
                for (int j = 0; j < sizeZ; j++) {
                    if (!cloud[i][j]) {
                        continue;
                    }
                    int endJ = j;
                    while (endJ < sizeZ && endJ - j < MAX_MERGE && cloud[i][endJ]) {
                        endJ++;
                    }
                    int endI = i + 1;
                    while (endI < sizeX && endI - i < MAX_MERGE && allSet(cloud[endI], j, endJ)) {
                        for (int k = j; k < endJ; k++) {
                            cloud[endI][k] = false;
                        }
                        endI++;
                    }
                    double minX = originX + i * CELL;
                    double minZ = originZ + j * CELL;
                    double maxX = originX + endI * CELL;
                    double maxZ = originZ + endJ * CELL;
                    double centerX = (minX + maxX) / 2;
                    double centerZ = (minZ + maxZ) / 2;
                    double depth = Mth.clamp((outline.effectiveRadius(centerX, centerZ) - inner) / (outer - inner), 0, 1);
                    // Thin at the inner edge, thick at the outer; paler toward the top, like the shader wall.
                    int alpha = (int) Math.round(255 * Mth.lerp(smoothstep(0, 1, depth), 0.3, 0.75) * (1 - 0.55 * height));

                    int section = sectionOf(outline, centerX, centerZ, depth);
                    boxesBySection.computeIfAbsent(section, s -> new ArrayList<>()).add(new DhApiRenderableBox(
                            new DhApiVec3d(minX, minY, minZ),
                            new DhApiVec3d(maxX, minY + LAYER_THICKNESS, maxZ),
                            new Color(RED, GREEN, BLUE, alpha),
                            EDhApiBlockMaterial.UNKNOWN));
                    alphasBySection.computeIfAbsent(section, s -> new ArrayList<>()).add(alpha);
                    j = endJ - 1;
                }
            }
        }

        buildCurtain(config, outline, originX, originZ, sizeX, sizeZ, boxesBySection, alphasBySection);

        Map<Integer, List<DhApiRenderableBox>> deckBySection = new HashMap<>();
        Map<Integer, List<Integer>> deckAlphasBySection = new HashMap<>();
        buildDeck(config, outline, gridMinX, gridMinZ, gridMaxX, gridMaxZ, deckBySection, deckAlphasBySection);

        // The same shading DH gives its clouds.
        DhApiRenderableBoxGroupShading shading = DhApiRenderableBoxGroupShading.getUnshaded();
        // Softer than DH's own cloud shading, so the boxes' faces don't stand out from each other.
        shading.north = shading.south = 0.96f;
        shading.east = shading.west = 0.92f;
        shading.top = 1.0f;
        shading.bottom = 0.88f;

        List<Section> sections = new ArrayList<>();
        for (Map.Entry<Integer, List<DhApiRenderableBox>> entry : boxesBySection.entrySet()) {
            List<DhApiRenderableBox> boxes = entry.getValue();
            IDhApiRenderableBoxGroup group = DhApi.Delayed.customRenderObjectFactory.createAbsolutePositionedGroup(NAME, boxes);
            group.setSkyLight(15);
            group.setBlockLight(0);
            group.setSsaoEnabled(false);
            group.setShading(shading);
            Section section = new Section(group, alphasBySection.get(entry.getKey()).stream().mapToInt(Integer::intValue).toArray(), false);
            group.setPreRenderFunc(section::preRender);
            sections.add(section);
        }
        for (Map.Entry<Integer, List<DhApiRenderableBox>> entry : deckBySection.entrySet()) {
            IDhApiRenderableBoxGroup group = DhApi.Delayed.customRenderObjectFactory.createAbsolutePositionedGroup(NAME, entry.getValue());
            group.setSkyLight(15);
            group.setBlockLight(0);
            group.setSsaoEnabled(false);
            group.setShading(shading);
            Section section = new Section(group, deckAlphasBySection.get(entry.getKey()).stream().mapToInt(Integer::intValue).toArray(), true);
            group.setPreRenderFunc(section::preRender);
            sections.add(section);
        }
        return new PaleCloudWall(sections);
    }

    /**
     * An opaque curtain of fog along the band's outer edge, from below the sea up into the sea of clouds. The cloud
     * bank in front of it is see-through, and behind it, across the Pale, lie the other islands: without it DH would
     * show them from any coast. Its top is inside the sea of clouds, so it never shows as an edge, and from above the
     * clouds it is hidden under them.
     */
    private static void buildCurtain(
            ClientConfig.Settings config, PaleShape outline, double originX, double originZ, int sizeX, int sizeZ,
            Map<Integer, List<DhApiRenderableBox>> boxesBySection, Map<Integer, List<Integer>> alphasBySection) {
        double inner = config.innerRadius();
        double outer = config.outerRadius();
        for (int i = 0; i < sizeX; i++) {
            int j = 0;
            while (j < sizeZ) {
                int top = curtainTop(outline, originX, originZ, i, j, inner, outer);
                if (top < 0) {
                    j++;
                    continue;
                }
                // A run along z of cells with the same top becomes one box.
                int end = j + 1;
                while (end < sizeZ && end - j < MAX_MERGE && curtainTop(outline, originX, originZ, i, end, inner, outer) == top) {
                    end++;
                }
                double minX = originX + i * CELL;
                double minZ = originZ + j * CELL;
                double maxX = minX + CELL;
                double maxZ = originZ + end * CELL;
                double centerX = (minX + maxX) / 2;
                double centerZ = (minZ + maxZ) / 2;
                double depth = Mth.clamp((outline.effectiveRadius(centerX, centerZ) - inner) / (outer - inner), 0, 1);
                int section = sectionOf(outline, centerX, centerZ, depth);
                boxesBySection.computeIfAbsent(section, key -> new ArrayList<>()).add(new DhApiRenderableBox(
                        new DhApiVec3d(minX, CURTAIN_BOTTOM, minZ),
                        new DhApiVec3d(maxX, top, maxZ),
                        new Color(RED, GREEN, BLUE, 255),
                        EDhApiBlockMaterial.UNKNOWN));
                alphasBySection.computeIfAbsent(section, key -> new ArrayList<>()).add(255);
                j = end;
            }
        }
    }

    /** {@return the curtain's top over this cell, or -1 if the cell isn't on the band's outer edge} */
    private static int curtainTop(PaleShape outline, double originX, double originZ, int i, int j, double inner, double outer) {
        double x = originX + (i + 0.5) * CELL;
        double z = originZ + (j + 0.5) * CELL;
        double depth = (outline.effectiveRadius(x, z) - inner) / (outer - inner);
        if (depth < CURTAIN_FROM || depth > CURTAIN_TO) {
            return -1;
        }
        return CURTAIN_TOP;
    }

    /**
     * The sea of clouds ({@code PaleCloudSea}) out to DH's horizon: two layers of cloud slabs over the Pale around and
     * between the islands, with holes over the islands. Near the camera the vanilla deck draws it instead.
     */
    private static void buildDeck(
            ClientConfig.Settings config, PaleShape outline, double minX, double minZ, double maxX, double maxZ,
            Map<Integer, List<DhApiRenderableBox>> boxesBySection, Map<Integer, List<Integer>> alphasBySection) {
        double originX = Math.floor((minX - DECK_MARGIN) / (DECK_CELL * DECK_SECTION)) * DECK_CELL * DECK_SECTION;
        double originZ = Math.floor((minZ - DECK_MARGIN) / (DECK_CELL * DECK_SECTION)) * DECK_CELL * DECK_SECTION;
        int sizeX = Mth.ceil((maxX + DECK_MARGIN - originX) / DECK_CELL);
        int sizeZ = Mth.ceil((maxZ + DECK_MARGIN - originZ) / DECK_CELL);
        double inner = config.innerRadius();
        double outer = config.outerRadius();
        for (int layer = 0; layer < DECK_LAYERS.length; layer++) {
            float[][] cover = new float[sizeX][sizeZ];
            for (int i = 0; i < sizeX; i++) {
                for (int j = 0; j < sizeZ; j++) {
                    double x = originX + (i + 0.5) * DECK_CELL;
                    double z = originZ + (j + 0.5) * DECK_CELL;
                    double mask = smoothstep(0, 1, (outline.effectiveRadius(x, z) - inner) / (outer - inner));
                    double shape = valueNoise(x / 700 + layer * 3.1, z / 700 - layer * 1.7) * 0.65 + valueNoise(x / 230, z / 230 + layer * 0.9) * 0.35;
                    cover[i][j] = mask > 0.1 && shape < 0.75 * mask ? (float) mask : 0f;
                }
            }
            // Merge into rectangles within each section's square of cells.
            for (int i = 0; i < sizeX; i++) {
                for (int j = 0; j < sizeZ; j++) {
                    if (cover[i][j] <= 0) {
                        continue;
                    }
                    int sectionI = i / DECK_SECTION;
                    int sectionJ = j / DECK_SECTION;
                    int endJ = j;
                    while (endJ < sizeZ && endJ / DECK_SECTION == sectionJ && cover[i][endJ] > 0) {
                        endJ++;
                    }
                    int endI = i + 1;
                    while (endI < sizeX && endI / DECK_SECTION == sectionI && allCovered(cover[endI], j, endJ)) {
                        for (int k = j; k < endJ; k++) {
                            cover[endI][k] = 0f;
                        }
                        endI++;
                    }
                    double mask = cover[i][j];
                    int alpha = (int) Math.round(255 * DECK_ALPHA * mask);
                    int section = DECK_SECTION_KEY + sectionI * 4096 + sectionJ;
                    double y = DECK_LAYERS[layer];
                    boxesBySection.computeIfAbsent(section, key -> new ArrayList<>()).add(new DhApiRenderableBox(
                            new DhApiVec3d(originX + i * DECK_CELL, y, originZ + j * DECK_CELL),
                            new DhApiVec3d(originX + endI * DECK_CELL, y + DECK_THICKNESS, originZ + endJ * DECK_CELL),
                            new Color(RED, GREEN, BLUE, alpha),
                            EDhApiBlockMaterial.UNKNOWN));
                    alphasBySection.computeIfAbsent(section, key -> new ArrayList<>()).add(alpha);
                    j = endJ - 1;
                }
            }
        }
    }

    private static boolean allCovered(float[] row, int from, int to) {
        for (int k = from; k < to; k++) {
            if (row[k] <= 0) {
                return false;
            }
        }
        return true;
    }

    void attach(IDhApiCustomRenderRegister register) {
        for (Section section : sections) {
            register.add(section.group);
        }
    }

    void detach(IDhApiCustomRenderRegister register) {
        for (Section section : sections) {
            register.remove(section.group.getId());
        }
    }

    int boxCount() {
        return sections.stream().mapToInt(section -> section.group.size()).sum();
    }

    private static boolean allSet(boolean[] row, int from, int to) {
        for (int k = from; k < to; k++) {
            if (!row[k]) {
                return false;
            }
        }
        return true;
    }

    /** Sections go round each island: its index, the sector around its centre, and how deep in the band. */
    private static int sectionOf(PaleShape outline, double x, double z, double depth) {
        PaleShape.Island island = outline.nearest(x, z);
        int index = outline.islands().indexOf(island);
        double angle = Math.atan2(z - island.centerZ(), x - island.centerX());
        int sector = Math.floorMod((int) Math.floor((angle + Math.PI) / (2 * Math.PI) * SECTORS), SECTORS);
        int band = Mth.clamp((int) Math.floor(depth * RADIAL_BANDS), 0, RADIAL_BANDS - 1);
        return (index * SECTORS + sector) * RADIAL_BANDS + band;
    }

    /** One section of the band: a DH box group that fades in with the distance from the camera. */
    private static final class Section {
        private final IDhApiRenderableBoxGroup group;
        private final int[] alphas;
        private final double minX;
        private final double maxX;
        private final double minZ;
        private final double maxZ;
        /** A piece of the sea of clouds rather than the wall: it hands over from the vanilla deck further out. */
        private final boolean deck;
        private int fadeStep = FADE_STEPS;

        Section(IDhApiRenderableBoxGroup group, int[] alphas, boolean deck) {
            this.group = group;
            this.alphas = alphas;
            this.deck = deck;
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (DhApiRenderableBox box : group) {
                minX = Math.min(minX, box.minPos.x);
                maxX = Math.max(maxX, box.maxPos.x);
                minZ = Math.min(minZ, box.minPos.z);
                maxZ = Math.max(maxZ, box.maxPos.z);
            }
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        void preRender(DhApiRenderParam param) {
            if (PaleShaderFog.steam()) {
                // With a shader pack the steam reaches the horizon itself (PaleSteam); these slabs would show as solid.
                group.setActive(false);
                return;
            }
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            // Horizontal distance from the camera to the nearest point of this section.
            double dx = Math.max(Math.max(minX - camera.x, camera.x - maxX), 0);
            double dz = Math.max(Math.max(minZ - camera.z, camera.z - maxZ), 0);
            double distance = Math.sqrt(dx * dx + dz * dz);
            double fade;
            if (deck) {
                double extent = Math.min(VANILLA_DECK_EXTENT, Minecraft.getInstance().gameRenderer.getDepthFar() * 0.9);
                fade = smoothstep(extent * 0.6, extent * 0.85, distance);
            } else {
                // Only where the vanilla wall is fading out (PaleWallRenderer#fadeEnd), so close up it's soft clouds.
                double wallEnd = PaleWallRenderer.fadeEnd();
                fade = smoothstep(wallEnd * 0.75, wallEnd, distance);
            }
            int step = (int) Math.round(fade * FADE_STEPS);
            group.setActive(step > 0);
            if (step == 0 || step == fadeStep) {
                return;
            }
            fadeStep = step;
            float scale = step / (float) FADE_STEPS;
            for (int i = 0; i < alphas.length; i++) {
                Color color = group.get(i).color;
                group.get(i).color = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(alphas[i] * scale));
            }
            group.triggerBoxChange();
        }
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Mth.clamp((value - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Smooth 2D value noise in 0..1, so the clouds bunch up instead of being scattered evenly. */
    private static double valueNoise(double x, double z) {
        int cellX = Mth.floor(x);
        int cellZ = Mth.floor(z);
        double fx = smoothstep(0, 1, x - cellX);
        double fz = smoothstep(0, 1, z - cellZ);
        double top = Mth.lerp(fx, hash(cellX, cellZ, 0), hash(cellX + 1, cellZ, 0));
        double bottom = Mth.lerp(fx, hash(cellX, cellZ + 1, 0), hash(cellX + 1, cellZ + 1, 0));
        return Mth.lerp(fz, top, bottom);
    }

    /** Fixed pseudo-random value in 0..1 for a cell, so the wall looks the same every time it's built. */
    private static double hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }
}
