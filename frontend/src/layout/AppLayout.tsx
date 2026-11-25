import type { ReactNode } from 'react';
import {
  ActionIcon,
  AppShell,
  Avatar,
  Badge,
  Burger,
  Group,
  Menu,
  NavLink,
  Stack,
  Text,
  Tooltip,
  UnstyledButton,
  em,
  useComputedColorScheme,
  useMantineColorScheme,
} from '@mantine/core';
import { useDisclosure, useLocalStorage, useMediaQuery } from '@mantine/hooks';
import { useNavigate, useRouterState } from '@tanstack/react-router';
import { useSession } from '../auth/SessionProvider';
import { logout } from '../auth/navigation';
import { Logo } from '../brand/Logo';
import { IconCollapse, IconExpand, IconHome, IconLogout, IconMoon, IconProduct, IconSun, IconTest } from '../components/icons';
import { CopyrightLine } from './Footer';

interface NavItem {
  to: '/' | '/products' | '/test-methods';
  label: string;
  description: string;
  icon: ReactNode;
  permission?: string;
}

const NAV: NavItem[] = [
  { to: '/', label: 'Home', description: 'Overview and your tasks', icon: <IconHome size={20} /> },
  { to: '/products', label: 'Products', description: 'Drug substances & products', icon: <IconProduct size={20} />, permission: 'masterdata.view' },
  { to: '/test-methods', label: 'Test library', description: 'Methods, units, limits', icon: <IconTest size={20} />, permission: 'masterdata.view' },
];

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join('');
}

/**
 * Header (logo, tenant, theme toggle, user menu), navigation and footer.
 * - Desktop: the menu can be collapsed to an icon rail (labels as tooltips); the choice is remembered.
 * - Phone/small window: the menu is hidden behind the burger and slides in at full width.
 */
export function AppLayout({ children }: { children: ReactNode }) {
  const { me, can } = useSession();
  const navigate = useNavigate();
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const [navOpen, { toggle: toggleNav, close: closeNav }] = useDisclosure(false);
  const { setColorScheme } = useMantineColorScheme();
  const scheme = useComputedColorScheme('light');
  const isMobile = useMediaQuery(`(max-width: ${em(767)})`) ?? false;
  const [collapsedPref, setCollapsed] = useLocalStorage<boolean>({ key: 'arq.nav.collapsed', defaultValue: false });
  const collapsed = collapsedPref && !isMobile;
  const items = NAV.filter((item) => !item.permission || can(item.permission));

  const isActive = (to: NavItem['to']) => (to === '/' ? pathname === '/' : pathname.startsWith(to));

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: collapsed ? 76 : 250, breakpoint: 'sm', collapsed: { mobile: !navOpen } }}
      footer={{ height: 36 }}
      padding="md"
      transitionDuration={180}
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger opened={navOpen} onClick={toggleNav} hiddenFrom="sm" size="sm" aria-label="Menu" />
            <UnstyledButton onClick={() => void navigate({ to: '/' })} aria-label="Home">
              <Logo size={30} />
            </UnstyledButton>
            <Badge variant="light" color="gray" visibleFrom="xs">
              {me.tenant}
            </Badge>
          </Group>
          <Group gap="xs" wrap="nowrap">
            <Tooltip label={scheme === 'dark' ? 'Light mode' : 'Dark mode'}>
              <ActionIcon
                variant="subtle"
                color="gray"
                size="lg"
                radius="xl"
                aria-label="Toggle colour scheme"
                onClick={() => setColorScheme(scheme === 'dark' ? 'light' : 'dark')}
                className="arq-hover-icon"
              >
                {scheme === 'dark' ? <IconSun /> : <IconMoon />}
              </ActionIcon>
            </Tooltip>
            <Menu position="bottom-end" width={240} shadow="md">
              <Menu.Target>
                <UnstyledButton aria-label="Account">
                  <Group gap={8} wrap="nowrap">
                    <Avatar color="arqIndigo" radius="xl" size={34}>
                      {initials(me.displayName)}
                    </Avatar>
                    <Text size="sm" fw={500} visibleFrom="sm">
                      {me.displayName}
                    </Text>
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>Signed in as</Menu.Label>
                <Stack gap={0} px="sm" pb="xs">
                  <Text size="sm" fw={500}>
                    {me.displayName}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {me.username} · {me.tenant}
                  </Text>
                  <Text size="xs" c="dimmed">
                    Sites: {me.allSites ? 'all' : me.sites.map((s) => s.code).join(', ') || 'none'}
                  </Text>
                </Stack>
                <Menu.Divider />
                <Menu.Item leftSection={<IconLogout />} color="red" onClick={logout}>
                  Sign out
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="sm">
        <AppShell.Section grow>
          <Stack gap={4}>
            {items.map((item) => (
              <Tooltip key={item.to} label={item.label} position="right" withArrow disabled={!collapsed}>
                <NavLink
                  label={collapsed ? null : item.label}
                  description={collapsed ? undefined : item.description}
                  aria-label={item.label}
                  leftSection={item.icon}
                  active={isActive(item.to)}
                  className={isActive(item.to) ? 'arq-hover-icon arq-active-icon' : 'arq-hover-icon'}
                  styles={{
                    root: { borderRadius: 'var(--mantine-radius-md)', justifyContent: collapsed ? 'center' : undefined },
                    section: collapsed ? { marginInlineEnd: 0 } : undefined,
                    body: collapsed ? { display: 'none' } : undefined,
                  }}
                  onClick={() => {
                    closeNav();
                    void navigate({ to: item.to });
                  }}
                />
              </Tooltip>
            ))}
          </Stack>
        </AppShell.Section>
        <AppShell.Section visibleFrom="sm">
          <Tooltip label={collapsed ? 'Expand menu' : 'Collapse menu'} position="right" withArrow>
            <NavLink
              label={collapsed ? null : 'Collapse menu'}
              aria-label={collapsed ? 'Expand menu' : 'Collapse menu'}
              leftSection={collapsed ? <IconExpand size={20} /> : <IconCollapse size={20} />}
              className="arq-hover-icon"
              c="dimmed"
              styles={{
                root: { borderRadius: 'var(--mantine-radius-md)', justifyContent: collapsed ? 'center' : undefined },
                section: collapsed ? { marginInlineEnd: 0 } : undefined,
                body: collapsed ? { display: 'none' } : undefined,
              }}
              onClick={() => setCollapsed(!collapsed)}
            />
          </Tooltip>
        </AppShell.Section>
      </AppShell.Navbar>

      {/* flex column: a page's last block (e.g. a grid) can fill the space down to the footer */}
      <AppShell.Main style={{ display: 'flex', flexDirection: 'column' }}>{children}</AppShell.Main>

      <AppShell.Footer>
        <Group h="100%" justify="center" px="md">
          <CopyrightLine />
        </Group>
      </AppShell.Footer>
    </AppShell>
  );
}
