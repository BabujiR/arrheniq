import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Center, Loader, Modal, Stack, Text } from '@mantine/core';
import { api, ApiError, onAuthProblem } from '../api/http';
import { keys } from '../api/masterdata';
import type { Me } from '../api/types';
import { can as canCheck } from './permissions';
import { goToLogin, goToReauth } from './navigation';

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
 * idle lock → lock screen with Unlock (re-authentication); no/expired/revoked session → login.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [locked, setLocked] = useState(false);

  useEffect(() => {
    onAuthProblem((code) => {
      if (code === 'ARQ-AUTH-LOCKED') {
        setLocked(true);
      } else {
        goToLogin();
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
          <Text>ArrheniQ is not reachable right now ({me.error.message}).</Text>
          <Button variant="light" onClick={() => void me.refetch()}>
            Try again
          </Button>
        </Stack>
      </Center>
    );
  }

  if (me.isPending || me.isError) {
    // on 401 the listener is already redirecting to the login page
    return (
      <Center h="100vh">
        <Loader />
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
      title="Session locked"
    >
      <Stack>
        <Text size="sm">
          Your session was locked after a period of inactivity. Sign in again as <b>{name}</b> to continue where you
          left off.
        </Text>
        <Button onClick={() => goToReauth('UNLOCK')}>Unlock</Button>
      </Stack>
    </Modal>
  );
}
