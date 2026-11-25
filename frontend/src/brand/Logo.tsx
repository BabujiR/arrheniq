import { Group, Text } from '@mantine/core';
import './brand.css';

interface Props {
  size?: number;
  /** draw = the curve draws itself once; loop = continuous (loading); still = no motion */
  motion?: 'draw' | 'loop' | 'still';
  withWordmark?: boolean;
}

/**
 * ArrheniQ mark: a flask with a stability (degradation) curve and a measurement point, a nod to the
 * Arrhenius equation that the name comes from. Pure SVG; colours follow the Mantine theme.
 */
export function Logo({ size = 32, motion = 'still', withWordmark = true }: Props) {
  const className = `arq-logo${motion === 'still' ? '' : ` arq-logo--${motion}`}`;
  return (
    <Group gap={8} wrap="nowrap" className={className} aria-label="ArrheniQ">
      <svg width={size} height={size} viewBox="0 0 48 48" role="img" aria-hidden={withWordmark}>
        <title>ArrheniQ</title>
        <g className="arq-logo__flask" fill="none" stroke="var(--mantine-color-indigo-6)" strokeWidth="3"
           strokeLinecap="round" strokeLinejoin="round">
          <path d="M18 5h12" />
          <path d="M20 5v12L8.5 37.5A4 4 0 0 0 12 43h24a4 4 0 0 0 3.5-5.5L28 17V5" />
        </g>
        <path className="arq-logo__curve" d="M17 25c3.5 0 5.5 4.6 9 8.2s5.2 4.8 8.6 4.8" fill="none"
              stroke="var(--mantine-color-teal-5)" strokeWidth="3" strokeLinecap="round" />
        <circle className="arq-logo__dot" cx="34.6" cy="38" r="2.6" fill="var(--mantine-color-teal-6)" />
      </svg>
      {withWordmark && (
        <Text fw={700} size={size >= 40 ? 'xl' : 'lg'} style={{ letterSpacing: 0.2 }}>
          Arrheni
          <Text span inherit c="teal.6">
            Q
          </Text>
        </Text>
      )}
    </Group>
  );
}
