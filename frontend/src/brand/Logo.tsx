import { Group, Text } from '@mantine/core';
import './brand.css';

interface Props {
  size?: number;
  /** draw = the curve draws itself once; loop = continuous (loading); still = no motion */
  motion?: 'draw' | 'loop' | 'still';
  withWordmark?: boolean;
}

/**
 * ArrheniQ "Q-Curve" mark: a Q whose tail is a stability (degradation) curve that falls steeply, levels off
 * and ends in a measurement point. The knockout behind the tail uses the page background, so the mark works
 * in light and dark mode. Brand sheet and assets: docs/brand/README.md.
 */
export function Logo({ size = 32, motion = 'still', withWordmark = true }: Props) {
  const className = `arq-logo${motion === 'still' ? '' : ` arq-logo--${motion}`}`;
  return (
    <Group gap={Math.round(size / 4)} wrap="nowrap" className={className} aria-label="ArrheniQ">
      <svg width={size} height={size} viewBox="0 0 72 72" role="img" aria-hidden={withWordmark}>
        <title>ArrheniQ</title>
        <circle className="arq-logo__ring" cx="32" cy="32" r="21" fill="none" stroke="var(--arq-brand-indigo)"
                strokeWidth="7.5" />
        <path d="M30 27C35 39 43 49 56.5 52" fill="none" stroke="var(--mantine-color-body)" strokeWidth="13"
              strokeLinecap="round" />
        <path className="arq-logo__curve" d="M30 27C35 39 43 49 56.5 52" fill="none" stroke="var(--arq-brand-teal)"
              strokeWidth="7.5" strokeLinecap="round" />
        <circle className="arq-logo__dot" cx="64.5" cy="53" r="4.6" fill="var(--arq-brand-teal)" />
      </svg>
      {withWordmark && (
        <Text
          ff="var(--arq-font-brand)"
          fw={600}
          fz={Math.max(16, Math.round(size * 0.62))}
          lh={1}
          style={{ letterSpacing: '-0.015em' }}
        >
          Arrheni
          <Text span inherit c="var(--arq-brand-teal)">
            Q
          </Text>
        </Text>
      )}
    </Group>
  );
}
