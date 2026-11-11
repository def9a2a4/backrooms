// Reference port of the runtime voxelizer (generator/VoxelMesh.java). Keep the two in lockstep:
// same decode, same column-ray rules, same hash and noise, so the preview shows what the server
// generates. All integer math goes through Math.imul / >>> so it matches Java int arithmetic.

// ── decode ──────────────────────────────────────────────────────────────────────────────────────

export async function loadMesh(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url}: ${res.status}`);
  const stream = res.body.pipeThrough(new DecompressionStream('gzip'));
  const buf = new Uint8Array(await new Response(stream).arrayBuffer());
  return decodeMesh(buf);
}

export function decodeMesh(buf) {
  const dv = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const magic = String.fromCharCode(buf[0], buf[1], buf[2], buf[3]);
  if (magic !== 'SHM2') throw new Error(`bad magic ${magic}`);
  let p = 4;
  const f32 = () => { const v = dv.getFloat32(p, true); p += 4; return v; };
  const varint = () => {
    let v = 0, shift = 0, b;
    do { b = buf[p++]; v += (b & 0x7f) * 2 ** shift; shift += 7; } while (b & 0x80);
    return v;
  };
  const zz = () => { const v = varint(); return (v % 2) ? -(v + 1) / 2 : v / 2; };

  const lo = [f32(), f32(), f32()];
  const hi = [f32(), f32(), f32()];
  const groundRef = f32();
  const island = { x: f32(), z: f32(), r: f32() };
  const palette = [];
  for (let n = buf[p++], i = 0; i < n; i++) {
    const family = buf[p++], len = buf[p++];
    const id = String.fromCharCode(...buf.subarray(p, p + len));
    p += len;
    const variants = [];
    let total = 0;
    for (let k = buf[p++], j = 0; j < k; j++) {
      const idx = buf[p++], weight = buf[p++];
      total += weight;
      variants.push({ idx, cum: total });
    }
    palette.push({ family, id, variants, total });
  }
  const nv = varint();
  const verts = new Float32Array(nv * 3);
  const q = [0, 0, 0];
  for (let i = 0; i < nv; i++) {
    for (let c = 0; c < 3; c++) {
      q[c] += zz();
      verts[i * 3 + c] = lo[c] + (q[c] / 65535) * (hi[c] - lo[c]);
    }
  }
  const vmat = buf.slice(p, p + nv); p += nv;
  const nt = varint();
  const tris = new Uint32Array(nt * 3);
  let a = 0;
  for (let i = 0; i < nt; i++) {
    a += zz();
    tris[i * 3] = a;
    tris[i * 3 + 1] = a + zz();
    tris[i * 3 + 2] = a + zz();
  }
  return { lo, hi, groundRef, island, palette, verts, vmat, tris, bytes: buf.length };
}

// ── hash and value noise (mirror VoxelMesh) ─────────────────────────────────────────────────────

export function hash(x, y, z) {
  let h = Math.imul(x, 0x27d4eb2d) ^ Math.imul(y, 0x165667b1) ^ Math.imul(z, 0x9e3779b1);
  h = Math.imul(h ^ (h >>> 15), 0x85ebca6b);
  h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35);
  return (h ^ (h >>> 16)) >>> 0;
}
const unit = (h) => h / 4294967296;
const lattice = (x, y, z, salt) => unit(hash(x, y, z ^ Math.imul(salt, 0x632be5ab)));
const smooth = (t) => t * t * (3 - 2 * t);
const lerp = (a, b, t) => a + (b - a) * t;

/** Smooth 3D value noise in [0, 1). */
export function vnoise(x, y, z, salt) {
  const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
  const tx = smooth(x - xi), ty = smooth(y - yi), tz = smooth(z - zi);
  const c = (dx, dy, dz) => lattice(xi + dx, yi + dy, zi + dz, salt);
  return lerp(
    lerp(lerp(c(0, 0, 0), c(1, 0, 0), tx), lerp(c(0, 1, 0), c(1, 1, 0), tx), ty),
    lerp(lerp(c(0, 0, 1), c(1, 0, 1), tx), lerp(c(0, 1, 1), c(1, 1, 1), tx), ty),
    tz);
}

/** Two-octave 2D noise, centered: [-1, 1). */
function fbm2(x, z, scale, salt) {
  return (vnoise(x / scale, 0, z / scale, salt) * 0.65
    + vnoise(x / (scale * 0.37), 0, z / (scale * 0.37), salt + 1) * 0.35) * 2 - 1;
}

// ── tuning (mirror VoxelMesh) ───────────────────────────────────────────────────────────────────

const DIRT_DEPTH = 3;        // dirt layers under a ground surface before stone
const CELL = 8;              // stone-vertex spatial hash cell, blocks
const PICK_MARGIN = 4;       // material picking stops this far below the mesh's lowest point
const WARP_AMP = 2.5;        // blocks; column sample warp, wobbles straight triangle edges
const WARP_SCALE = 20;
const BUMP_GROUND = 1.2;     // blocks; height noise on ground surfaces
const BUMP_STONE = 2.0;      // blocks; height noise on stone surfaces
const BUMP_SCALE = 14;
const MAT_WARP = 3.0;        // blocks; smooth jitter of the stone-material lookup
const MAT_WARP_SCALE = 9;
const MAT_LATTICE = 2;       // blocks; stone material is looked up on this lattice (then jittered)
const CLUMP_SCALE = 5;       // blocks; size of variant clumps
const ISLAND_DEPTH = 6.25;   // underside depth at the island center, in blocks per unit scale
const ISLAND_EDGE = 4;       // minimum thickness at the rim
const ISLAND_RAGGED = 0.18;  // underside noise as a fraction of the local depth

const FILL = 2;              // pseudo-family for plain stone fill

const SALT_WARP_X = 11, SALT_WARP_Z = 13, SALT_BUMP = 17, SALT_MAT = 23, SALT_CLUMP = 31,
  SALT_UNDER = 41;

/** Pick a variant of palette entry `m` for block (x, y, z): clumpy, weighted. */
function variant(palette, m, x, y, z) {
  const e = palette[m];
  if (e.variants.length === 1) return e.variants[0].idx;
  const clump = vnoise(x / CLUMP_SCALE, y / CLUMP_SCALE, z / CLUMP_SCALE, SALT_CLUMP + m);
  const r = Math.min(0.999999, Math.max(0, (clump - 0.5) * 1.6 + 0.5) * 0.7 + unit(hash(x, y, z)) * 0.3);
  const t = r * e.total;
  for (const v of e.variants) if (t < v.cum) return v.idx;
  return e.variants[e.variants.length - 1].idx;
}

// ── voxelize ────────────────────────────────────────────────────────────────────────────────────

/**
 * Voxelize the mesh into a dense volume around the origin.
 * Returns { vol (Uint8Array, palette index + 1, 0 = air), nx, ny, nz, y0, ids } where volume
 * index is (x * nz + z) * ny + (y - y0) and x/z run from -radius..radius.
 */
export function voxelize(mesh, { scale = 16, groundY = 64, radius = 320 } = {}) {
  const { verts, tris, vmat, palette } = mesh;
  const nv = verts.length / 3;
  // world-space vertices: x east, y up (groundRef → groundY), z south
  const W = new Float64Array(nv * 3);
  for (let i = 0; i < nv; i++) {
    W[i * 3] = verts[i * 3] * scale;
    W[i * 3 + 1] = (verts[i * 3 + 1] - mesh.groundRef) * scale + groundY;
    W[i * 3 + 2] = verts[i * 3 + 2] * scale;
  }
  const family = (v) => palette[vmat[v]].family;
  const pal = Object.fromEntries(palette.map((p, i) => [p.id, i]));
  const DIRT = pal.dirt, STONE = pal.stone;
  // Below this level everything is plain stone: no variants, no stone-material lookups.
  let meshMinY = Infinity;
  for (let i = 0; i < nv; i++) meshMinY = Math.min(meshMinY, W[i * 3 + 1]);
  const pickFloor = Math.floor(meshMinY) - PICK_MARGIN;

  // stone-vertex spatial hash for per-voxel stone materials
  const grid = new Map();
  for (let i = 0; i < nv; i++) {
    if (family(i) !== 1) continue;
    const k = cellKey(Math.floor(W[i * 3] / CELL), Math.floor(W[i * 3 + 1] / CELL), Math.floor(W[i * 3 + 2] / CELL));
    let list = grid.get(k);
    if (!list) grid.set(k, list = []);
    list.push(i);
  }
  const nearestStone = (x, y, z) => {
    const cx = Math.floor(x / CELL), cy = Math.floor(y / CELL), cz = Math.floor(z / CELL);
    let best = -1, bd = Infinity;
    for (let r = 1; r <= 3 && best < 0; r++) {
      for (let dx = -r; dx <= r; dx++) for (let dy = -r; dy <= r; dy++) for (let dz = -r; dz <= r; dz++) {
        const list = grid.get(cellKey(cx + dx, cy + dy, cz + dz));
        if (!list) continue;
        for (const v of list) {
          const ex = W[v * 3] - x, ey = W[v * 3 + 1] - y, ez = W[v * 3 + 2] - z;
          const d = ex * ex + ey * ey + ez * ez;
          if (d < bd) { bd = d; best = v; }
        }
      }
    }
    return best;
  };
  // stone material on a coarse lattice, cached: one nearest-vertex search per lattice point
  const latticeCache = new Map();
  const stoneMat = (x, y, z) => {
    const lx = Math.floor(x / MAT_LATTICE), ly = Math.floor(y / MAT_LATTICE), lz = Math.floor(z / MAT_LATTICE);
    const key = cellKey(lx, ly, lz);
    let m = latticeCache.get(key);
    if (m === undefined) {
      const v = nearestStone((lx + 0.5) * MAT_LATTICE, (ly + 0.5) * MAT_LATTICE, (lz + 0.5) * MAT_LATTICE);
      m = v >= 0 ? vmat[v] : STONE;
      latticeCache.set(key, m);
    }
    return m;
  };

  // island underside: lens-shaped, thickest at the center, ragged with noise
  const ix0 = mesh.island.x * scale, iz0 = mesh.island.z * scale, iR = mesh.island.r * scale;
  const maxDepth = ISLAND_DEPTH * scale;
  const islandBottom = (x, z) => {
    const dx = x + 0.5 - ix0, dz = z + 0.5 - iz0;
    const d = Math.sqrt(dx * dx + dz * dz) / iR;
    const depth = ISLAND_EDGE + (maxDepth - ISLAND_EDGE) * Math.sqrt(Math.max(0, 1 - d * d));
    return Math.floor(groundY - depth * (1 + ISLAND_RAGGED * fbm2(x, z, 24, SALT_UNDER)));
  };

  // per-column warped sample points
  const n = 2 * radius + 1;
  const wx = new Float64Array(n * n), wz = new Float64Array(n * n);
  for (let ix = 0; ix < n; ix++) for (let iz = 0; iz < n; iz++) {
    const x = ix - radius, z = iz - radius;
    wx[ix * n + iz] = x + 0.5 + (vnoise(x / WARP_SCALE, 0, z / WARP_SCALE, SALT_WARP_X) * 2 - 1) * WARP_AMP;
    wz[ix * n + iz] = z + 0.5 + (vnoise(x / WARP_SCALE, 0, z / WARP_SCALE, SALT_WARP_Z) * 2 - 1) * WARP_AMP;
  }

  // per-column hit lists
  const hits = new Array(n * n);
  for (let t = 0; t < tris.length; t += 3) {
    const a = tris[t], b = tris[t + 1], c = tris[t + 2];
    const ax = W[a * 3], ay = W[a * 3 + 1], az = W[a * 3 + 2];
    const bx = W[b * 3], by = W[b * 3 + 1], bz = W[b * 3 + 2];
    const cx = W[c * 3], cy = W[c * 3 + 1], cz = W[c * 3 + 2];
    const det = (bx - ax) * (cz - az) - (cx - ax) * (bz - az);
    const ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
    const nxn = uy * vz - uz * vy, nyn = uz * vx - ux * vz, nzn = ux * vy - uy * vx;
    // skip (near-)vertical triangles: |n.y| / |n| < 1e-4
    if (nyn * nyn <= 1e-8 * (nxn * nxn + nyn * nyn + nzn * nzn)) continue;
    const up = nyn > 0;
    const x0 = Math.max(-radius, Math.floor(Math.min(ax, bx, cx) - WARP_AMP - 0.5));
    const x1 = Math.min(radius, Math.ceil(Math.max(ax, bx, cx) + WARP_AMP - 0.5));
    const z0 = Math.max(-radius, Math.floor(Math.min(az, bz, cz) - WARP_AMP - 0.5));
    const z1 = Math.min(radius, Math.ceil(Math.max(az, bz, cz) + WARP_AMP - 0.5));
    for (let x = x0; x <= x1; x++) {
      for (let z = z0; z <= z1; z++) {
        const idx = (x + radius) * n + (z + radius);
        const px = wx[idx], pz = wz[idx];
        const w1 = ((px - ax) * (cz - az) - (cx - ax) * (pz - az)) / det;
        const w2 = ((bx - ax) * (pz - az) - (px - ax) * (bz - az)) / det;
        const w0 = 1 - w1 - w2;
        if (w0 < 0 || w1 < 0 || w2 < 0) continue;
        let dom = a;
        if (w1 > w0 && w1 >= w2) dom = b;
        else if (w2 > w0 && w2 > w1) dom = c;
        const fam = family(dom);
        const y = w0 * ay + w1 * by + w2 * cy
          + fbm2(x, z, BUMP_SCALE, SALT_BUMP) * (fam === 1 ? BUMP_STONE : BUMP_GROUND);
        // dithered top material: pick a vertex with probability = its weight
        const r = unit(hash(x, Math.floor(y), z));
        const pick = r < w0 ? a : r < w0 + w1 ? b : c;
        (hits[idx] ||= []).push({ y, up, fam, mat: vmat[pick] });
      }
    }
  }

  // volume bounds
  let yMax = groundY, y0 = groundY;
  for (let i = 0; i < hits.length; i++) {
    if (!hits[i]) continue;
    for (const h of hits[i]) yMax = Math.max(yMax, h.y);
    const x = Math.floor(i / n) - radius, z = (i % n) - radius;
    y0 = Math.min(y0, islandBottom(x, z));
  }
  y0 -= 1;
  const ny = Math.ceil(yMax) + 3 - y0;
  const vol = new Uint8Array(n * n * ny);

  for (let ix = 0; ix < n; ix++) {
    for (let iz = 0; iz < n; iz++) {
      const list = hits[ix * n + iz];
      const x = ix - radius, z = iz - radius;
      // off the island (a circle of island radius): void
      const ex = x + 0.5 - ix0, ez = z + 0.5 - iz0;
      if (!list || ex * ex + ez * ez > iR * iR) continue;
      const base = (ix * n + iz) * ny;
      const set = (y, m) => {
        const k = y - y0;
        if (k >= 0 && k < ny) vol[base + k] = variant(palette, m, x, y, z) + 1;
      };
      const bottom = islandBottom(x, z);
      const fill = (yTop, yBot, h) => {
        for (let y = yTop; y >= yBot; y--) {
          if (y < pickFloor) {
            for (let k = Math.max(yBot, y0) - y0; k <= y - y0; k++) vol[base + k] = STONE + 1;
            break;
          }
          if (h.fam === FILL) {
            set(y, STONE);
          } else if (h.fam === 0) {
            const depth = yTop - y;
            set(y, depth === 0 ? h.mat : depth <= DIRT_DEPTH ? DIRT : STONE);
          } else {
            const jx = (vnoise(x / MAT_WARP_SCALE, y / MAT_WARP_SCALE, z / MAT_WARP_SCALE, SALT_MAT) * 2 - 1) * MAT_WARP;
            const jy = (vnoise(x / MAT_WARP_SCALE, y / MAT_WARP_SCALE, z / MAT_WARP_SCALE, SALT_MAT + 1) * 2 - 1) * MAT_WARP;
            const jz = (vnoise(x / MAT_WARP_SCALE, y / MAT_WARP_SCALE, z / MAT_WARP_SCALE, SALT_MAT + 2) * 2 - 1) * MAT_WARP;
            set(y, stoneMat(x + 0.5 + jx, y + 0.5 + jy, z + 0.5 + jz));
          }
        }
      };
      list.sort((p, q) => q.y - p.y);
      // A block is solid when its center lies between an up-facing hit (above) and the next
      // down-facing hit (below). Repeated hits of one kind (shared edges) collapse.
      let top = null, lowest = Infinity;
      for (const h of list) {
        if (h.up) {
          if (!top) top = h;
        } else if (top) {
          const yt = Math.floor(top.y - 0.5), yb = Math.ceil(h.y - 0.5);
          if (yt >= yb) { fill(yt, yb, top); lowest = Math.min(lowest, yb); }
          top = null;
        }
      }
      if (top) {
        // open bottom (the ground sheet, and stones standing on it): solid down to the underside
        fill(Math.floor(top.y - 0.5), bottom, top);
      } else if (lowest !== Infinity) {
        // closed bottom (a down-facing hit is lowest): plain stone below it
        fill(lowest - 1, bottom, { fam: FILL });
      }
    }
  }
  return { vol, nx: n, ny, nz: n, y0, ids: palette.map((p) => p.id) };
}

function cellKey(x, y, z) {
  return ((x + 1024) * 2048 + (y + 1024)) * 2048 + (z + 1024);
}
