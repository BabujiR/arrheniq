import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Center, Group, Modal, Stack, Text, ThemeIcon } from '@mantine/core';
import { api, ApiError, onAuthProblem } from '../api/http';
import { keys } from '../api/masterdata';
import type { Me } from '../api/types';
import { can as canCheck } from './permissions';
import { Logo } from '../brand/Logo';
import { IconLock } from '../components/icons';
import { goToReauth, goToWelcome } from './navigation';

interface Session {
  me: Me;
  can: (permission: string) => boolean;
}

const SessionContext = createContext<Session | null>(null);

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error('useSession must be used inside <SessionProvider>');
  }
  return session;
}

/**
 * Loads "who am I" once and reacts to authentication problems from any API call:
 * idle lock → lock screen with Unlock (re-authentication); no/expired/revoked session → welcome screen.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [locked, setLocked] = useState(false);

  useEffect(() => {
    onAuthProblem((code) => {
      if (code === 'ARQ-AUTH-LOCKED') {
        setLocked(true);
      } else if (code === 'ARQ-AUTH-EXPIRED') {
        goToWelcome('expired');
      } else if (code === 'ARQ-AUTH-REVOKED') {
        goToWelcome('revoked');
      } else {
        goToWelcome();
      }
    });
  }, []);

  const me = useQuery({
    queryKey: keys.me,
    queryFn: () => api<Me>('GET', '/api/v1/me'),
    staleTime: 5 * 60_000,
    retry: false,
  });

  if (me.isError && !(me.error instanceof ApiError && me.error.status === 401)) {
    return (
      <Center h="100vh">
        <Stack align="center">
          <Logo size={48} />
          <Text>ArrheniQ is not reachable right now ({me.error.message}).</Text>
          <Button variant="light" onClick={() => void me.refetch()}>
            Try again
          </Button>
        </Stack>
      </Center>
    );
  }

  if (me.isPending || me.isError) {
    // on 401 the listener is already redirecting to the welcome screen
    return (
      <Center h="100vh">
        <Stack align="center" gap="xs">
          <Logo size={64} motion="loop" />
          <Text size="sm" c="dimmed">
            Loading your workspace…
          </Text>
        </Stack>
      </Center>
    );
  }

  const session: Session = { me: me.data, can: (p) => canCheck(me.data, p) };
  return (
    <SessionContext.Provider value={session}>
      {children}
      <LockScreen opened={locked} name={me.data.displayName} />
    </SessionContext.Provider>
  );
}

function LockScreen({ opened, name }: { opened: boolean; name: string }) {
  return (
    <Modal
      opened={opened}
      onClose={() => {}}
      withCloseButton={false}
      closeOnClickOutside={false}
      closeOnEscape={false}
      centered
      title={
        <Group gap="xs">
          <ThemeIcon variant="light" color="yellow" radius="xl">
            <IconLock />
          </ThemeIcon>
          <Text fw={600}>Session locked</Text>
        </Group>
      }
    >
      <Stack>
        <Text size="sm">
          Your session was locked after a period of inactivity. Sign in again as <b>{name}</b> to continue where you
          left off.
        </Text>
        <Button leftSection={<IconLock />} onClick={() => goToReauth('UNLOCK')}>
          Unlock
        </Button>
      </Stack>
    </Modal>
  );
}
