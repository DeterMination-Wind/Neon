package betterterraingen.v2.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Deterministic room-and-corridor geometry used by the structured ruin mode. */
final class StructuredRuinLayout {
    private StructuredRuinLayout() {
    }

    static Spec spec(int mapWidth, int mapHeight, RuinGenerateFilter.StructurePreset preset) {
        int shortSide = Math.min(mapWidth, mapHeight);
        RuinGenerateFilter.StructurePreset safePreset = preset == null
            ? RuinGenerateFilter.StructurePreset.large : preset;

        float ratio;
        int minimumSpan;
        int minimumRoomArea;
        int maximumRoomArea;
        int minimumRooms;
        int maximumRooms;
        int corridorWidth;
        int mainCorridorWidth;
        float wallDamage;
        float floorDamage;
        switch (safePreset) {
            case small -> {
                ratio = 0.22f;
                minimumSpan = 22;
                minimumRoomArea = 32;
                maximumRoomArea = 80;
                minimumRooms = 4;
                maximumRooms = 7;
                corridorWidth = 2;
                mainCorridorWidth = 4;
                wallDamage = 0.20f;
                floorDamage = 0.12f;
            }
            case medium -> {
                ratio = 0.34f;
                minimumSpan = 34;
                minimumRoomArea = 64;
                maximumRoomArea = 180;
                minimumRooms = 7;
                maximumRooms = 12;
                corridorWidth = 3;
                mainCorridorWidth = 5;
                wallDamage = 0.24f;
                floorDamage = 0.16f;
            }
            case large -> {
                ratio = 0.80f;
                minimumSpan = 80;
                minimumRoomArea = 96;
                maximumRoomArea = 320;
                minimumRooms = 12;
                maximumRooms = 18;
                corridorWidth = 3;
                mainCorridorWidth = 7;
                wallDamage = 0.28f;
                floorDamage = 0.20f;
            }
            default -> throw new IllegalStateException("Unhandled structure preset: " + safePreset);
        }

        int targetSpan = Math.round(shortSide * ratio);
        int span = Math.min(shortSide, Math.max(minimumSpan, targetSpan));
        span = Math.max(8, span);
        float roomScale = Math.max(1f, span / 96f);
        int scaledMinimumArea = Math.max(16, Math.round(minimumRoomArea * roomScale));
        int scaledMaximumArea = Math.max(scaledMinimumArea,
            Math.round(maximumRoomArea * roomScale));
        return new Spec(span, scaledMinimumArea, scaledMaximumArea, minimumRooms, maximumRooms,
            corridorWidth, mainCorridorWidth, wallDamage, floorDamage);
    }

    static Bounds bounds(int mapWidth, int mapHeight, int centerX, int centerY,
                         RuinGenerateFilter.StructurePreset preset) {
        Spec spec = spec(mapWidth, mapHeight, preset);
        int originX = centerX - spec.span / 2;
        int originY = centerY - spec.span / 2;
        return new Bounds(originX, originY, originX + spec.span - 1, originY + spec.span - 1);
    }

