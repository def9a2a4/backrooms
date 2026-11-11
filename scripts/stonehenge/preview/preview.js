// Browser preview for the Level 56 Stonehenge: decodes the shipped stonehenge.mesh.gz, runs the
// reference voxelizer (voxelize.js), greedy-meshes the visible faces and renders them with the
// vanilla block textures the build script cached in .temp/textures/. Viewer setup follows
// defCoreLib's docs/util/placed3d.js makeViewer().
//
// Serve the repo root (make stonehenge-preview) and open /scripts/stonehenge/preview/.

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { loadMesh, voxelize } from './voxelize.js';

// ?mesh=/.temp/other.mesh.gz previews an alternate build without replacing the shipped file
const MESH_URL = new URLSearchParams(location.search).get('mesh')
  || '/src/main/resources/structures/stonehenge.mesh.gz';
const TEX_URL = '/.temp/textures/';
const GROUND_Y = 64;
// Must match GRASS_TINT in build_mesh.py and grass_color in the salisbury biome.
const GRASS_TINT = 0x8ab64e;
const FOG_COLOR = 0xb4b9bd;

// Texture per (block, face). Faces: 0 +x, 1 -x, 2 +y (top), 3 -y, 4 +z, 5 -z.
const FACE_TEX = {
  grass_block: ['grass_block_side', 'grass_block_side', 'grass_block_top', 'dirt', 'grass_block_side', 'grass_block_side'],
  dirt_path: ['dirt_path_side', 'dirt_path_side', 'dirt_path_top', 'dirt', 'dirt_path_side', 'dirt_path_side'],
  lodestone: ['lodestone_side', 'lodestone_side', 'lodestone_top', 'lodestone_top', 'lodestone_side', 'lodestone_side'],
};
const texFor = (id, face) => (FACE_TEX[id] ? FACE_TEX[id][face] : id);

// ── viewer ──────────────────────────────────────────────────────────────────────────────────────

const container = document.getElementById('view');
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x9fb4c8);
const camera = new THREE.PerspectiveCamera(50, innerWidth / innerHeight, 1, 20000);
camera.position.set(-420, 260, 420);
const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(innerWidth, innerHeight);
renderer.outputColorSpace = THREE.SRGBColorSpace;
container.appendChild(renderer.domElement);
const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.dampingFactor = 0.08;
controls.target.set(0, 40, 0);
controls.update();
scene.add(new THREE.HemisphereLight(0xffffff, 0x5a5040, 1.6));
const sun = new THREE.DirectionalLight(0xffffff, 1.6);
sun.position.set(-0.5, 1, 0.35);
scene.add(sun);
addEventListener('resize', () => {
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight);
});
(function animate() {
  requestAnimationFrame(animate);
  controls.update();
  renderer.render(scene, camera);
})();

const loader = new THREE.TextureLoader();
const materials = new Map();
function materialFor(tex) {
  if (!materials.has(tex)) {
    const t = loader.load(`${TEX_URL}${tex}.png`);
    t.magFilter = THREE.NearestFilter;
    t.minFilter = THREE.NearestMipmapLinearFilter;
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.colorSpace = THREE.SRGBColorSpace;
    const m = new THREE.MeshLambertMaterial({ map: t });
    if (tex === 'grass_block_top') m.color.set(GRASS_TINT);
    materials.set(tex, m);
  }
  return materials.get(tex);
}

// ── greedy mesher ───────────────────────────────────────────────────────────────────────────────
// Classic per-axis sweep: for each slice between layers, build a mask of visible faces keyed by
// (block id, facing), then merge equal neighbors into maximal rectangles. Texture UVs are in
// block units with RepeatWrapping, so merged quads still show one texture tile per block.

function greedy(v) {
  const { vol, nx, ny, nz, y0, ids } = v;
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
      b.pos.push(p[0] - (nx - 1) / 2, p[1] + y0 - GROUND_Y, p[2] - (nz - 1) / 2);
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

  const group = new THREE.Group();
  for (const [tex, b] of buffers) {
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(b.pos, 3));
    g.setAttribute('normal', new THREE.Float32BufferAttribute(b.nrm, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(b.uv, 2));
    g.setIndex(b.idx);
    group.add(new THREE.Mesh(g, materialFor(tex)));
  }
  return { group, quads };
}

// ── altar (mirrors Level56ChunkGenerator.placeAltar) ─────────────────────────────────────────────
// Stepped polished diorite pyramid (5×5, 3×3, 1×1) on the highest ground under its footprint,
// lodestone on top, at the monument center.

function placeAltar(v) {
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

// ── source mesh overlay ─────────────────────────────────────────────────────────────────────────

function wireframe(mesh, scale) {
  const g = new THREE.BufferGeometry();
  const p = new Float32Array(mesh.verts.length);
  for (let i = 0; i < p.length; i += 3) {
    p[i] = mesh.verts[i] * scale;
    p[i + 1] = (mesh.verts[i + 1] - mesh.groundRef) * scale + 0.05;
    p[i + 2] = mesh.verts[i + 2] * scale;
  }
  g.setAttribute('position', new THREE.BufferAttribute(p, 3));
  g.setIndex(new THREE.BufferAttribute(mesh.tris, 1));
  return new THREE.Mesh(g, new THREE.MeshBasicMaterial({ color: 0xff3366, wireframe: true }));
}

// ── UI ──────────────────────────────────────────────────────────────────────────────────────────

const $ = (id) => document.getElementById(id);
const stats = $('stats');
let mesh = null, voxels = null, wire = null;

function clear(obj) {
  if (!obj) return;
  scene.remove(obj);
  obj.traverse((o) => o.geometry && o.geometry.dispose());
}

async function build() {
  const scale = Number($('scale').value);
  const radius = Number($('radius').value);
  stats.textContent = 'voxelizing…';
  await new Promise((r) => setTimeout(r, 20));
  const t0 = performance.now();
  const v = voxelize(mesh, { scale, groundY: GROUND_Y, radius });
  placeAltar(v);
  const t1 = performance.now();
  const { group, quads } = greedy(v);
  const t2 = performance.now();
  clear(voxels);
  voxels = group;
  scene.add(voxels);
  clear(wire);
  wire = wireframe(mesh, scale);
  wire.visible = $('wire').checked;
  scene.add(wire);
  let solid = 0;
  for (const b of v.vol) if (b) solid++;
  stats.textContent =
    `mesh      ${(mesh.bytes / 1024).toFixed(1)} KB raw, ${mesh.tris.length / 3} tris, ${mesh.verts.length / 3} verts\n` +
    `volume    ${v.nx}×${v.ny}×${v.nz}, ${solid.toLocaleString()} solid\n` +
    `height    ${v.y0}..${v.y0 + v.ny - 1}\n` +
    `quads     ${quads.toLocaleString()}\n` +
    `voxelize  ${(t1 - t0).toFixed(0)} ms, mesh ${(t2 - t1).toFixed(0)} ms`;
}

$('build').onclick = build;
$('wire').onchange = () => { if (wire) wire.visible = $('wire').checked; };
$('fog').onchange = () => {
  scene.fog = $('fog').checked ? new THREE.Fog(FOG_COLOR, 24, 192) : null;
  scene.background.set($('fog').checked ? FOG_COLOR : 0x9fb4c8);
};

try {
  mesh = await loadMesh(MESH_URL);
  await build();
} catch (e) {
  stats.textContent = `error: ${e.message}`;
  console.error(e);
}
