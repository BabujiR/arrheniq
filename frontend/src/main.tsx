import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from '@tanstack/react-router';
import { AllCommunityModule, ModuleRegistry } from 'ag-grid-community';
import { ApiError } from './api/http';
import { router } from './router';
import { theme } from './theme';
import './brand/brand.css';

ModuleRegistry.registerModules([AllCommunityModule]);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      // never retry client errors (4xx: permission, validation, session); retry server errors twice
      retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 2,
    },
  },
});

const container = document.getElementById('root');
if (!container) {
  throw new Error('#root element missing');
}

createRoot(container).render(
  <StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="light">
      <Notifications position="top-right" />
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>
  </StrictMode>,
);
