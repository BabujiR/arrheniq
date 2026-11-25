import type { ReactNode } from 'react';
import { Group, Stack, Text, ThemeIcon, Title } from '@mantine/core';

interface Props {
  icon: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  color?: string;
  actions?: ReactNode;
}

/** Page title with its icon and one line saying what the page is for. */
export function PageHeader({ icon, title, description, color = 'arqIndigo', actions }: Props) {
  return (
    <Group justify="space-between" align="flex-start" mb="md" wrap="wrap" gap="md">
      <Group gap="sm" wrap="nowrap" align="flex-start">
        <ThemeIcon size={42} radius="md" variant="light" color={color} className="arq-pop">
          {icon}
        </ThemeIcon>
        <Stack gap={2}>
          <Title order={3}>{title}</Title>
          {description && (
            <Text size="sm" c="dimmed">
              {description}
            </Text>
          )}
        </Stack>
      </Group>
      {actions && <Group gap="sm">{actions}</Group>}
    </Group>
  );
}
