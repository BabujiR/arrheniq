import { Outlet, createRootRoute, createRoute, createRouter } from '@tanstack/react-router';
import { SessionProvider } from './auth/SessionProvider';
import { AppLayout } from './layout/AppLayout';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { ProductsPage } from './pages/ProductsPage';
import { SignedOutPage } from './pages/SignedOutPage';
import { WelcomePage } from './pages/WelcomePage';
import { TestMethodsPage } from './pages/TestMethodsPage';

/**
 * Routes. Everything under the pathless "app" route needs a session (SessionProvider sends visitors without one
 * to /welcome); /welcome and /signed-out do not.
 */
const rootRoute = createRootRoute({ component: Outlet, notFoundComponent: NotFoundPage });

const signedOutRoute = createRoute({ getParentRoute: () => rootRoute, path: '/signed-out', component: SignedOutPage });
const welcomeRoute = createRoute({ getParentRoute: () => rootRoute, path: '/welcome', component: WelcomePage });

const appRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: 'app',
  component: () => (
    <SessionProvider>
      <AppLayout>
        <Outlet />
      </AppLayout>
    </SessionProvider>
  ),
});

/** `?new=1` opens the create dialog (quick actions on the home page). */
export interface ListSearch {
  new?: string;
}
const listSearch = (search: Record<string, unknown>): ListSearch => ({
  new: typeof search.new === 'string' ? search.new : undefined,
});

const indexRoute = createRoute({ getParentRoute: () => appRoute, path: '/', component: HomePage });
const productsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/products',
  component: ProductsPage,
  validateSearch: listSearch,
});
const productRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/products/$productId',
  component: ProductDetailPage,
});
const testMethodsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/test-methods',
  component: TestMethodsPage,
  validateSearch: listSearch,
});

const routeTree = rootRoute.addChildren([
  signedOutRoute,
  welcomeRoute,
  appRoute.addChildren([indexRoute, productsRoute, productRoute, testMethodsRoute]),
]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
