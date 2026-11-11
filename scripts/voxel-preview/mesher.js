// Worker-safe pieces of the preview (no three.js, no DOM): the greedy mesher, which turns a
// voxel volume into flat geometry buffers per texture, the Level 56 altar, and the plain-stone
// view. Runs in worker.js; preview.js turns the buffers into three.js meshes.

// ── greedy mesher ───────────────────────────────────────────────────────────────────────────────
// Classic per-axis sweep: for each slice between layers, build a mask of visible faces keyed by
// (block id, facing), then merge equal neighbors into maximal rectangles. Texture UVs are in
// block units with RepeatWrapping, so merged quads still show one texture tile per block.

/**
 * Returns { buffers: [{ tex, pos, nrm, uv, idx }] (typed arrays), quads }. `texFor(id, face)`
 * names the texture of a block face; positions are relative to groundY (y) and world x/z.
 */
export function greedy(v, texFor, groundY, onProgress) {
  const { vol, nx, ny, nz, x0, y0, z0, ids } = v;
  const dims = [nx, ny, nz];
  const at = (x, y, z) => (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz)
    ? (y < 0 ? 1 : 0)                       // below the volume counts as solid
    : vol[(x * nz + z) * ny + y];
  const buffers = new Map();                  // texture name -> {pos, nrm, uv, idx}
  const buf = (tex) => {
    if (!buffers.has(tex)) buffers.set(tex, { pos: [], nrm: [], uv: [], idx: [] });
    return buffers.get(tex);
  };
  let quads = 0;

  for (let d = 0; d < 3; d++) {
    const u = (d + 1) % 3, w = (d + 2) % 3;
    const x = [0, 0, 0], q = [0, 0, 0];
    q[d] = 1;
    const mask = new Int32Array(dims[u] * dims[w]);
    for (x[d] = -1; x[d] < dims[d];) {
      if (onProgress && x[d] % 16 === 0) onProgress((d + (x[d] + 1) / (dims[d] + 1)) / 3);
      let n = 0;
      for (x[w] = 0; x[w] < dims[w]; x[w]++) {
        for (x[u] = 0; x[u] < dims[u]; x[u]++) {
          const a = at(x[0], x[1], x[2]);
          const b = at(x[0] + q[0], x[1] + q[1], x[2] + q[2]);
          // positive id: face of `a` looking +d; negative: face of `b` looking -d
          mask[n++] = (a && !b) ? a : (!a && b) ? -b : 0;
        }
      }
      x[d]++;
      n = 0;
      for (let j = 0; j < dims[w]; j++) {
        for (let i = 0; i < dims[u];) {
          const c = mask[n];
          if (!c) { i++; n++; continue; }
          let width = 1;
          while (i + width < dims[u] && mask[n + width] === c) width++;
          let height = 1;
          outer: for (; j + height < dims[w]; height++) {
            for (let k = 0; k < width; k++) {
              if (mask[n + k + height * dims[u]] !== c) break outer;
            }
          }
          emit(d, u, w, x[d], i, j, width, height, c);
          quads++;
          for (let l = 0; l < height; l++) for (let k = 0; k < width; k++) mask[n + k + l * dims[u]] = 0;
          i += width; n += width;
        }
      }
    }
  }

  function emit(d, u, w, layer, i, j, wd, ht, c) {
    const id = ids[Math.abs(c) - 1];
    const face = d * 2 + (c > 0 ? 0 : 1);          // +x,-x,+y,-y,+z,-z
    const b = buf(texFor(id, face));
    const base = b.pos.length / 3;
    const corner = (du, dw) => {
      const p = [0, 0, 0];
      p[d] = layer; p[u] = i + du; p[w] = j + dw;
      b.pos.push(p[0] + x0, p[1] + y0 - groundY, p[2] + z0);
      const nrm = [0, 0, 0]; nrm[d] = c > 0 ? 1 : -1;
      b.nrm.push(...nrm);
      // side faces: v runs up the world Y axis so textures stand upright
      if (d === 1) b.uv.push(p[0], p[2]);
      else b.uv.push(d === 0 ? p[2] : p[0], p[1]);
    };
    corner(0, 0); corner(wd, 0); corner(wd, ht); corner(0, ht);
    if (c > 0) b.idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
    else b.idx.push(base, base + 2, base + 1, base, base + 3, base + 2);
  }

  const out = [];
  for (const [tex, b] of buffers) {
    out.push({
      tex,
      pos: new Float32Array(b.pos), nrm: new Float32Array(b.nrm), uv: new Float32Array(b.uv),
      idx: new Uint32Array(b.idx),
    });
  }
  return { buffers: out, quads };
}

// ── altar (mirrors Level56ChunkGenerator.placeAltar) ─────────────────────────────────────────────
// Stepped polished diorite pyramid (5×5, 3×3, 1×1) on the highest ground under its footprint,
// lodestone on top, at the monument center.

export function placeAltar(v) {
  const { vol, nx, ny, nz, y0, ids } = v;
  const cx = (nx - 1) / 2, cz = (nz - 1) / 2;
  const diorite = ids.push('polished_diorite'), lodestone = ids.push('lodestone');
  const col = (x, z) => (x * nz + z) * ny;
  let top = -Infinity;
  for (let x = cx - 2; x <= cx + 2; x++) for (let z = cz - 2; z <= cz + 2; z++) {
    for (let k = ny - 1; k >= 0; k--) if (vol[col(x, z) + k]) { top = Math.max(top, k); break; }
  }
  const base = top + 1;
  for (let x = cx - 2; x <= cx + 2; x++) for (let z = cz - 2; z <= cz + 2; z++) {
    for (let k = base - 1; k >= 0 && !vol[col(x, z) + k]; k--) vol[col(x, z) + k] = diorite;
    const d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
    [2, 1, 0].forEach((r, step) => { if (d <= r) vol[col(x, z) + base + step] = diorite; });
    if (d === 0) vol[col(x, z) + base + 3] = lodestone;
  }
  return base + y0;
}

/** Every solid block as one plain block: shows the shape without the inferred materials. */
export function plain(v) {
  const vol = new Uint8Array(v.vol.length);
  for (let i = 0; i < vol.length; i++) vol[i] = v.vol[i] ? 1 : 0;
  return { ...v, vol, ids: ['stone'] };
}
