import { useEffect, useState } from 'react';

function prefersReducedMotion(): boolean {
  return globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

/** Counts from 0 to `target` in ~600 ms (dashboard numbers); jumps straight to the value with reduced motion. */
export function useCountUp(target: number, durationMs = 600): number {
  const [value, setValue] = useState(0);
  useEffect(() => {
    if (prefersReducedMotion() || target === 0) {
      setValue(target);
      return;
    }
    let frame = 0;
    const start = performance.now();
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs);
      setValue(Math.round(target * (1 - (1 - t) ** 3)));
      if (t < 1) {
        frame = requestAnimationFrame(tick);
      }
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [target, durationMs]);
  return value;
}
