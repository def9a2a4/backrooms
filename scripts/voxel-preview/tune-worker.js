// Live block matching for the preview's tuning sliders: a port of build_scan.py's
// match_cells()/assign() (blur, OKLab nearest block with the texture-contrast term,
// majority filter, glass), run on the per-cell export (build_scan.py --cells-only). Keep the two
// in step: the sliders pick MODELS[...]["tune"] for the real build.
//
// in:  {type: 'load', url} -> {type: 'loaded', count, tune, palette ids}
//      {type: 'verts', verts} (mesh-space vertex positions of the mesh being tuned)
//      {type: 'run', id, tune: {blur, spread, pattern, majority}}
//        -> progress..., {type: 'done', id, mats, vmats}
// vmats re-derives each vertex's material from the tuned cells (the nearest cell, or the nearest
// coarse brick), as the build does: triangles that drifted off the scanned surface use them.

let C = null;       // the export, decoded
let VERTS = null;   // mesh-space vertex positions, for vmats

function progress(phase, f) { postMessage({ type: 'progress', phase, f }); }

async function load(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url}: ${res.status}`);
  // count bytes as they arrive for the progress bar, then gunzip
  const total = Number(res.headers.get('content-length')) || 0;
  let seen = 0;
  const counted = res.body.pipeThrough(new TransformStream({
    transform(chunk, ctl) {
      seen += chunk.length;
      if (total) progress('loading cells', Math.min(seen / total, 1) * 0.9);
      ctl.enqueue(chunk);
    },
  }));
  const buf = await new Response(counted.pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();
  progress('indexing cells', 0.95);
  const dv = new DataView(buf);
  const magic = String.fromCharCode(...new Uint8Array(buf, 0, 4));
  if (magic !== 'CEL1') throw new Error(`bad magic ${magic}`);
  const hlen = dv.getUint32(4, true);
  const h = JSON.parse(new TextDecoder().decode(new Uint8Array(buf, 8, hlen)));
  let p = 8 + hlen;
  p += (4 - (p % 4)) % 4;
  const n = h.count;
  const u32 = () => { const a = new Uint32Array(buf, p, n); p += 4 * n; return a; };
  const f32 = () => { const a = new Float32Array(buf, p, n); p += 4 * n; return a; };
  const keys = u32();
  const L = f32(), A = f32(), B = f32(), con = f32(), up = f32(), w = f32(), frac = f32();
  // open-addressing hash: key -> cell index
  let size = 1; while (size < n * 2) size <<= 1;
  const table = new Int32Array(size).fill(-1);
  const mask = size - 1;
  for (let i = 0; i < n; i++) {
    if ((i & 0xfffff) === 0) progress('indexing cells', 0.9 + 0.1 * i / n);
    let s = Math.imul(keys[i], 0x9e3779b1) >>> 0 & mask;
    while (table[s] >= 0) s = (s + 1) & mask;
    table[s] = i;
  }
  const find = (k) => {
    let s = Math.imul(k, 0x9e3779b1) >>> 0 & mask;
    for (;;) {
      const i = table[s];
      if (i < 0) return -1;
      if (keys[i] === k) return i;
      s = (s + 1) & mask;
    }
  };
  C = { h, n, keys, L, A, B, con, up, w, frac, find };
  return { count: n, tune: h.tune, ids: h.palette.map((e) => e.id) };
}

// neighbor offsets in build_scan.py's loop order (dx, dy, dz), self excluded
const OFFS = [];
for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
  if (dx || dy || dz) OFFS.push([dx, dy, dz, 1 / Math.sqrt(dx * dx + dy * dy + dz * dz)]);
}

/** Index of each cell's 26 neighbors, -1 if absent (computed on the fly, per pass). */
function neighborsOf(i, out) {
  const { keys, h, find } = C;
  const [DX, DY, DZ] = h.dims;
  const k = keys[i], y = k % DY, z = Math.floor(k / DY) % DZ, x = Math.floor(k / (DY * DZ));
  for (let o = 0; o < 26; o++) {
    const [dx, dy, dz] = OFFS[o];
    const nx = x + dx, ny = y + dy, nz = z + dz;
    out[o] = (nx < 0 || ny < 0 || nz < 0 || nx >= DX || ny >= DY || nz >= DZ)
      ? -1 : find((nx * DZ + nz) * DY + ny);
  }
}

/** build_scan.py blur_cells(): low-pass the colors with a blur of about `radius` cells, through
 *  bricks of 2*radius cells and a trilinear mix of the 8 brick averages around each cell (each
 *  weighted by the surface it holds). */
function blurCells(mL, mA, mB, radius) {
  const { n, h, keys, w } = C;
  const [DX, DY, DZ] = h.dims;
  const S = Math.max(Math.round(2 * radius), 1);
  const BY = Math.floor((DY - 1) / S) + 2, BZ = Math.floor((DZ - 1) / S) + 2;
  const bkey = (x, y, z) => (x * BZ + z) * BY + y;
  const bricks = new Map();     // key -> index into bw/bL/bA/bB
  const bw = [], bL = [], bA = [], bB = [];
  const cx = new Int32Array(n), cy = new Int32Array(n), cz = new Int32Array(n);
  for (let i = 0; i < n; i++) {
    const k = keys[i];
    cy[i] = k % DY; cz[i] = Math.floor(k / DY) % DZ; cx[i] = Math.floor(k / (DY * DZ));
    const bk = bkey(Math.floor(cx[i] / S), Math.floor(cy[i] / S), Math.floor(cz[i] / S));
    let j = bricks.get(bk);
    if (j === undefined) { j = bw.length; bricks.set(bk, j); bw.push(0); bL.push(0); bA.push(0); bB.push(0); }
    bw[j] += w[i]; bL[j] += w[i] * mL[i]; bA[j] += w[i] * mA[i]; bB[j] += w[i] * mB[i];
  }
  for (let j = 0; j < bw.length; j++) { bL[j] /= bw[j]; bA[j] /= bw[j]; bB[j] /= bw[j]; }
  const oL = new Float32Array(n), oA = new Float32Array(n), oB = new Float32Array(n);
  const u0 = [0, 0, 0], f = [0, 0, 0];
  for (let i = 0; i < n; i++) {
    if ((i & 0x3ffff) === 0) progress('blur', i / n);
    const c = [cx[i], cy[i], cz[i]];
    for (let a = 0; a < 3; a++) {
      const u = (c[a] + 0.5) / S - 0.5;
      u0[a] = Math.floor(u); f[a] = u - u0[a];
    }
    let aL = 0, aA = 0, aB = 0, tot = 0;
    for (let ox = 0; ox < 2; ox++) for (let oy = 0; oy < 2; oy++) for (let oz = 0; oz < 2; oz++) {
      const x = u0[0] + ox, y = u0[1] + oy, z = u0[2] + oz;
      if (x < 0 || y < 0 || z < 0) continue;
      const j = bricks.get(bkey(x, y, z));
      if (j === undefined) continue;
      const t = (ox ? f[0] : 1 - f[0]) * (oy ? f[1] : 1 - f[1]) * (oz ? f[2] : 1 - f[2]) * bw[j];
      aL += t * bL[j]; aA += t * bA[j]; aB += t * bB[j]; tot += t;
    }
    oL[i] = aL / tot; oA[i] = aA / tot; oB[i] = aB / tot;
  }
  return [oL, oA, oB];
}

function run(t) {
  const { n, h, L, A, B, con, up, w, frac } = C;
  let mL = Float32Array.from(L), mA = Float32Array.from(A), mB = Float32Array.from(B);
  const nb = new Int32Array(26);
  const steps = (t.blur > 0 ? 1 : 0) + 1 + t.majority;
  let step = 0;
  if (t.blur > 0) {
    progress('blur', 0);
    [mL, mA, mB] = blurCells(mL, mA, mB, t.blur);
    step++;
  }
  // shading overlay (build_scan.py SHADE): height across the model, slope from how much the
  // surface faces up
  if (t.height || t.slope) {
    const DY = h.dims[1];
    for (let i = 0; i < n; i++) {
      const y = C.keys[i] % DY;
      mL[i] += (t.height * (y / Math.max(DY - 1, 1) - 0.5) + t.slope * (Math.min(Math.max(up[i], 0), 1) - 0.5)) * 0.2;
    }
  }
  // assign(): normalize the scan's color statistics onto the palette's, then nearest block
  progress('matching', step / steps);
  const pal = h.palette, P = pal.length, fx = h.fixed;
  let ws = 0, fm = [0, 0, 0];
  for (let i = 0; i < n; i++) { ws += w[i]; fm[0] += w[i] * mL[i]; fm[1] += w[i] * mA[i]; fm[2] += w[i] * mB[i]; }
  fm = fm.map((v) => v / ws);
  let fs0 = 0;
  for (let i = 0; i < n; i++) fs0 += w[i] * (mL[i] - fm[0]) ** 2;
  fs0 = Math.sqrt(fs0 / ws);
  const pl = pal.map((e) => [0, 1, 2].map((c) => (e.side[c] + e.top[c]) / 2));
  const pm = [0, 1, 2].map((c) => pl.reduce((s, v) => s + v[c], 0) / P);
  const ps0 = Math.sqrt(pl.reduce((s, v) => s + (v[0] - pm[0]) ** 2, 0) / P);
  const scaleL = ps0 * t.spread / Math.max(fs0, 1e-6);
  const mats = new Uint8Array(n);
  for (let i = 0; i < n; i++) {
    if ((i & 0x3ffff) === 0) progress('matching', (step + i / n) / steps);
    const qL = (mL[i] - fm[0]) * scaleL + pm[0];
    let qa = (mA[i] - fm[1]) * fx.chroma + fm[1] * (1 - fx.cast);
    let qb = (mB[i] - fm[2]) * fx.chroma + fm[2] * (1 - fx.cast);
    if (fx.deadzone) {
      const c = Math.hypot(qa, qb), s = Math.max(c - fx.deadzone, 0) / Math.max(c, 1e-9);
      qa *= s; qb *= s;
    }
    const f = Math.min(Math.max(up[i], 0), 1), g = Math.min(Math.max(-up[i], 0), 1), sd = 1 - f - g;
    let best = 0, bd = Infinity;
    for (let k = 0; k < P; k++) {
      const e = pal[k];
      const dL = qL - (e.side[0] * sd + e.top[0] * f + e.bottom[0] * g);
      const da = qa - (e.side[1] * sd + e.top[1] * f + e.bottom[1] * g);
      const db = qb - (e.side[2] * sd + e.top[2] * f + e.bottom[2] * g);
      const dc = con[i] - e.contrast;
      const d = 0.25 * dL * dL + da * da + db * db + e.penalty + t.pattern * dc * dc;
      if (d < bd) { bd = d; best = k; }
    }
    mats[i] = best;
  }
  step++;
  let cur = mats;
  const votes = new Int32Array(27);
  for (let pass = 0; pass < t.majority; pass++, step++) {
    const next = new Uint8Array(n);
    for (let i = 0; i < n; i++) {
      if ((i & 0x3ffff) === 0) progress('majority', (step + i / n) / steps);
      neighborsOf(i, nb);
      votes[0] = cur[i];
      for (let o = 0; o < 26; o++) votes[o + 1] = nb[o] < 0 ? cur[i] : cur[nb[o]];
      let bestK = 0, bestC = -1;
      for (let k = 0; k < 27; k++) {
        let c = 0;
        for (let m = 0; m < 27; m++) if (votes[m] === votes[k]) c++;
        if (c > bestC) { bestC = c; bestK = k; }
      }
      next[i] = votes[bestK];
    }
    cur = next;
  }
  if (fx.forced >= 0) {
    for (let i = 0; i < n; i++) if (frac[i] >= fx.forced_fraction) cur[i] = fx.forced;
  }
  return cur;
}

/** Per-vertex material from the tuned cells: the cell under the vertex or within 2 cells (as
 *  the runtime's grid lookup), else the nearest 8-cell brick within 4 bricks, else -1. */
function vertexMats(mats) {
  if (!VERTS) return null;
  const { h, keys, find } = C;
  const [DX, DY, DZ] = h.dims, cell = h.cell, o = h.origin;
  const BR = 8, BX = Math.ceil(DX / BR), BY = Math.ceil(DY / BR), BZ = Math.ceil(DZ / BR);
  const brick = new Map();
  for (let i = 0; i < keys.length; i++) {
    const k = keys[i], y = k % DY, z = Math.floor(k / DY) % DZ, x = Math.floor(k / (DY * DZ));
    brick.set((Math.floor(x / BR) * BZ + Math.floor(z / BR)) * BY + Math.floor(y / BR), mats[i]);
  }
  const nv = VERTS.length / 3, out = new Int16Array(nv);
  for (let v = 0; v < nv; v++) {
    if ((v & 0x3ffff) === 0) progress('vertices', v / nv);
    const fx = (VERTS[v * 3] - o[0]) / cell, fy = (VERTS[v * 3 + 1] - o[1]) / cell, fz = (VERTS[v * 3 + 2] - o[2]) / cell;
    const cx = Math.floor(fx), cy = Math.floor(fy), cz = Math.floor(fz);
    let best = -1, bd = Infinity;
    for (let r = 0; r <= 2 && best < 0; r++) {
      for (let dx = -r; dx <= r; dx++) for (let dy = -r; dy <= r; dy++) for (let dz = -r; dz <= r; dz++) {
        if (Math.max(Math.abs(dx), Math.abs(dy), Math.abs(dz)) !== r) continue;
        const x = cx + dx, y = cy + dy, z = cz + dz;
        if (x < 0 || y < 0 || z < 0 || x >= DX || y >= DY || z >= DZ) continue;
        const i = find((x * DZ + z) * DY + y);
        if (i < 0) continue;
        const d = (x + 0.5 - fx) ** 2 + (y + 0.5 - fy) ** 2 + (z + 0.5 - fz) ** 2;
        if (d < bd) { bd = d; best = mats[i]; }
      }
    }
    if (best < 0) {
      const bx = Math.floor(fx / BR), by = Math.floor(fy / BR), bz = Math.floor(fz / BR);
      for (let r = 0; r <= 4 && best < 0; r++) {
        for (let dx = -r; dx <= r; dx++) for (let dy = -r; dy <= r; dy++) for (let dz = -r; dz <= r; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dy), Math.abs(dz)) !== r) continue;
          const x = bx + dx, y = by + dy, z = bz + dz;
          if (x < 0 || y < 0 || z < 0 || x >= BX || y >= BY || z >= BZ) continue;
          const m = brick.get((x * BZ + z) * BY + y);
          if (m === undefined) continue;
          const d = ((x + 0.5) * BR - fx) ** 2 + ((y + 0.5) * BR - fy) ** 2 + ((z + 0.5) * BR - fz) ** 2;
          if (d < bd) { bd = d; best = m; }
        }
      }
    }
    out[v] = best;
  }
  return out;
}

onmessage = async ({ data }) => {
  try {
    if (data.type === 'load') postMessage({ type: 'loaded', ...(await load(data.url)) });
    else if (data.type === 'verts') VERTS = data.verts;
    else if (data.type === 'run') {
      const mats = run(data.tune);
      const vmats = vertexMats(mats);
      postMessage({ type: 'done', id: data.id, mats, vmats }, vmats ? [mats.buffer, vmats.buffer] : [mats.buffer]);
    }
  } catch (e) {
    postMessage({ type: 'error', message: e.message });
  }
};
