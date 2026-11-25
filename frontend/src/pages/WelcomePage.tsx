import type { ReactNode } from 'react';
import { Alert, Box, Button, Center, Group, Paper, SimpleGrid, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { Logo } from '../brand/Logo';
import { goToLogin } from '../auth/navigation';
import { IconApproved, IconLock, IconProduct, IconSignature } from '../components/icons';
import { CopyrightLine } from '../layout/Footer';

const REASONS: Record<string, { color: string; title: string; text: string }> = {
  expired: {
    color: 'yellow',
    title: 'Your session ended',
    text: 'For your security, sessions end after a while. Sign in again to continue where you left off.',
  },
  revoked: {
    color: 'red',
    title: 'You were signed out',
    text: 'Your access changed or your account was deactivated. If this is unexpected, contact your administrator.',
  },
};

function safeReturnTo(value: string | null): string {
  return value && value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/welcome') ? value : '/';
}

/**
 * Shown when there is no session. ArrheniQ never asks for a password itself: "Sign in" continues to the
 * organisation's identity provider (ADR 0005/0006), which handles passwords, MFA and single sign-on.
 */
export function WelcomePage() {
  const params = new URLSearchParams(window.location.search);
  const reason = REASONS[params.get('reason') ?? ''];
  const returnTo = safeReturnTo(params.get('returnTo'));

  return (
    <Box mih="100vh" className="arq-welcome-bg" style={{ position: 'relative', overflow: 'hidden' }}>
      <BackdropCurve />
      <Center mih="100vh" p="md" style={{ position: 'relative' }}>
        <Stack align="center" gap="lg" w="100%" maw={460}>
          <Paper withBorder radius="lg" shadow="md" p={{ base: 'lg', sm: 40 }} w="100%" className="arq-fade-in">
            <Stack align="center" gap="md">
              <Logo size={64} motion="draw" />
              <Text c="dimmed" size="sm" ta="center">
                Intelligent stability. Predictable shelf life.
              </Text>
              {reason && (
                <Alert color={reason.color} title={reason.title} w="100%" variant="light">
                  {reason.text}
                </Alert>
              )}
              <Title order={3} ta="center" mt="xs">
                Sign in to ArrheniQ
              </Title>
              <Button size="md" fullWidth leftSection={<IconLock />} onClick={() => goToLogin(returnTo)}>
                Sign in
              </Button>
              <Text size="xs" c="dimmed" ta="center">
                You'll continue to your organisation's secure sign-in page. ArrheniQ never sees your password.
              </Text>
            </Stack>
          </Paper>

          <SimpleGrid cols={{ base: 1, xs: 3 }} spacing="sm" w="100%">
            <Feature icon={<IconProduct />} color="arqIndigo" text="Products, tests and studies in one place" />
            <Feature icon={<IconSignature />} color="teal" text="Audit trail and e-signatures (21 CFR Part 11)" />
            <Feature icon={<IconApproved />} color="arqIndigo" text="Shelf life you can defend" />
          </SimpleGrid>

          <CopyrightLine />
        </Stack>
      </Center>
    </Box>
  );
}

function Feature({ icon, color, text }: { icon: ReactNode; color: string; text: string }) {
  return (
    <Group gap="xs" wrap="nowrap" className="arq-fade-in">
      <ThemeIcon variant="light" color={color} radius="md" size={30}>
        {icon}
      </ThemeIcon>
      <Text size="xs" c="dimmed">
        {text}
      </Text>
    </Group>
  );
}

/** Large, faint stability curve behind the card (decoration only). */
function BackdropCurve() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 1200 800"
      preserveAspectRatio="xMidYMid slice"
      style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}
    >
      <path
        className="arq-backdrop-curve"
        d="M-40 140C260 150 380 520 640 640S1020 760 1240 770"
        fill="none"
        stroke="var(--arq-brand-teal)"
        strokeOpacity="0.12"
        strokeWidth="56"
        strokeLinecap="round"
      />
      <circle cx="1140" cy="745" r="26" fill="var(--arq-brand-teal)" fillOpacity="0.14" />
    </svg>
  );
}