    static Layout generate(int mapWidth, int mapHeight, int centerX, int centerY, int seed,
                           RuinGenerateFilter.StructurePreset preset) {
        Spec spec = spec(mapWidth, mapHeight, preset);
        RuinGenerateFilter.StructurePreset layoutPreset = safePreset(preset);
        int span = spec.span;
        int originX = centerX - span / 2;
        int originY = centerY - span / 2;
        Random random = new Random(mixSeed(seed, centerX, centerY, preset));

        boolean[] floor = new boolean[span * span];
        boolean[] criticalFloor = new boolean[span * span];
        boolean[] mainFloor = new boolean[span * span];
        List<Room> rooms = new ArrayList<>();
        Room hall = createRoom(random, spec, span, span / 2, span / 2);
        rooms.add(hall);
        carveRoom(floor, criticalFloor, span, hall, true);

        int roomTarget = spec.minimumRooms + random.nextInt(spec.maximumRooms - spec.minimumRooms + 1);
        int mainRoomTarget = Math.min(roomTarget - 1,
            layoutPreset == RuinGenerateFilter.StructurePreset.large ? 4 : 3);
        List<Room> mainAnchors = new ArrayList<>();
        mainAnchors.add(hall);

        if (layoutPreset == RuinGenerateFilter.StructurePreset.large) {
            // Large ruins need a real footprint, not a dense cluster around the hall.
            // Edge rooms are deliberately jittered and connected with the wide main
            // route so the outer 80% envelope remains readable without a rigid box.
            for (int side = 0; side < 4; side++) {
                Room perimeter = createPerimeterRoom(random, spec, span, side, rooms);
                if (perimeter == null) continue;
                rooms.add(perimeter);
                carveRoom(floor, criticalFloor, span, perimeter, true);
                carveRoomConnection(floor, criticalFloor, mainFloor, span, hall, perimeter,
                    spec.mainCorridorWidth, random, true);
                mainAnchors.add(perimeter);
            }
        }

        // Grow rooms from existing room walls. The first few attachments form the
        // boulevard hierarchy; later attachments use narrower secondary corridors.
        for (int index = rooms.size(); index < roomTarget; index++) {
            boolean mainRoute = index <= mainRoomTarget;
            Room parent = chooseParent(rooms, mainAnchors, random, mainRoute);
            Room candidate = null;
            for (int attempt = 0; attempt < 48 && candidate == null; attempt++) {
                int side = random.nextInt(4);
                Room next = createAdjacentRoom(random, spec, span, parent, side);
                if (next != null && !overlaps(next, rooms, 1)) candidate = next;
            }

            if (candidate == null) {
                for (int attempt = 0; attempt < 160 && candidate == null; attempt++) {
                    int x = 2 + random.nextInt(Math.max(1, span - 4));
                    int y = 2 + random.nextInt(Math.max(1, span - 4));
                    Room next = createRoom(random, spec, span, x, y);
                    if (next != null && !overlaps(next, rooms, 1)) candidate = next;
                }
            }

            if (candidate == null) continue;
            rooms.add(candidate);
            carveRoom(floor, criticalFloor, span, candidate, false);
            int width = mainRoute ? Math.max(4, spec.mainCorridorWidth - (index > 1 ? 1 : 0))
                : spec.corridorWidth;
            carveRoomConnection(floor, criticalFloor, mainFloor, span, parent, candidate,
                width, random, mainRoute);
            if (mainRoute) mainAnchors.add(candidate);
        }

        // A few redundant links create loops without turning every room into a hub.
        int extraConnections = Math.max(1, rooms.size() / 5);
        for (int connection = 0; connection < extraConnections; connection++) {
            Room first = rooms.get(1 + random.nextInt(Math.max(1, rooms.size() - 1)));
            Room second = rooms.get(random.nextInt(rooms.size()));
            if (first == second) continue;
            carveRoomConnection(floor, criticalFloor, mainFloor, span, first, second,
                spec.corridorWidth, random, false);
        }

        if (!roomsConnected(floor, span, rooms)) {
            for (int index = 1; index < rooms.size(); index++) {
                Room room = rooms.get(index);
                carveRoomConnection(floor, criticalFloor, mainFloor, span, room, hall,
                    spec.corridorWidth, random, false);
            }
        }

        collapseNonCriticalFloor(floor, criticalFloor, mainFloor, span, rooms, random, layoutPreset);
        boolean[] panelFloor = buildPanelFloors(floor, criticalFloor, mainFloor, span, rooms, random);
        boolean[] wall = buildWalls(floor, span);
        boolean[] mainWall = buildMainWalls(floor, mainFloor, wall, span);
        boolean[] debrisWall = buildDebrisWalls(floor, wall, mainFloor, span, random);
        boolean[] criticalWall = buildCriticalWalls(floor, criticalFloor, wall, span);
        return transform(new Layout(originX, originY, span, floor, wall, mainFloor, mainWall,
            panelFloor, debrisWall, criticalFloor, criticalWall), random);
    }

    private static RuinGenerateFilter.StructurePreset safePreset(
        RuinGenerateFilter.StructurePreset preset) {
        return preset == null ? RuinGenerateFilter.StructurePreset.large : preset;
    }

