// Browser preview of voxelized meshes: Level 56's shipped stonehenge.mesh.gz and the scan builds
// in .temp/meshes/ (models.json lists them). Runs the reference voxelizers (voxelize.js: column
// rays for terrain, a thickened shell for interiors), greedy-meshes the visible faces and renders
// them with vanilla block textures from .temp/textures/ (serve.py fetches missing ones). Viewer
// setup follows defCoreLib's docs/util/placed3d.js makeViewer().
//
// Serve the repo root (make voxel-preview) and open /scripts/voxel-preview/.
// URL params: ?model=<id>&scale=<n>, or ?mesh=<url> to preview any .mesh.gz as a column model.

import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { loadMesh } from './voxelize.js';

const TEX_URL = '/.temp/textures/';
const GROUND_Y = 64;
// Must match GRASS_TINT in build_mesh.py and grass_color in the salisbury biome.
const GRASS_TINT = 0x8ab64e;
const SKY = 0x9fb4c8;
const MAX_VOXELS = 400e6;   // dense volume budget; past this the tab runs out of memory

const BLOCKS = await (await fetch('./blocks.json')).json();
const MODELS = await (await fetch('./models.json')).json();

// Texture per (block, face). Faces: 0 +x, 1 -x, 2 +y (top), 3 -y, 4 +z, 5 -z.
function texFor(id, face) {
  const t = BLOCKS[id] ?? id;
  if (typeof t === 'string') return t;
  return face === 2 ? t.top : face === 3 ? t.bottom : t.side;
}

// ── viewer ──────────────────────────────────────────────────────────────────────────────────────

const container = document.getElementById('view');
const scene = new THREE.Scene();
scene.background = new THREE.Color(SKY);
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
    // animated textures are vertical strips: show the first frame
    const t = loader.load(`${TEX_URL}${tex}.png`, (t) => {
      const { width, height } = t.image;
      if (height > width) t.repeat.set(1, width / height);
    });
    t.magFilter = THREE.NearestFilter;
    t.minFilter = THREE.NearestMipmapLinearFilter;
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.colorSpace = THREE.SRGBColorSpace;
    // glass: alpha-tested, so the frame shows and the pane is see-through
    const m = new THREE.MeshLambertMaterial({ map: t, alphaTest: 0.5, side: tex === 'glass' ? THREE.DoubleSide : THREE.FrontSide });
    if (tex === 'grass_block_top') m.color.set(GRASS_TINT);
    materials.set(tex, m);
  }
  return materials.get(tex);
}

// ── voxel geometry (built in worker.js; see mesher.js) ─────────────────────────────────────────

function voxelGroup(buffers) {
  const group = new THREE.Group();
  for (const b of buffers) {
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(b.pos, 3));
    g.setAttribute('normal', new THREE.BufferAttribute(b.nrm, 3));
    g.setAttribute('uv', new THREE.BufferAttribute(b.uv, 2));
    g.setIndex(new THREE.BufferAttribute(b.idx, 1));
    group.add(new THREE.Mesh(g, materialFor(b.tex)));
  }
  return group;
}

// ── source mesh overlay ─────────────────────────────────────────────────────────────────────────

function wireframe(mesh, scale, baseY) {
  const g = new THREE.BufferGeometry();
  const p = new Float32Array(mesh.verts.length);
  for (let i = 0; i < p.length; i += 3) {
    p[i] = mesh.verts[i] * scale;
    p[i + 1] = (mesh.verts[i + 1] - baseY) * scale + 0.05;
    p[i + 2] = mesh.verts[i + 2] * scale;
  }
  g.setAttribute('position', new THREE.BufferAttribute(p, 3));
  g.setIndex(new THREE.BufferAttribute(mesh.tris, 1));
  return new THREE.Mesh(g, new THREE.MeshBasicMaterial({ color: 0xff3366, wireframe: true }));
}

// ── UI ──────────────────────────────────────────────────────────────────────────────────────────

