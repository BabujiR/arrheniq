import { createTheme, type MantineColorsTuple } from '@mantine/core';

/**
 * ArrheniQ theme. Brand: Arrhenius Indigo #2E3192 (shade 8) and Shelf-life Teal #0CA678 (Mantine teal 7);
 * UI text in Inter (self-hosted); Space Grotesk only in the logo wordmark. See docs/brand/README.md.
 */
const arqIndigo: MantineColorsTuple = [
  '#eef0fb', '#dcdff5', '#b5b9ea', '#8b91de', '#686fd4', '#5259ce', '#464dcb', '#383fb3', '#2e3192', '#24267a',
];

export const theme = createTheme({
  colors: { arqIndigo },
  primaryColor: 'arqIndigo',
  primaryShade: { light: 8, dark: 5 },
  defaultRadius: 'sm',
  fontFamily: '"Inter Variable", Inter, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
  fontFamilyMonospace: 'ui-monospace, "Cascadia Code", "SF Mono", Consolas, monospace',
  headings: { fontFamily: '"Inter Variable", Inter, system-ui, sans-serif', fontWeight: '650' },
  components: {
    Card: { defaultProps: { shadow: 'xs' } },
    Button: { defaultProps: { radius: 'md' } },
    Modal: { defaultProps: { radius: 'md', overlayProps: { blur: 2 } } },
  },
});
