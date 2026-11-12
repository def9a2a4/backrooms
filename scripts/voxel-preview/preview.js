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
  if (!obj.userData.cached) obj.traverse((o) => o.geometry && o.geometry.dispose());
}

const model = () => MODELS.find((m) => m.id === $('model').value);

function selectModel(m, scale) {
  setupTune(m);
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

// "@name": a published mesh, resolved through the plugin's manifest to meshes/<file>
const MANIFEST = await (await fetch('/src/main/resources/meshes.json')).json();
const resolve = (u) => (u.startsWith('@') ? `/meshes/${MANIFEST.meshes[u.slice(1)].file}` : u);
const meshUrl = (m) => resolve(m.sizes ? m.sizes[$('size').value] : m.mesh);

async function getMesh(m) {
  const url = meshUrl(m);
  if (!meshes.has(url)) meshes.set(url, await loadMesh(url));
  const mesh = meshes.get(url);
  // tuned materials replace the file's surface grid (same cells, same order) and the per-vertex
  // fallback materials (re-derived from the tuned cells)
  if (tuned?.mats && tuned.model === m.id && mesh.grid && mesh.grid.cells.size === tuned.mats.length) {
    if (mesh.grid.tunedFrom !== tuned) {
      const cells = new Map();
      let i = 0;
      for (const k of mesh.grid.cells.keys()) cells.set(k, tuned.mats[i++]);
      mesh.grid = { ...mesh.grid, cells, tunedFrom: tuned };
      mesh.vmatBuilt ??= mesh.vmat;
      if (tuned.vmats && tuned.vmats.length === mesh.vmatBuilt.length) {
        mesh.vmat = mesh.vmatBuilt.map((m0, v) => (tuned.vmats[v] >= 0 ? tuned.vmats[v] : m0));
      }
    }
  }
  return mesh;
}

// ── tuning ──────────────────────────────────────────────────────────────────────────────────────
// For scans with a cells export (build_scan.py --cells-only), the block matching is redone live
// in tune-worker.js with the sliders' values; the result replaces the 16x mesh's surface grid.
// "Copy settings" gives the MODELS[...]["tune"] entry for the real build.

const TUNE_KEYS = ['blur', 'spread', 'pattern', 'majority', 'height', 'slope', 'brightness'];
const TUNE_ZERO = { height: 0, slope: 0, brightness: 0 };   // exports made before these knobs existed
let tuneWorker = null, tuneModel = null, tuneDefaults = null, tuned = null, tuneRun = 0, tuneTimer = null;

function tuneValues() {
  return Object.fromEntries(TUNE_KEYS.map((k) => [k, Number($(`t-${k}`).value)]));
}

function showTune(values) {
  for (const k of TUNE_KEYS) {
    $(`t-${k}`).value = values[k];
    $(`o-${k}`).textContent = values[k];
  }
}

function tuneProgress(label, f) {
  const bar = $('tune-progress');
  if (f == null) {
    bar.style.display = 'none';
    $('tune-status').textContent = label;
    return;
  }
  bar.style.display = '';
  bar.value = f;
  $('tune-status').textContent = `${label} ${(f * 100).toFixed(0)}%`;
}

function setupTune(m) {
  $('tune').style.display = m.cells ? '' : 'none';
  if (!m.cells || tuneModel === m.id) return;
  if (tuneWorker) tuneWorker.terminate();
  tuneModel = m.id;
  tuned = null;
  tuneDefaults = null;
  tuneProgress('loading cells', 0);
  tuneWorker = new Worker(new URL('./tune-worker.js', import.meta.url), { type: 'module' });
  tuneWorker.onmessage = ({ data }) => {
    if (data.type === 'loaded') {
      tuneDefaults = { ...TUNE_ZERO, ...data.tune };
      const fromUrl = Object.fromEntries(TUNE_KEYS.filter((k) => params.has(k)).map((k) => [k, Number(params.get(k))]));
      showTune({ ...tuneDefaults, ...fromUrl });
      tuneProgress(`${data.count.toLocaleString()} cells`);
      sendVerts(m).then(() => {
        if (Object.keys(fromUrl).length || ['colors', 'textured'].includes($('viewmode').value)) scheduleTune(0);
      });
    } else if (data.type === 'progress') {
      tuneProgress(data.phase, data.f);
    } else if (data.type === 'done') {
      if (data.id !== tuneRun) return;
      tuned = { model: m.id, mats: data.mats, vmats: data.vmats, colors: data.colors,
        centers: data.centers, glass: data.glass, norm: data.norm, values: tuneValues() };
      if ($('viewmode').value === 'textured') {
        // only the shader's uniforms change
        const t = textured.get(m.id);
        if (t && !(t instanceof Promise)) setFilters(t, m);
        tuneProgress('filtered');
        return;
      }
      tuneProgress('redrawing', 1);
      // let the bar paint before the redraw blocks the page
      setTimeout(() => build().then(async () => {
        // the mesh's grid must be the one the cells were exported from, or the tuning is dropped
        const g = (await getMesh(m)).grid;
        tuneProgress(!tuned.mats ? 'filtered' : g?.tunedFrom === tuned ? 'matched'
          : `not applied: this mesh has ${g ? g.cells.size.toLocaleString() : 'no'} grid cells, the export ${data.mats.length.toLocaleString()}`);
      }), 30);
    } else if (data.type === 'error') {
      tuneProgress(`no cells: ${data.message}`);
    }
  };
  tuneWorker.postMessage({ type: 'load', url: m.cells });
}

/** Give the tuning worker the 16x mesh's vertices, so it can re-derive vertex materials too. */
async function sendVerts(m) {
  const url = resolve(m.sizes ? Object.values(m.sizes)[0] : m.mesh);
  if (!meshes.has(url)) meshes.set(url, await loadMesh(url));
  tuneWorker.postMessage({ type: 'verts', verts: meshes.get(url).verts.slice() });
}

function scheduleTune(delay = 400) {
  showTune(tuneValues());
  clearTimeout(tuneTimer);
  tuneProgress('waiting', 0);
  tuneTimer = setTimeout(() => {
    tuneRun++;
    // the color views don't need blocks: skip the matching, majority filter and vertices
    const colorsOnly = ['colors', 'textured'].includes($('viewmode').value);
    tuneWorker.postMessage({ type: 'run', id: tuneRun, tune: tuneValues(), colorsOnly });
  }, delay);
}

for (const k of TUNE_KEYS) $(`t-${k}`).oninput = () => scheduleTune();
$('t-reset').onclick = () => { if (tuneDefaults) { showTune(tuneDefaults); scheduleTune(0); } };
$('t-copy').onclick = async () => {
  const text = `"tune": ${JSON.stringify(tuneValues()).replace(/,/g, ', ').replace(/:/g, ': ')},`;
  try { await navigator.clipboard.writeText(text); } catch { /* no clipboard: still shown */ }
  $('tune-status').textContent = 'copied';
  stats.textContent = `${model().id} → build_scan.py MODELS:\n${text}`;
};

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

function colorPoints(t, scale, baseY, cell) {
  const n = t.colors.length / 3;
  const pos = new Float32Array(n * 3), col = new Float32Array(n * 3);
  for (let i = 0; i < n; i++) {
    pos[i * 3] = t.centers[i * 3] * scale;
    pos[i * 3 + 1] = (t.centers[i * 3 + 1] - baseY) * scale;
    pos[i * 3 + 2] = t.centers[i * 3 + 2] * scale;
    const g = t.glass[i] ? 0.85 : 0;
    const glassTint = [0.0, 0.85, 1.0];   // strong cyan: nothing scanned looks like it
    for (let c = 0; c < 3; c++) {
      // sRGB bytes -> linear, as three.js expects for vertex colors
      const v = t.colors[i * 3 + c] / 255;
      const lin = v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
      col[i * 3 + c] = lin * (1 - g) + glassTint[c] * g;
    }
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  geo.setAttribute('color', new THREE.BufferAttribute(col, 3));
  // three.js sizes attenuated points as size * (viewport height / 2) / depth, which is the
  // world size times 1 / tan(fov / 2): one cell wide (plus a little, so there are no gaps)
  const size = cell * scale * 1.2 / Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
  return new THREE.Points(geo, new THREE.PointsMaterial({ size, vertexColors: true }));
}

// ── textured scan ────────────────────────────────────────────────────────────────────────────────
// The scan itself with its photo textures (shrunk; build_scan.py --textured), and the matching's
// color filters applied per pixel in the shader: shading, contrast, brightness and white balance
// exactly as tune-worker.js does them per cell (its `norm` mapping), blur approximated by
// sampling a coarser mip level (a box of about the blur's width in texels, which also bleeds
// across the texture atlas' seams).

const textured = new Map();   // model id -> {group, info, tris, uniforms} (or the Promise loading it)

async function getTextured(m) {
  if (!textured.has(m.id)) {
    textured.set(m.id, (async () => {
      const base = `/.temp/textured/${m.id}`;
      const res = await fetch(`${base}.json`);
      if (!res.ok) throw new Error(`${base}.json: ${res.status}`);
      const info = await res.json();
      const { GLTFLoader } = await import('three/addons/loaders/GLTFLoader.js');
      const gltf = await new GLTFLoader().loadAsync(`${base}.glb`, (e) => {
        if (e.total) stats.textContent = `loading textured scan ${(100 * e.loaded / e.total).toFixed(0)}%`;
      });
      const uniforms = {
        uFilter: { value: false }, uShell: { value: m.mode === 'shell' },
        uLod: { value: 0 }, uHeight: { value: 0 }, uSlope: { value: 0 }, uBright: { value: 0 },
        uScaleL: { value: 1 }, uPm0: { value: 0 }, uFm: { value: new THREE.Vector3() },
        uCast: { value: 0 }, uChroma: { value: 1 }, uDead: { value: 0 },
        uOriginY: { value: 0 }, uSpanY: { value: 1 },
      };
      let tris = 0;
      gltf.scene.traverse((o) => {
        if (!o.isMesh) return;
        o.geometry.computeVertexNormals();
        tris += (o.geometry.index ? o.geometry.index.count : o.geometry.attributes.position.count) / 3;
        const map = o.material.map;
        o.material = new THREE.ShaderMaterial({
          glslVersion: THREE.GLSL3, vertexShader: SCAN_VERT, fragmentShader: SCAN_FRAG,
          side: THREE.DoubleSide,
          uniforms: { ...uniforms, uMap: { value: map }, uMapSize: { value: map ? map.image.width : 1 } },
        });
      });
      const group = new THREE.Group();
      group.add(gltf.scene);
      group.userData.cached = true;
      return { group, info, tris, uniforms };
    })());
  }
  const t = await textured.get(m.id).catch((e) => { textured.delete(m.id); throw e; });
  textured.set(m.id, t);
  return t;
}

/** The sliders' filters into the textured scan's shader (the mapping from the last tuning run). */
function setFilters(t, m) {
  const u = t.uniforms, v = tuneValues(), n = tuned?.model === m.id ? tuned.norm : null;
  u.uFilter.value = $('t-filters').checked && !!n;
  if (!n) return;
  // blur: a mip level about 2 * radius wide (the radius is in cells of n.cell meters)
  u.uLod.value = v.blur > 0 ? Math.log2(1 + 2 * v.blur * n.cell * t.info.texels_per_meter) : 0;
  u.uHeight.value = v.height; u.uSlope.value = v.slope; u.uBright.value = v.brightness;
  u.uScaleL.value = n.scaleL; u.uPm0.value = n.pm0; u.uFm.value.set(...n.fm);
  u.uCast.value = n.cast; u.uChroma.value = n.chroma; u.uDead.value = n.deadzone;
  u.uOriginY.value = n.originY; u.uSpanY.value = n.cell * Math.max(n.dimsY - 1, 1);
}

const SCAN_VERT = /* glsl */ `
  out vec2 vUv;
  out vec3 vMesh, vN;
  void main() {
    vUv = uv;
    vMesh = position;
    vN = normal;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }`;

const SCAN_FRAG = /* glsl */ `
  uniform sampler2D uMap;
  uniform float uMapSize, uLod, uHeight, uSlope, uBright, uScaleL, uPm0, uCast, uChroma, uDead;
  uniform float uOriginY, uSpanY;
  uniform vec3 uFm;
  uniform bool uFilter, uShell;
  in vec2 vUv;
  in vec3 vMesh, vN;
  out vec4 fragColor;
  vec3 toLab(vec3 c) {
    vec3 lms = vec3(0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b,
                    0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b,
                    0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b);
    lms = pow(max(lms, 0.0), vec3(1.0 / 3.0));
    return vec3(0.2104542553 * lms.x + 0.7936177850 * lms.y - 0.0040720468 * lms.z,
                1.9779984951 * lms.x - 2.4285922050 * lms.y + 0.4505937099 * lms.z,
                0.0259040371 * lms.x + 0.7827717662 * lms.y - 0.8086757660 * lms.z);
  }
  vec3 fromLab(vec3 c) {
    vec3 lms = vec3(c.x + 0.3963377774 * c.y + 0.2158037573 * c.z,
                    c.x - 0.1055613458 * c.y - 0.0638541728 * c.z,
                    c.x - 0.0894841775 * c.y - 1.2914855480 * c.z);
    lms = lms * lms * lms;
    return vec3(4.0767416621 * lms.x - 3.3077115913 * lms.y + 0.2309699292 * lms.z,
                -1.2684380046 * lms.x + 2.6097574011 * lms.y - 0.3413193965 * lms.z,
                -0.0041960863 * lms.x - 0.7034186147 * lms.y + 1.7076147010 * lms.z);
  }
  vec3 toSrgb(vec3 v) {
    v = clamp(v, 0.0, 1.0);
    return mix(12.92 * v, 1.055 * pow(v, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, v));
  }
  void main() {
    // the blur's mip level, or the usual one if that's coarser (far away)
    vec2 px = vUv * uMapSize;
    float lod = max(uFilter ? uLod : 0.0, log2(max(max(length(dFdx(px)), length(dFdy(px))), 1e-6)));
    vec3 lin = textureLod(uMap, vUv, lod).rgb;      // sRGB texture: sampled as linear
    vec3 c = toSrgb(lin);
    if (uFilter) {
      vec3 lab = toLab(lin);
      vec3 n = normalize(vN);
      float up = clamp(uShell ? abs(n.y) : n.y, 0.0, 1.0);
      float shade = (uHeight * ((vMesh.y - uOriginY) / uSpanY - 0.5) + uSlope * (up - 0.5)) * 0.6;
      float L = (lab.x - uFm.x) * uScaleL + uPm0 + shade + uBright * 0.15;
      vec2 ab = (lab.yz - uFm.yz) * uChroma + uFm.yz * (1.0 - uCast);
      if (uDead > 0.0) { float k = length(ab); ab *= max(k - uDead, 0.0) / max(k, 1e-9); }
      c = toSrgb(fromLab(vec3(L, ab)));
    }
    // mild light so the shapes read, without changing the colors much
    vec3 fn = normalize(cross(dFdx(vMesh), dFdy(vMesh)));
    float l = 0.8 + 0.2 * abs(dot(fn, normalize(vec3(-0.5, 1.0, 0.35))));
    fragColor = vec4(c * l, 1.0);
  }`;

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
    const cam = first && params.get('cam')?.split(',').map(Number);
    if (cam?.length === 6 && cam.every(Number.isFinite)) {
      camera.position.set(cam[0], cam[1], cam[2]);
      controls.target.set(cam[3], cam[4], cam[5]);
      controls.update();
    } else if (first || $('refit').checked) fit(ext);
    first = false;
    const q = new URLSearchParams(location.search);
    q.set('model', m.id);
    q.set('scale', scale);
    q.set('view', $('viewmode').value);
    if (m.sizes) q.set('size', $('size').value); else q.delete('size');
    for (const k of TUNE_KEYS) {
      if (tuned && tuned.model === m.id) q.set(k, tuned.values[k]); else q.delete(k);
    }
    history.replaceState(null, '', `?${q}`);
  };
  $('t-filters-row').style.display = $('viewmode').value === 'textured' ? '' : 'none';
  if ($('viewmode').value === 'textured') {
    cancelJob();
    clear(voxels);
    clear(wire);
    if (!m.cells) {
      stats.textContent = 'no textured export for this model';
      return;
    }
    let t;
    try {
      t = await getTextured(m);
    } catch (e) {
      stats.textContent = `${e.message}\nexport it: uv run scripts/scans/build_scan.py ${m.id} --textured`;
      return;
    }
    if (model() !== m || $('viewmode').value !== 'textured') return;   // changed while loading
    const baseY = m.mode === 'shell' ? mesh.lo[1] : mesh.groundRef;
    t.group.scale.setScalar(scale);
    t.group.position.set(0, -baseY * scale, 0);
    voxels = t.group;
    scene.add(voxels);
    setFilters(t, m);
    if (tuneDefaults && !(tuned?.model === m.id && tuned.norm)) scheduleTune(0);
    stats.textContent =
      `scan      ${t.tris.toLocaleString()} tris, textures ${t.info.texture}², ` +
      `${(t.info.texels_per_meter * (mesh.grid?.cell ?? 0.0625)).toFixed(1)} texels per block\n` +
      `size      ${ext.map((e) => Math.round(e)).join(' × ')} blocks (x y z)`;
    finish();
    return;
  }
  if ($('viewmode').value === 'colors') {
    // the cells in the colors the block matching sees (after blur, shading, contrast and
    // brightness), one square per cell; glass cells cyan
    cancelJob();
    clear(voxels);
    clear(wire);
    if (!m.cells) {
      stats.textContent = 'no cells export for this model\n(build_scan.py --cells-only)';
      return;
    }
    if (!tuned || tuned.model !== m.id) {
      // drawn once the matching is done (started here, or when the cells finish loading)
      stats.textContent = 'matching…';
      if (tuneDefaults) scheduleTune(0);
      return;
    }
    const baseY = m.mode === 'shell' ? mesh.lo[1] : mesh.groundRef;
    voxels = colorPoints(tuned, scale, baseY, mesh.grid?.cell ?? 0.0625);
    scene.add(voxels);
    stats.textContent =
      `cells     ${(tuned.colors.length / 3).toLocaleString()}, as matched\n` +
      `size      ${ext.map((e) => Math.round(e)).join(' × ')} blocks (x y z)`;
    finish();
    return;
  }
  // the block views need blocks: redo a colors-only run in full
  if (tuned && tuned.model === m.id && !tuned.mats) scheduleTune(0);   // blocks wanted now
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
      altar: !!m.altar, mats: $('mats').checked, blocks: BLOCKS, groundY: GROUND_Y,
      gridMats: mesh.grid?.tunedFrom ? mesh.grid.tunedFrom.mats.slice() : null,
      vmats: mesh.grid?.tunedFrom ? Uint8Array.from(mesh.vmat) : null });
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

// the view goes in the URL too (cam=position,target), so a link shows the same spot
controls.addEventListener('end', () => {
  const q = new URLSearchParams(location.search);
  q.set('cam', [...camera.position.toArray(), ...controls.target.toArray()].map((v) => v.toFixed(0)).join(','));
  history.replaceState(null, '', `?${q}`);
});

$('t-filters').onchange = () => {
  const t = textured.get(model().id);
  if (t && !(t instanceof Promise)) setFilters(t, model());
};
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
