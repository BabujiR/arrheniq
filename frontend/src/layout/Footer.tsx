import { Text } from '@mantine/core';

/** Copyright line; the year follows the clock, so it never goes stale. */
export function CopyrightLine() {
  return (
    <Text size="xs" c="dimmed" ta="center">
      © {new Date().getFullYear()} ArrheniQ. All rights reserved.
    </Text>
  );
}