    private static Room createRoom(Random random, Spec spec, int span, int centerX, int centerY) {
        int[] size = roomSize(random, spec, span);
        return roomAt(random, span, centerX, centerY, size[0], size[1]);
    }

    private static int[] roomSize(Random random, Spec spec, int span) {
        int maximumDimension = Math.max(4, Math.min(span - 4,
            Math.max(14, Math.min(48, Math.round(span * 0.18f)))));
        int width = 0;
        int height = 0;
        for (int attempt = 0; attempt < 40; attempt++) {
            int candidateWidth = 4 + random.nextInt(Math.max(1, maximumDimension - 3));
            int candidateHeight = 4 + random.nextInt(Math.max(1, maximumDimension - 3));
            int area = candidateWidth * candidateHeight;
            if (area >= spec.minimumRoomArea && area <= spec.maximumRoomArea
                && candidateWidth <= candidateHeight * 2 && candidateHeight <= candidateWidth * 2) {
                width = candidateWidth;
                height = candidateHeight;
                break;
            }
        }

        if (width == 0) {
            int target = (spec.minimumRoomArea + spec.maximumRoomArea) / 2;
            width = Math.max(4, Math.min(maximumDimension, (int)Math.sqrt(target)));
            height = Math.max(4, Math.min(maximumDimension, (target + width - 1) / width));
        }
        return new int[]{width, height};
    }

    private static Room roomAt(Random random, int span, int centerX, int centerY,
                               int width, int height) {
        if (width < 4 || height < 4 || width > span - 2 || height > span - 2) return null;
        int left = clampRange(centerX - width / 2, 1, span - width - 1);
        int top = clampRange(centerY - height / 2, 1, span - height - 1);
        return new Room(left, top, left + width - 1, top + height - 1,
            random.nextInt(7));
    }

    private static Room createPerimeterRoom(Random random, Spec spec, int span, int side,
                                             List<Room> rooms) {
        for (int attempt = 0; attempt < 64; attempt++) {
            int[] size = roomSize(random, spec, span);
            int width = size[0];
            int height = size[1];
            int centerX;
            int centerY;
            int jitter = Math.max(3, span / 5);
            switch (side) {
                case 0 -> {
                    centerX = span / 2 + randomOffset(random, jitter);
                    centerY = 1 + height / 2;
                }
                case 1 -> {
                    centerX = span - 2 - width / 2;
                    centerY = span / 2 + randomOffset(random, jitter);
                }
                case 2 -> {
                    centerX = span / 2 + randomOffset(random, jitter);
                    centerY = span - 2 - height / 2;
                }
                case 3 -> {
                    centerX = 1 + width / 2;
                    centerY = span / 2 + randomOffset(random, jitter);
                }
                default -> throw new IllegalStateException("Unhandled perimeter side: " + side);
            }

            Room candidate = roomAt(random, span, centerX, centerY, width, height);
            if (candidate != null && !overlaps(candidate, rooms, 1)) return candidate;
        }
        return null;
    }

    private static Room createAdjacentRoom(Random random, Spec spec, int span, Room parent,
                                           int side) {
        int[] size = roomSize(random, spec, span);
        int width = size[0];
        int height = size[1];
        int gap = 2 + random.nextInt(3);
        int left;
        int top;
        int offset;
        switch (side) {
            case 0 -> {
                top = parent.top - gap - height;
                offset = randomOffset(random, Math.max(2, parent.width() / 3));
                left = parent.centerX() - width / 2 + offset;
            }
            case 1 -> {
                left = parent.right + gap;
                offset = randomOffset(random, Math.max(2, parent.height() / 3));
                top = parent.centerY() - height / 2 + offset;
            }
            case 2 -> {
                top = parent.bottom + gap;
                offset = randomOffset(random, Math.max(2, parent.width() / 3));
                left = parent.centerX() - width / 2 + offset;
            }
            case 3 -> {
                left = parent.left - gap - width;
                offset = randomOffset(random, Math.max(2, parent.height() / 3));
                top = parent.centerY() - height / 2 + offset;
            }
            default -> throw new IllegalStateException("Unhandled room side: " + side);
        }

        if (left < 1 || top < 1 || left + width >= span - 1 || top + height >= span - 1) {
            return null;
        }
        return new Room(left, top, left + width - 1, top + height - 1,
            random.nextInt(7));
    }