const $ = (id) => document.getElementById(id);
const stats = $('stats');
const params = new URLSearchParams(location.search);
if (params.get('mesh')) {
  MODELS.unshift({ id: 'custom', title: params.get('mesh'), mesh: params.get('mesh'), mode: 'column', scale: 16, radius: 320 });
}
for (const m of MODELS) $('model').add(new Option(m.title, m.id));
const meshes = new Map();       // url -> decoded mesh
let voxels = null, wire = null;

function clear(obj) {
  if (!obj) return;
  scene.remove(obj);
  obj.traverse((o) => o.geometry && o.geometry.dispose());
}

const model = () => MODELS.find((m) => m.id === $('model').value);

function selectModel(m, scale) {
  $('model').value = m.id;
  $('scale').value = String(scale ?? m.scale);
  $('radius').value = m.radius;
  $('thick-row').style.display = m.mode === 'shell' ? '' : 'none';
  const sizes = Object.keys(m.sizes ?? {});
  $('size-row').style.display = sizes.length ? '' : 'none';
  $('size').replaceChildren(...sizes.map((k) => new Option(k, k)));
  if (sizes.includes(params.get('size'))) $('size').value = params.get('size');
  const [title, author, url] = m.credit ?? [];
  $('credit').innerHTML = title
    ? `"<a href="${url}">${title}</a>" by ${author}, CC BY 4.0. Decimated and voxelized.` : '';
  applyFog();
}

function applyFog() {
  const fog = model().fog;
  $('fog-row').style.display = fog ? '' : 'none';
  const on = fog && $('fog').checked;
  scene.fog = on ? new THREE.Fog(fog[0], fog[1], fog[2]) : null;
  scene.background.set(on ? fog[0] : SKY);
}

const meshUrl = (m) => (m.sizes ? m.sizes[$('size').value] : m.mesh);

async function getMesh(m) {
  const url = meshUrl(m);
  if (!meshes.has(url)) meshes.set(url, await loadMesh(url));
  return meshes.get(url);
}

// ── reduced mesh view ───────────────────────────────────────────────────────────────────────────
// The decimated triangles as they are, before voxelizing, both sides (scan winding isn't
// reliable), textured per pixel from the surface grid.

// Block textures as one 16×16 layer per palette entry (top and side arrays), first frame only.
async function blockLayers(palette) {
  const layers = async (face) => {
    const data = new Uint8Array(palette.length * 16 * 16 * 4);
    await Promise.all(palette.map(async (e, i) => {
      const img = new Image();
      img.src = `${TEX_URL}${texFor(e.id, face)}.png`;
      await img.decode();
      const cv = document.createElement('canvas');
      cv.width = cv.height = 16;
      const ctx = cv.getContext('2d');
      ctx.drawImage(img, 0, 0, img.width, img.width, 0, 0, 16, 16);
      data.set(ctx.getImageData(0, 0, 16, 16).data, i * 1024);
    }));
    const t = new THREE.DataArrayTexture(data, 16, 16, palette.length);
    t.magFilter = THREE.NearestFilter;
    t.minFilter = THREE.NearestFilter;
    t.needsUpdate = true;
    return t;
  };
  return { top: await layers(2), side: await layers(0) };
}

