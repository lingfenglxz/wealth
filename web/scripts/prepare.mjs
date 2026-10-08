import { cp, mkdir } from 'node:fs/promises';
await mkdir('public/runtime', { recursive: true });
await cp('node_modules/pyodide', 'public/runtime', { recursive: true });
