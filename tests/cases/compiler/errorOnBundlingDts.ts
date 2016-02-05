// @module: amd
// @out: out.js
// @declaration: true

// @filename: folder/m.d.ts
export let x: number;

// @filename: main.ts
import {x} from "./folder/m";
export {x}