    private static Room chooseParent(List<Room> rooms, List<Room> mainAnchors,
                                     Random random, boolean mainRoute) {
        if (mainRoute) {
            if (mainAnchors.size() > 1 && random.nextFloat() < 0.65f) {
                return mainAnchors.get(mainAnchors.size() - 1);
            }
            return mainAnchors.get(random.nextInt(mainAnchors.size()));
        }
        return rooms.get(random.nextInt(rooms.size()));
    }

    private static void carveRoomConnection(boolean[] floor, boolean[] critical,
                                             boolean[] main, int span, Room first, Room second,
                                             int width, Random random, boolean mainRoute) {
        int side = connectionSide(first, second);
        int[] start = doorway(first, side, random);
        int[] end = doorway(second, (side + 2) % 4, random);
        carveBoundaryConnection(floor, critical, main, span, start[0], start[1], end[0], end[1],
            width, random, mainRoute);
    }

    private static int connectionSide(Room first, Room second) {
        if (second.bottom < first.top) return 0;
        if (second.left > first.right) return 1;
        if (second.top > first.bottom) return 2;
        if (second.right < first.left) return 3;

        int dx = second.centerX() - first.centerX();
        int dy = second.centerY() - first.centerY();
        return Math.abs(dx) >= Math.abs(dy) ? dx >= 0 ? 1 : 3 : dy >= 0 ? 2 : 0;
    }

    private static int[] doorway(Room room, int side, Random random) {
        boolean horizontal = side == 0 || side == 2;
        int min = horizontal ? room.left + 1 : room.top + 1;
        int max = horizontal ? room.right - 1 : room.bottom - 1;
        int preferred = min + random.nextInt(Math.max(1, max - min + 1));
        for (int offset = 0; offset <= Math.max(room.width(), room.height()); offset++) {
            int[] candidates = {preferred - offset, preferred + offset};
            for (int coordinate : candidates) {
                if (coordinate < min || coordinate > max) continue;
                int x = horizontal ? coordinate : side == 1 ? room.right : room.left;
                int y = horizontal ? side == 2 ? room.bottom : room.top : coordinate;
                if (room.contains(x, y)) return new int[]{x, y};
            }
        }

        int x = horizontal ? room.centerX() : side == 1 ? room.right : room.left;
        int y = horizontal ? side == 2 ? room.bottom : room.top : room.centerY();
        return new int[]{x, y};
    }

    private static void carveBoundaryConnection(boolean[] floor, boolean[] critical,
                                                boolean[] main, int span, int fromX, int fromY,
                                                int toX, int toY, int width, Random random,
                                                boolean mainRoute) {
        int dx = toX - fromX;
        int dy = toY - fromY;
        float split = 0.35f + random.nextFloat() * 0.30f;
        if (Math.abs(dx) >= Math.abs(dy)) {
            int midX = Math.round(fromX + dx * split);
            carveHorizontal(floor, critical, main, span, fromX, midX, fromY, width, mainRoute);
            carveVertical(floor, critical, main, span, fromY, toY, midX, width, mainRoute);
            carveHorizontal(floor, critical, main, span, midX, toX, toY, width, mainRoute);
        } else {
            int midY = Math.round(fromY + dy * split);
            carveVertical(floor, critical, main, span, fromY, midY, fromX, width, mainRoute);
            carveHorizontal(floor, critical, main, span, fromX, toX, midY, width, mainRoute);
            carveVertical(floor, critical, main, span, midY, toY, toX, width, mainRoute);
        }
    }

