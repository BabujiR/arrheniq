import { Button, Center, Paper, Stack, Text } from '@mantine/core';
import { Logo } from '../brand/Logo';
import { goToLogin } from '../auth/navigation';

const MESSAGES: Record<string, string> = {
  login: 'Sign-in was refused. If you think you should have access to ArrheniQ, contact your administrator.',
  reauth:
    'The re-authentication did not confirm the same user, so you were signed out for safety. Sign in again to continue.',
};

/** Shown after logout or a refused login (backend `arrheniq.security.signed-out-path`). Needs no session. */
export function SignedOutPage() {
  const error = new URLSearchParams(window.location.search).get('error') ?? '';
  return (
    <Center h="100vh">
      <Paper withBorder radius="md" shadow="sm" p="xl" w={440} className="arq-fade-in">
        <Stack align="center" ta="center">
          <Logo size={56} motion="draw" />
          <Text c="dimmed" size="sm">
            Intelligent stability. Predictable shelf life.
          </Text>
          <Text>{MESSAGES[error] ?? 'You are signed out.'}</Text>
          <Button fullWidth onClick={() => goToLogin('/')}>
            Sign in
          </Button>
        </Stack>
      </Paper>
    </Center>
  );
}
