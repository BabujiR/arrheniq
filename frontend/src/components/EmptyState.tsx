import type { ReactNode } from 'react';
import { Stack, Text, ThemeIcon } from '@mantine/core';

/** Friendly "nothing here yet" message with an optional next step. */
export function EmptyState({ icon, title, hint, action }: { icon: ReactNode; title: string; hint?: string; action?: ReactNode }) {
  return (
    <Stack align="center" gap={6} py="lg" className="arq-fade-in">
      <ThemeIcon size={44} radius="xl" variant="light" color="gray">
        {icon}
      </ThemeIcon>
      <Text fw={500}>{title}</Text>
      {hint && (
        <Text size="sm" c="dimmed" ta="center" maw={360}>
          {hint}
        </Text>
      )}
      {action}
    </Stack>
  );
}