// The surface grid as a 3D texture of palette index + 1 (0 = empty), dilated twice so the
// decimated surface, which drifts a little off the original, still lands in filled cells.
const gridTextures = new WeakMap();
function gridTexture(grid) {
  if (gridTextures.has(grid)) return gridTextures.get(grid);
  const [dx, dy, dz] = grid.dims;
  let a = new Uint8Array(dx * dy * dz);
  for (const [k, m] of grid.cells) {
    const y = k % dy, z = Math.floor(k / dy) % dz, x = Math.floor(k / (dy * dz));
    a[x + dx * (y + dy * z)] = m + 1;
  }
  const sx = 1, sy = dx, sz = dx * dy;
  for (let pass = 0; pass < 2; pass++) {
    const b = a.slice();
    for (let z = 0; z < dz; z++) for (let y = 0; y < dy; y++) for (let x = 0; x < dx; x++) {
      const i = x + sy * y + sz * z;
      if (a[i]) continue;
      const v = (x > 0 && a[i - sx]) || (x < dx - 1 && a[i + sx]) || (y > 0 && a[i - sy])
        || (y < dy - 1 && a[i + sy]) || (z > 0 && a[i - sz]) || (z < dz - 1 && a[i + sz]);
      if (v) b[i] = v;
    }
    a = b;
  }
  const t = new THREE.Data3DTexture(a, dx, dy, dz);
  t.format = THREE.RedFormat;
  t.type = THREE.UnsignedByteType;
  t.magFilter = t.minFilter = THREE.NearestFilter;
  t.unpackAlignment = 1;
  t.needsUpdate = true;
  gridTextures.set(grid, t);
  return t;
}

const EMPTY_GRID = new THREE.Data3DTexture(new Uint8Array(1), 1, 1, 1);
EMPTY_GRID.format = THREE.RedFormat;
EMPTY_GRID.needsUpdate = true;

const MESH_VERT = /* glsl */ `
  uniform float uScale, uBaseY;
  in float mat;
  out vec3 vWorld, vMesh;
  flat out float vMat;
  void main() {
    vWorld = position;
    vMesh = vec3(position.x / uScale, position.y / uScale + uBaseY, position.z / uScale);
    vMat = mat;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }`;
const MESH_FRAG = /* glsl */ `
  precision highp sampler3D;
  precision highp sampler2DArray;
  uniform sampler3D uGrid;
  uniform sampler2DArray uTop, uSide;
  uniform vec3 uOrigin, uDims, uTint;
  uniform float uCell, uGrass;
  uniform bool uUseGrid, uMats;
  in vec3 vWorld, vMesh;
  flat in float vMat;
  out vec4 fragColor;
  void main() {
    vec3 n = normalize(cross(dFdx(vWorld), dFdy(vWorld)));
    float m = vMat;
    if (uUseGrid) {
      vec3 t = (vMesh - uOrigin) / uCell / uDims;
      if (all(greaterThanEqual(t, vec3(0.0))) && all(lessThan(t, vec3(1.0)))) {
        float g = floor(texture(uGrid, t).r * 255.0 + 0.5);
        if (g > 0.0) m = g - 1.0;
      }
    }
    vec3 an = abs(n);
    bool top = an.y >= an.x && an.y >= an.z;
    vec2 uv = top ? vWorld.xz : (an.x >= an.z ? vWorld.zy : vWorld.xy);
    uv = fract(uv);
    uv.y = 1.0 - uv.y;
    vec4 tc = top ? texture(uTop, vec3(uv, m)) : texture(uSide, vec3(uv, m));
    if (uMats && tc.a < 0.5) discard;          // glass panes
    vec3 c = tc.rgb;
    if (top && abs(m - uGrass) < 0.5) c *= uTint;
    if (!uMats) c = vec3(0.62);
    float l = 0.45 + 0.55 * abs(dot(n, normalize(vec3(-0.5, 1.0, 0.35))));
    fragColor = vec4(c * l, 1.0);
  }`;

/** The decimated triangles, textured: per pixel, the block from the surface grid (else the
 *  triangle's vertex material), drawn with its vanilla texture at one block per tile. */
