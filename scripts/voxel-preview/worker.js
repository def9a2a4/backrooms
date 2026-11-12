// Background voxelization for the preview: fetch + decode the mesh, voxelize, greedy-mesh, and
// post the geometry buffers back (transferred, not copied). Progress goes out as
// { type: 'progress', phase, f } with f in 0..1.

import { loadMesh, voxelize, voxelizeShell } from './voxelize.js';
import { greedy, placeAltar, plain } from './mesher.js';

onmessage = async ({ data: job }) => {
  try {
    const progress = (phase, scale = 1, offset = 0) => (f) =>
      postMessage({ type: 'progress', phase, f: offset + f * scale });
    const mesh = await loadMesh(job.url);
    if (job.gridMats && mesh.grid && mesh.grid.cells.size === job.gridMats.length) {
      // tuned materials from the preview's sliders, in the grid's key order
      let i = 0;
      for (const k of mesh.grid.cells.keys()) mesh.grid.cells.set(k, job.gridMats[i++]);
    }
    if (job.vmats && job.vmats.length === mesh.vmat.length) mesh.vmat = job.vmats;
    const t0 = performance.now();
    const opts = { ...job.opts, onProgress: progress('voxelizing', 0.85) };
    const v = job.mode === 'shell' ? voxelizeShell(mesh, opts) : voxelize(mesh, opts);
    if (job.altar) placeAltar(v);
    const t1 = performance.now();
    let solid = 0;
    for (const b of v.vol) if (b) solid++;
    const texFor = (id, face) => {
      const t = job.blocks[id] ?? id;
      if (typeof t === 'string') return t;
      return face === 2 ? t.top : face === 3 ? t.bottom : t.side;
    };
    const { buffers, quads } = greedy(job.mats ? v : plain(v), texFor, job.groundY,
      progress('meshing', 0.15, 0.85));
    const t2 = performance.now();
    const transfer = buffers.flatMap((b) => [b.pos.buffer, b.nrm.buffer, b.uv.buffer, b.idx.buffer]);
    postMessage({
      type: 'done', buffers, quads, solid, tVox: t1 - t0, tMesh: t2 - t1,
      volume: { nx: v.nx, ny: v.ny, nz: v.nz, y0: v.y0 },
    }, transfer);
  } catch (e) {
    postMessage({ type: 'error', message: e.message });
  }
};
