import { createTheme, type MantineColorsTuple } from '@mantine/core';

/**
 * ArrheniQ theme. Brand: Arrhenius Indigo #2E3192 (shade 8) and Shelf-life Teal #0CA678 (Mantine teal 7);
 * headings in Space Grotesk, body in Inter / system UI. See docs/brand/README.md.
 */
const arqIndigo: MantineColorsTuple = [
  '#eef0fb', '#dcdff5', '#b5b9ea', '#8b91de', '#686fd4', '#5259ce', '#464dcb', '#383fb3', '#2e3192', '#24267a',
];

export const theme = createTheme({
  colors: { arqIndigo },
  primaryColor: 'arqIndigo',
  primaryShade: { light: 8, dark: 5 },
  defaultRadius: 'sm',
  fontFamily: 'Inter, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
  headings: { fontFamily: '"Space Grotesk", Inter, system-ui, sans-serif', fontWeight: '600' },
  components: {
    Card: { defaultProps: { shadow: 'xs' } },
    Button: { defaultProps: { radius: 'md' } },
    Modal: { defaultProps: { radius: 'md', overlayProps: { blur: 2 } } },
  },
});