async function solidMesh(mesh, scale, baseY, mats, useGrid) {
  const nt = mesh.tris.length;
  const pos = new Float32Array(nt * 3), mat = new Float32Array(nt);
  for (let i = 0; i < nt; i++) {
    const v = mesh.tris[i];
    pos[i * 3] = mesh.verts[v * 3] * scale;
    pos[i * 3 + 1] = (mesh.verts[v * 3 + 1] - baseY) * scale;
    pos[i * 3 + 2] = mesh.verts[v * 3 + 2] * scale;
    mat[i] = mesh.vmat[mesh.tris[i - (i % 3)]];      // flat: the triangle's first vertex
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  g.setAttribute('mat', new THREE.BufferAttribute(mat, 1));
  const { top, side } = await blockLayers(mesh.palette);
  const grid = mesh.grid;
  const tint = new THREE.Color(GRASS_TINT);
  const material = new THREE.ShaderMaterial({
    glslVersion: THREE.GLSL3,
    vertexShader: MESH_VERT,
    fragmentShader: MESH_FRAG,
    side: THREE.DoubleSide,
    uniforms: {
      uScale: { value: scale }, uBaseY: { value: baseY },
      uGrid: { value: grid ? gridTexture(grid) : EMPTY_GRID },
      uUseGrid: { value: !!grid && useGrid },
      uOrigin: { value: new THREE.Vector3(...(grid ? grid.origin : [0, 0, 0])) },
      uDims: { value: new THREE.Vector3(...(grid ? grid.dims : [1, 1, 1])) },
      uCell: { value: grid ? grid.cell : 1 },
      uTop: { value: top }, uSide: { value: side },
      uTint: { value: new THREE.Vector3(tint.r, tint.g, tint.b) },
      uGrass: { value: mesh.palette.findIndex((e) => e.id === 'grass_block') },
      uMats: { value: mats },
    },
  });
  return new THREE.Mesh(g, material);
}

let first = true;
async function build() {
  const m = model();
  const scale = Number($('scale').value);
  const radius = Number($('radius').value);
  stats.textContent = 'loading…';
  let mesh;
  try {
    mesh = await getMesh(m);
  } catch (e) {
    stats.textContent = `${e.message}\nbuild it: ${m.build ?? ''}${m.sizes ? ' --max-kb N --output ' + meshUrl(m).slice(1) : ''}`;
    return;
  }
  const ext = [0, 1, 2].map((c) => (mesh.hi[c] - mesh.lo[c]) * scale);
  $('grid-row').style.display = mesh.grid ? '' : 'none';
  const finish = () => {
    if (first || $('refit').checked) fit(ext);
    first = false;
    const q = new URLSearchParams(location.search);
    q.set('model', m.id);
    q.set('scale', scale);
    q.set('view', $('viewmode').value);
    if (m.sizes) q.set('size', $('size').value); else q.delete('size');
    history.replaceState(null, '', `?${q}`);
  };
  if ($('viewmode').value === 'mesh') {
    cancelJob();
    const baseY = m.mode === 'shell' ? mesh.lo[1] : mesh.groundRef;
    clear(voxels);
    voxels = await solidMesh(mesh, scale, baseY, $('mats').checked, $('grid').checked);
    scene.add(voxels);
    clear(wire);
    wire = wireframe(mesh, scale, baseY);
    wire.visible = $('wire').checked;
    scene.add(wire);
    stats.textContent =
      `mesh      ${(mesh.bytes / 1024).toFixed(1)} KB raw, ${mesh.tris.length / 3} tris, ${mesh.verts.length / 3} verts\n` +
      `size      ${ext.map((e) => Math.round(e)).join(' × ')} blocks (x y z)`;
    finish();
    return;
  }
  const side = (c) => Math.min(ext[c] + 8, 2 * radius + 1);
  const est = side(0) * side(2) * (ext[1] + (m.mode === 'column' ? 6.25 * scale + 8 : 8));
  if (est > MAX_VOXELS) {
    stats.textContent = `~${(est / 1e6).toFixed(0)}M voxels: too big.\nLower the scale or the crop radius.`;
    return;
  }
  const opts = { scale, groundY: GROUND_Y, radius, useGrid: $('grid').checked };
  if (m.mode === 'shell') opts.thickness = Number($('thick').value);
  const baseY = m.mode === 'shell' ? mesh.lo[1] : mesh.groundRef;
  let r;
  try {
    r = await runJob({ url: new URL(meshUrl(m), location.href).href, mode: m.mode, opts,
      altar: !!m.altar, mats: $('mats').checked, blocks: BLOCKS, groundY: GROUND_Y });
  } catch (e) {
    if (e.message !== 'cancelled') stats.textContent = `error: ${e.message}`;
    return;
  }
  clear(voxels);
  voxels = voxelGroup(r.buffers);
  scene.add(voxels);
  clear(wire);
  wire = wireframe(mesh, scale, baseY);
  wire.visible = $('wire').checked;
  scene.add(wire);
  const v = r.volume;
  stats.textContent =
    `mesh      ${(mesh.bytes / 1024).toFixed(1)} KB raw, ${mesh.tris.length / 3} tris, ${mesh.verts.length / 3} verts\n` +
    `size      ${ext.map((e) => Math.round(e)).join(' × ')} blocks (x y z)\n` +
    `grid      ${mesh.grid ? `${mesh.grid.cells.size.toLocaleString()} cells of ${mesh.grid.cell} m` : 'none'}\n` +
    `volume    ${v.nx}×${v.ny}×${v.nz}, ${r.solid.toLocaleString()} solid\n` +
    `height    ${v.y0}..${v.y0 + v.ny - 1}\n` +
    `quads     ${r.quads.toLocaleString()}\n` +
    `voxelize  ${r.tVox.toFixed(0)} ms, mesh ${r.tMesh.toFixed(0)} ms`;
  finish();
}

// ── background job ──────────────────────────────────────────────────────────────────────────────
// One worker per build: starting another build or pressing Cancel terminates it, so a huge
// voxelization can't wedge the page.

let worker = null, rejectJob = null;
function cancelJob() {
  if (!worker) return;
  worker.terminate();
  worker = null;
  $('progress-row').style.display = 'none';
  if (rejectJob) rejectJob(new Error('cancelled'));
  rejectJob = null;
}

function runJob(job) {
  cancelJob();
  const w = worker = new Worker(new URL('./worker.js', import.meta.url), { type: 'module' });
  $('progress-row').style.display = '';
  $('progress').value = 0;
  $('progress-label').textContent = 'loading mesh…';
  stats.textContent = '';
  return new Promise((resolve, reject) => {
    rejectJob = reject;
    w.onmessage = ({ data }) => {
      if (data.type === 'progress') {
        $('progress').value = data.f;
        $('progress-label').textContent = `${data.phase} ${(data.f * 100).toFixed(0)}%`;
        return;
      }
      w.terminate();
      if (worker === w) worker = null;
      rejectJob = null;
      $('progress-row').style.display = 'none';
      if (data.type === 'done') resolve(data);
      else reject(new Error(data.message));
    };
    w.onerror = (e) => {
      w.terminate();
      if (worker === w) worker = null;
      rejectJob = null;
      $('progress-row').style.display = 'none';
      reject(new Error(e.message || 'worker failed'));
    };
    w.postMessage(job);
  });
}

function fit(ext) {
  const r = Math.max(ext[0], ext[2]) / 2;
  controls.target.set(0, ext[1] * 0.3, 0);
  camera.position.set(-r * 1.1, r * 0.7 + ext[1], r * 1.1);
  controls.update();
}

$('build').onclick = build;
$('cancel').onclick = () => { cancelJob(); stats.textContent = 'cancelled'; };
$('model').onchange = () => { selectModel(model()); build(); };
$('mats').onchange = build;
$('viewmode').onchange = build;
$('size').onchange = build;
$('grid').onchange = build;
if (params.get('view')) $('viewmode').value = params.get('view');
$('wire').onchange = () => { if (wire) wire.visible = $('wire').checked; };
$('fog').onchange = applyFog;

selectModel(MODELS.find((m) => m.id === params.get('model')) ?? MODELS[0], params.get('scale'));
await build();
