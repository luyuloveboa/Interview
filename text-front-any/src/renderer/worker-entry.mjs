// Plain-JS entry point for render worker threads. Worker threads don't inherit the TypeScript
// loader from tests or `tsx`, so we register tsx here and then load the real worker module.
import { register } from 'tsx/esm/api';

register();
const { default: renderTask } = await import('./worker.ts');

export default renderTask;
