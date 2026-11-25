import type { ReactNode } from 'react';
import { Group, Stack, Text, ThemeIcon, Title } from '@mantine/core';

interface Props {
  icon: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  color?: string;
  actions?: ReactNode;
}

/** Page title with its (animated) icon and one line saying what the page is for. Compact, so content gets the room. */
export function PageHeader({ icon, title, description, color = 'arqIndigo', actions }: Props) {
  const from = color === 'arqIndigo' ? 'arqIndigo.8' : `${color}.7`;
  return (
    <Group justify="space-between" align="center" mb="sm" wrap="wrap" gap="sm">
      <Group gap={12} wrap="nowrap" align="center">
        <ThemeIcon
          size={38}
          radius="md"
          variant="gradient"
          gradient={{ from, to: 'teal.6', deg: 135 }}
          className="arq-pop arq-draw arq-tile"
        >
          {icon}
        </ThemeIcon>
        <Stack gap={0}>
          <Title order={2} fz={20} lh={1.25} fw={650}>
            {title}
          </Title>
          {description && (
            <Text size="xs" c="dimmed" lh={1.4}>
              {description}
            </Text>
          )}
        </Stack>
      </Group>
      {actions && <Group gap="xs">{actions}</Group>}
    </Group>
  );
}
