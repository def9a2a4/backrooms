package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.material.MaterialData;

/**
 * ChunkData view shifted vertically by {@code dy}: writing at y lands at y + dy.
 * Lets generators with a hard-coded ground height (Level 101's structures)
 * build on a floor at any other height.
 */
@SuppressWarnings({"deprecation", "removal"})
final class OffsetChunkData implements ChunkData {

    private final ChunkData delegate;
    private final int dy;

    OffsetChunkData(ChunkData delegate, int dy) {
        this.delegate = delegate;
        this.dy = dy;
    }

    @Override public int getMinHeight() { return delegate.getMinHeight() - dy; }
    @Override public int getMaxHeight() { return delegate.getMaxHeight() - dy; }
    @Override public Biome getBiome(int x, int y, int z) { return delegate.getBiome(x, y + dy, z); }

    @Override public void setBlock(int x, int y, int z, Material material) { delegate.setBlock(x, y + dy, z, material); }
    @Override public void setBlock(int x, int y, int z, MaterialData material) { delegate.setBlock(x, y + dy, z, material); }
    @Override public void setBlock(int x, int y, int z, BlockData blockData) { delegate.setBlock(x, y + dy, z, blockData); }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material) {
        delegate.setRegion(xMin, yMin + dy, zMin, xMax, yMax + dy, zMax, material);
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, MaterialData material) {
        delegate.setRegion(xMin, yMin + dy, zMin, xMax, yMax + dy, zMax, material);
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData) {
        delegate.setRegion(xMin, yMin + dy, zMin, xMax, yMax + dy, zMax, blockData);
    }

    @Override public Material getType(int x, int y, int z) { return delegate.getType(x, y + dy, z); }
    @Override public MaterialData getTypeAndData(int x, int y, int z) { return delegate.getTypeAndData(x, y + dy, z); }
    @Override public BlockData getBlockData(int x, int y, int z) { return delegate.getBlockData(x, y + dy, z); }
    @Override public byte getData(int x, int y, int z) { return delegate.getData(x, y + dy, z); }
    @Override public int getHeight(HeightMap heightMap, int x, int z) { return delegate.getHeight(heightMap, x, z) - dy; }
}