    private static int clampRange(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean overlaps(Room candidate, List<Room> rooms, int padding) {
        for (Room room : rooms) {
            if (candidate.right + padding < room.left || room.right + padding < candidate.left
                || candidate.bottom + padding < room.top || room.bottom + padding < candidate.top) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static void carveRoom(boolean[] floor, boolean[] critical, int span, Room room,
                                  boolean isCritical) {
        for (int y = room.top; y <= room.bottom; y++) {
            for (int x = room.left; x <= room.right; x++) {
                if (!room.contains(x, y)) continue;
                int index = x + y * span;
                floor[index] = true;
                if (isCritical || room.isCore(x, y)) critical[index] = true;
            }
        }
    }

    private static int randomOffset(Random random, int magnitude) {
        return Math.round((random.nextFloat() * 2f - 1f) * magnitude);
    }

    private static void carveHorizontal(boolean[] floor, boolean[] critical, boolean[] main,
                                        int span, int fromX, int toX, int y, int width,
                                        boolean mainRoute) {
        int left = Math.min(fromX, toX);
        int right = Math.max(fromX, toX);
        int half = width / 2;
        for (int x = left; x <= right; x++) {
            for (int offset = -half; offset < width - half; offset++) {
                mark(floor, critical, main, span, x, y + offset, mainRoute);
            }
        }
    }

    private static void collapseNonCriticalFloor(boolean[] floor, boolean[] critical,
                                                 boolean[] main, int span, List<Room> rooms,
                                                 Random random,
                                                 RuinGenerateFilter.StructurePreset preset) {
        float chance;
        int attempts;
        switch (preset) {
            case small -> {
                chance = 0.10f;
                attempts = span * 2;
            }
            case medium -> {
                chance = 0.15f;
                attempts = span * 3;
            }
            case large -> {
                chance = 0.20f;
                attempts = span * 5;
            }
            default -> throw new IllegalStateException("Unhandled structure preset: " + preset);
        }

        for (int attempt = 0; attempt < attempts; attempt++) {
            if (random.nextFloat() > chance) continue;
            int x = 2 + random.nextInt(Math.max(1, span - 4));
            int y = 2 + random.nextInt(Math.max(1, span - 4));
            int width = 1 + random.nextInt(preset == RuinGenerateFilter.StructurePreset.small ? 2 : 3);
            int height = 1 + random.nextInt(3);
            if (!canCollapse(floor, critical, main, span, x, y, width, height)) continue;

            for (int localY = y; localY < y + height && localY < span - 1; localY++) {
                for (int localX = x; localX < x + width && localX < span - 1; localX++) {
                    floor[localX + localY * span] = false;
                    main[localX + localY * span] = false;
                }
            }
        }

        // Keep all remaining floor islands attached to the hall after the collapse pass.
        retainConnectedFloor(floor, critical, main, span, rooms.get(0).centerX(), rooms.get(0).centerY());
    }

    private static boolean canCollapse(boolean[] floor, boolean[] critical, boolean[] main,
                                       int span, int x, int y, int width, int height) {
        boolean touchesVoid = false;
        for (int localY = y; localY < y + height && localY < span - 1; localY++) {
            for (int localX = x; localX < x + width && localX < span - 1; localX++) {
                int index = localX + localY * span;
                if (!floor[index] || critical[index] || main[index]) return false;
                if (localX <= 1 || localY <= 1 || localX >= span - 2 || localY >= span - 2
                    || !floor[index - 1] || !floor[index + 1]
                    || !floor[index - span] || !floor[index + span]) {
                    touchesVoid = true;
                }
                if (adjacentToMain(main, span, localX, localY)) return false;
            }
        }
        return touchesVoid;
    }

    private static void retainConnectedFloor(boolean[] floor, boolean[] critical, boolean[] main,
                                              int span, int startX, int startY) {
        int start = startX + startY * span;
        if (!floor[start]) {
            for (int index = 0; index < floor.length; index++) {
                if (floor[index] && critical[index]) {
                    start = index;
                    break;
                }
            }
        }

        boolean[] visited = new boolean[floor.length];
        int[] queue = new int[floor.length];
        int head = 0;
        int tail = 0;
        if (floor[start]) {
            queue[tail++] = start;
            visited[start] = true;
        }

        while (head < tail) {
            int current = queue[head++];
            int x = current % span;
            int y = current / span;
            if (x > 0) tail = enqueueFloor(floor, visited, queue, tail, current - 1);
            if (x + 1 < span) tail = enqueueFloor(floor, visited, queue, tail, current + 1);
            if (y > 0) tail = enqueueFloor(floor, visited, queue, tail, current - span);
            if (y + 1 < span) tail = enqueueFloor(floor, visited, queue, tail, current + span);
        }

        for (int index = 0; index < floor.length; index++) {
            if (floor[index] && !visited[index]) {
                floor[index] = false;
                critical[index] = false;
                main[index] = false;
            }
        }
    }

    private static boolean[] buildPanelFloors(boolean[] floor, boolean[] critical,
                                              boolean[] main, int span, List<Room> rooms,
                                              Random random) {
        boolean[] panel = new boolean[floor.length];
        for (int index = 1; index < rooms.size(); index++) {
            Room room = rooms.get(index);
            int patchCount = 1 + random.nextInt(2);
            for (int patch = 0; patch < patchCount; patch++) {
                int patchWidth = Math.max(2, Math.min(room.width(), 3 + random.nextInt(5)));
                int patchHeight = Math.max(2, Math.min(room.height(), 2 + random.nextInt(4)));
                int xRange = Math.max(1, room.width() - patchWidth + 1);
                int yRange = Math.max(1, room.height() - patchHeight + 1);
                int left = room.left + random.nextInt(xRange);
                int top = room.top + random.nextInt(yRange);
                for (int y = top; y < top + patchHeight; y++) {
                    for (int x = left; x < left + patchWidth; x++) {
                        int cell = x + y * span;
                        if (floor[cell] && !main[cell] && (!critical[cell] || random.nextBoolean())) {
                            panel[cell] = true;
                        }
                    }
                }
            }
        }
        return panel;
    }

    private static boolean[] buildDebrisWalls(boolean[] floor, boolean[] wall, boolean[] main,
                                               int span, Random random) {
        boolean[] debris = new boolean[floor.length];
        for (int y = 2; y < span - 2; y++) {
            for (int x = 2; x < span - 2; x++) {
                int index = x + y * span;
                if (floor[index] || wall[index] || adjacentToMain(main, span, x, y)) continue;
                boolean nearWall = wall[index - 1] || wall[index + 1]
                    || wall[index - span] || wall[index + span];
                if (nearWall && random.nextFloat() < 0.24f) debris[index] = true;
            }
        }
        return debris;
    }

    private static boolean adjacentToMain(boolean[] main, int span, int x, int y) {
        return (x > 0 && main[x - 1 + y * span])
            || (x + 1 < span && main[x + 1 + y * span])
            || (y > 0 && main[x + (y - 1) * span])
            || (y + 1 < span && main[x + (y + 1) * span]);
    }

    private static void carveVertical(boolean[] floor, boolean[] critical, boolean[] main,
                                      int span, int fromY, int toY, int x, int width,
                                      boolean mainRoute) {
        int top = Math.min(fromY, toY);
        int bottom = Math.max(fromY, toY);
        int half = width / 2;
        for (int y = top; y <= bottom; y++) {
            for (int offset = -half; offset < width - half; offset++) {
                mark(floor, critical, main, span, x + offset, y, mainRoute);
            }
        }
    }

    private static void mark(boolean[] floor, boolean[] critical, boolean[] main, int span,
                             int x, int y, boolean mainRoute) {
        if (x < 1 || y < 1 || x >= span - 1 || y >= span - 1) return;
        int index = x + y * span;
        floor[index] = true;
        critical[index] = true;
        if (mainRoute) main[index] = true;
    }

    private static boolean roomsConnected(boolean[] floor, int span, List<Room> rooms) {
        if (rooms.isEmpty()) return false;
        boolean[] visited = new boolean[floor.length];
        int[] queue = new int[floor.length];
        int head = 0;
        int tail = 0;
        Room hall = rooms.get(0);
        int start = hall.centerX() + hall.centerY() * span;
        if (!floor[start]) return false;
        queue[tail++] = start;
        visited[start] = true;

        while (head < tail) {
            int current = queue[head++];
            int x = current % span;
            int y = current / span;
            if (x > 0) tail = enqueueFloor(floor, visited, queue, tail, current - 1);
            if (x + 1 < span) tail = enqueueFloor(floor, visited, queue, tail, current + 1);
            if (y > 0) tail = enqueueFloor(floor, visited, queue, tail, current - span);
            if (y + 1 < span) tail = enqueueFloor(floor, visited, queue, tail, current + span);
        }

        for (Room room : rooms) {
            if (!visited[room.centerX() + room.centerY() * span]) return false;
        }
        return true;
    }

    private static int enqueueFloor(boolean[] floor, boolean[] visited, int[] queue,
                                    int queueIndex, int index) {
        if (!floor[index] || visited[index]) return queueIndex;
        visited[index] = true;
        queue[queueIndex] = index;
        return queueIndex + 1;
    }

    private static boolean[] buildWalls(boolean[] floor, int span) {
        boolean[] wall = new boolean[floor.length];
        for (int y = 1; y < span - 1; y++) {
            for (int x = 1; x < span - 1; x++) {
                int index = x + y * span;
                if (floor[index]) continue;
                wall[index] = floor[index - 1] || floor[index + 1]
                    || floor[index - span] || floor[index + span];
            }
        }
        return wall;
    }

    private static boolean[] buildMainWalls(boolean[] floor, boolean[] mainFloor,
                                            boolean[] wall, int span) {
        boolean[] mainWall = new boolean[wall.length];
        for (int y = 1; y < span - 1; y++) {
            for (int x = 1; x < span - 1; x++) {
                int index = x + y * span;
                if (!wall[index]) continue;
                mainWall[index] = mainFloor[index - 1] || mainFloor[index + 1]
                    || mainFloor[index - span] || mainFloor[index + span];
            }
        }
        return mainWall;
    }

    private static boolean[] buildCriticalWalls(boolean[] floor, boolean[] criticalFloor,
                                                boolean[] wall, int span) {
        boolean[] critical = new boolean[wall.length];
        for (int y = 1; y < span - 1; y++) {
            for (int x = 1; x < span - 1; x++) {
                int index = x + y * span;
                if (!wall[index]) continue;
                critical[index] = criticalFloor[index - 1] || criticalFloor[index + 1]
                    || criticalFloor[index - span] || criticalFloor[index + span];
            }
        }
        return critical;
    }

    private static Layout transform(Layout source, Random random) {
        int span = source.span;
        boolean[] floor = new boolean[source.floor.length];
        boolean[] wall = new boolean[source.wall.length];
        boolean[] mainFloor = new boolean[source.mainFloor.length];
        boolean[] mainWall = new boolean[source.mainWall.length];
        boolean[] panelFloor = new boolean[source.panelFloor.length];
        boolean[] debrisWall = new boolean[source.debrisWall.length];
        boolean[] criticalFloor = new boolean[source.criticalFloor.length];
        boolean[] criticalWall = new boolean[source.criticalWall.length];
        int rotation = random.nextInt(4);
        boolean mirror = random.nextBoolean();

        for (int y = 0; y < span; y++) {
            for (int x = 0; x < span; x++) {
                int sourceIndex = x + y * span;
                int transformedX = x;
                int transformedY = y;
                if (mirror) transformedX = span - 1 - transformedX;
                for (int turn = 0; turn < rotation; turn++) {
                    int nextX = span - 1 - transformedY;
                    transformedY = transformedX;
                    transformedX = nextX;
                }
                int targetIndex = transformedX + transformedY * span;
                floor[targetIndex] = source.floor[sourceIndex];
                wall[targetIndex] = source.wall[sourceIndex];
                mainFloor[targetIndex] = source.mainFloor[sourceIndex];
                mainWall[targetIndex] = source.mainWall[sourceIndex];
                panelFloor[targetIndex] = source.panelFloor[sourceIndex];
                debrisWall[targetIndex] = source.debrisWall[sourceIndex];
                criticalFloor[targetIndex] = source.criticalFloor[sourceIndex];
                criticalWall[targetIndex] = source.criticalWall[sourceIndex];
            }
        }
        return new Layout(source.originX, source.originY, span, floor, wall, mainFloor, mainWall,
            panelFloor, debrisWall, criticalFloor, criticalWall);
    }

    private static long mixSeed(int seed, int centerX, int centerY,
                                RuinGenerateFilter.StructurePreset preset) {
        long value = seed * 0x9e3779b97f4a7c15L;
        value ^= centerX * 0xc2b2ae3d27d4eb4fL;
        value ^= centerY * 0x165667b19e3779f9L;
        value ^= (preset == null ? 2 : preset.ordinal() + 1) * 0x94d049bb133111ebL;
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    static final class Spec {
        final int span;
        final int minimumRoomArea;
        final int maximumRoomArea;
        final int minimumRooms;
        final int maximumRooms;
        final int corridorWidth;
        final int mainCorridorWidth;
        final float wallDamage;
        final float floorDamage;

        Spec(int span, int minimumRoomArea, int maximumRoomArea, int minimumRooms,
             int maximumRooms, int corridorWidth, int mainCorridorWidth,
             float wallDamage, float floorDamage) {
            this.span = span;
            this.minimumRoomArea = minimumRoomArea;
            this.maximumRoomArea = maximumRoomArea;
            this.minimumRooms = minimumRooms;
            this.maximumRooms = maximumRooms;
            this.corridorWidth = corridorWidth;
            this.mainCorridorWidth = mainCorridorWidth;
            this.wallDamage = wallDamage;
            this.floorDamage = floorDamage;
        }
    }

    static final class Bounds {
        final int minX;
        final int minY;
        final int maxX;
        final int maxY;

        Bounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        boolean contains(int width, int height) {
            return minX >= 0 && minY >= 0 && maxX < width && maxY < height;
        }

        boolean intersects(Bounds other) {
            return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY;
        }
    }

    static final class Layout {
        final int originX;
        final int originY;
        final int span;
        final boolean[] floor;
        final boolean[] wall;
        final boolean[] mainFloor;
        final boolean[] mainWall;
        final boolean[] panelFloor;
        final boolean[] debrisWall;
        final boolean[] criticalFloor;
        final boolean[] criticalWall;

        Layout(int originX, int originY, int span, boolean[] floor, boolean[] wall,
               boolean[] mainFloor, boolean[] mainWall, boolean[] panelFloor,
               boolean[] debrisWall, boolean[] criticalFloor, boolean[] criticalWall) {
            this.originX = originX;
            this.originY = originY;
            this.span = span;
            this.floor = floor;
            this.wall = wall;
            this.mainFloor = mainFloor;
            this.mainWall = mainWall;
            this.panelFloor = panelFloor;
            this.debrisWall = debrisWall;
            this.criticalFloor = criticalFloor;
            this.criticalWall = criticalWall;
        }
    }

    private static final class Room {
        final int left;
        final int top;
        final int right;
        final int bottom;
        final int shape;

        Room(int left, int top, int right, int bottom, int shape) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.shape = shape;
        }

        int centerX() {
            return (left + right) / 2;
        }

        int centerY() {
            return (top + bottom) / 2;
        }

        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }

        boolean isCore(int x, int y) {
            return Math.abs(x - centerX()) <= 1 && Math.abs(y - centerY()) <= 1;
        }

        boolean contains(int x, int y) {
            int localX = x - left;
            int localY = y - top;
            int width = width();
            int height = height();
            if (shape == 0 || width < 6 || height < 6) return true;

            boolean topLeft = localX < 2 && localY < 2;
            boolean topRight = localX >= width - 2 && localY < 2;
            boolean bottomLeft = localX < 2 && localY >= height - 2;
            boolean bottomRight = localX >= width - 2 && localY >= height - 2;
            if (shape == 1) return !(topLeft || bottomRight);
            if (shape == 2) return !(topRight || bottomLeft);
            if (shape == 3) return !(topLeft || topRight);
            if (shape == 4) return !(bottomLeft || bottomRight);

            if (shape == 5) {
                boolean leftNotch = localX == 0 && localY >= height / 3 && localY <= height * 2 / 3;
                boolean rightNotch = localX == width - 1
                    && localY >= height / 4 && localY <= height * 3 / 4;
                return !leftNotch && !rightNotch;
            }

            boolean topNotch = localY == 0 && localX >= width / 3 && localX <= width * 2 / 3;
            boolean bottomNotch = localY == height - 1
                && localX >= width / 4 && localX <= width * 3 / 4;
            return !topNotch && !bottomNotch;
        }
    }
}
