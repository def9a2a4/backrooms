package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.NamespacedKey;

/**
 * Level 143 — "The Planned City". Level 142's walled cities, laid out the same way (see
 * {@link Level142ChunkGenerator} and {@link MaceCity}), but built from a set of buildings drawn by
 * rules rather than by hand: scripts/level143/build_pack.py generates every house, shop, chapel,
 * gatehouse, farm, mine and airship as a schematic in Mace's format, in three styles of its own
 * (Timber Town, Ochre Town, Ashen Town), with their own street names, signs and emblems. Under
 * each city run sewers, an underground railway and passages to the mine and the guard towers
 * ({@link CityUnderground}); balloons and airships crowd its sky.
 *
 * <p>The pack is downloaded by MeshStore as "level143", like Level 142's.
 */
public class Level143ChunkGenerator extends Level142ChunkGenerator {

    static final String PACK = "level143";

    /**
     * Bigger cities, and every size: most squares stay whole or split only once, and a city takes
     * anywhere from a fifth to three fifths of its region, so cities run from 5 to 29 chunks with
     * few gaps, a fifth of them small towns (about 35% of the land).
     */
    static final Layout LAYOUT = new Layout(55, 92, 1.0, 0.20, 0.42);

    public Level143ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey, PACK, MaceCity.Theme.LEVEL_143, LAYOUT);
    }
}
