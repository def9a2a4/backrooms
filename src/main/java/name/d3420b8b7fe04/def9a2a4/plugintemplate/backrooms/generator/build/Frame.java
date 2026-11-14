package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Blocks.Dir;

/**
 * A turned scope, as the grammar's {@code turn}: cell (u, v) is u along {@code along} and v
 * along {@code across} from (ox, oz). Lets one rule lay out a row of rooms, a wing or a piece of
 * furniture the same way whichever side of a building it is on.
 */
public record Frame(int ox, int oz, Dir along, Dir across) {

    public int x(int u, int v) {
        return ox + along.dx * u + across.dx * v;
    }

    public int z(int u, int v) {
        return oz + along.dz * u + across.dz * v;
    }

    public void set(Grid g, int u, int y, int v, String state) {
        g.set(x(u, v), y, z(u, v), state);
    }

    public boolean put(Grid g, int u, int y, int v, String state) {
        return g.put(x(u, v), y, z(u, v), state);
    }

    public boolean free(Grid g, int u, int y, int v) {
        return g.free(x(u, v), y, z(u, v));
    }
}
