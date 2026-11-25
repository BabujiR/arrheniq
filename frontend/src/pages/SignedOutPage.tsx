import { Button, Center, Paper, Stack, Text, Title } from '@mantine/core';
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
      <Paper withBorder p="xl" w={440}>
        <Stack>
          <Title order={3}>ArrheniQ</Title>
          <Text>{MESSAGES[error] ?? 'You are signed out.'}</Text>
          <Button onClick={() => goToLogin('/')}>Sign in</Button>
        </Stack>
      </Paper>
    </Center>
  );
}
