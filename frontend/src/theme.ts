import { createTheme } from '@mantine/core';

/** ArrheniQ theme (placeholder until the brand design system exists). */
export const theme = createTheme({
  primaryColor: 'indigo',
  defaultRadius: 'sm',
  fontFamily: 'Inter, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
  headings: { fontWeight: '650' },
  components: {
    Card: { defaultProps: { shadow: 'xs' } },
    Button: { defaultProps: { radius: 'md' } },
    Modal: { defaultProps: { radius: 'md', overlayProps: { blur: 2 } } },
  },
});
