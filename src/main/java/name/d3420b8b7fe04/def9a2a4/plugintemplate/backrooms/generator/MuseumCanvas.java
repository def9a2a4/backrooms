package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Level 1882's view of one chunk: writes in world coordinates, clipped to the chunk. With no
 * ChunkData it only records the labels (signs, lecterns, pots, paintings) whose text and contents
 * are filled in when the chunk first loads, since ChunkData can't hold block-entity data.
 */
final class MuseumCanvas {

    enum Kind { SIGN, LECTERN, POT, PAINTING }

    /** What a label describes; the catalogue writes its text from this and the label's own seed. */
    record Subject(String name, MuseumCatalogue.Topic topic, MuseumCatalogue.Rarity rarity, int gallery,
                   MuseumCatalogue.Theme theme) {}

    /** A block entity to fill in: at (x, y, z), facing {@code face}; paintings up to w×h blocks. */
    record Label(int x, int y, int z, Kind kind, BlockFace face, Subject subject, int w, int h) {}

    final @Nullable ChunkData data;
    final int x0, z0, minY, maxY;
    final List<Label> labels = new ArrayList<>();
    MuseumPalette palette;      // the district's, for signs and lanterns

    MuseumCanvas(@Nullable ChunkData data, int x0, int z0) {
        this.data = data;
        this.x0 = x0;
        this.z0 = z0;
        this.minY = data != null ? data.getMinHeight() : -64;
        this.maxY = data != null ? data.getMaxHeight() - 1 : 319;
    }

    boolean has(int x, int z) {
        return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
    }

    void set(int x, int y, int z, BlockData b) {
        if (data == null || y < minY || y > maxY || !has(x, z)) return;
        data.setBlock(x - x0, y, z - z0, b);
    }

    void set(int x, int y, int z, Material m) {
        set(x, y, z, m.createBlockData());
    }

    @Nullable Material type(int x, int y, int z) {
        if (data == null || y < minY || y > maxY || !has(x, z)) return null;
        return data.getType(x - x0, y, z - z0);
    }

    /** Air, or an invisible light block. */
    boolean air(int x, int y, int z) {
        Material m = type(x, y, z);
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.LIGHT;
    }

    private void label(int x, int y, int z, Kind kind, BlockFace face, Subject s, int w, int h) {
        if (has(x, z)) labels.add(new Label(x, y, z, kind, face, s, w, h));
    }

    /** A wall sign hung on the block behind it, facing {@code face}. */
    void sign(int x, int y, int z, BlockFace face, Subject s) {
        Directional d = (Directional) palette.sign.createBlockData();
        d.setFacing(face);
        set(x, y, z, d);
        label(x, y, z, Kind.SIGN, face, s, 0, 0);
    }

    /** A lectern whose reader stands on the {@code face} side; it gets a book. */
    void lectern(int x, int y, int z, BlockFace face, Subject s) {
        Directional d = (Directional) Material.LECTERN.createBlockData();
        d.setFacing(face);
        set(x, y, z, d);
        label(x, y, z, Kind.LECTERN, face, s, 0, 0);
    }

    /** A decorated pot; it gets sherds. */
    void pot(int x, int y, int z, BlockFace face, Subject s) {
        Directional d = (Directional) Material.DECORATED_POT.createBlockData();
        d.setFacing(face);
        set(x, y, z, d);
        label(x, y, z, Kind.POT, face, s, 0, 0);
    }

    /** A painting up to w×h blocks hung on the wall behind (x, y, z), centered there. */
    void painting(int x, int y, int z, BlockFace face, Subject s, int w, int h) {
        label(x, y, z, Kind.PAINTING, face, s, w, h);
    }
}
