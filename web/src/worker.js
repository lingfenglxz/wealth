let runtime;
let initialized;
async function initialize(base) {
  const { loadPyodide } = await import(/* @vite-ignore */ `${base}runtime/pyodide.mjs`);
  runtime = await loadPyodide({ indexURL: `${base}runtime/` });
  for (const name of ['ssq_core.py', 'browser_api.py']) {
    const response = await fetch(base + name);
    if (!response.ok) throw new Error(`算法文件加载失败：${name}`);
    runtime.FS.writeFile(`/home/pyodide/${name}`, await response.text());
  }
  await runtime.runPythonAsync('from browser_api import browser_request');
}
self.onmessage = async ({ data }) => {
  try {
    initialized ??= initialize(data.base);
    await initialized;
    runtime.globals.set('request_json', JSON.stringify(data.payload));
    const result = await runtime.runPythonAsync('browser_request(request_json)');
    self.postMessage({ id: data.id, result: JSON.parse(result) });
  } catch (error) {
    self.postMessage({ id: data.id, error: error.message });
  }
};
