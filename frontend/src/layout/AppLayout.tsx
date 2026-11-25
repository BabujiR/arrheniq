import type { ReactNode } from 'react';
import { AppShell, Badge, Button, Group, NavLink, Text, Title } from '@mantine/core';
import { useNavigate, useRouterState } from '@tanstack/react-router';
import { useSession } from '../auth/SessionProvider';
import { logout } from '../auth/navigation';

/** Header (tenant, user, logout) + navigation. Menu entries are shown only with the matching permission. */
export function AppLayout({ children }: { children: ReactNode }) {
  const { me, can } = useSession();
  const navigate = useNavigate();
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  return (
    <AppShell header={{ height: 56 }} navbar={{ width: 220, breakpoint: 'sm' }} padding="md">
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group gap="sm">
            <Title order={4}>ArrheniQ</Title>
            <Badge variant="outline">{me.tenant}</Badge>
          </Group>
          <Group gap="sm">
            <Text size="sm">{me.displayName}</Text>
            <Button size="xs" variant="default" onClick={logout}>
              Sign out
            </Button>
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="xs">
        {can('masterdata.view') && (
          <>
            <NavLink
              label="Products"
              active={pathname.startsWith('/products')}
              onClick={() => void navigate({ to: '/products' })}
            />
            <NavLink
              label="Test library"
              active={pathname.startsWith('/test-methods')}
              onClick={() => void navigate({ to: '/test-methods' })}
            />
          </>
        )}
      </AppShell.Navbar>
      <AppShell.Main>{children}</AppShell.Main>
    </AppShell>
  );
}
