import { Navigate, Outlet, createRootRoute, createRoute, createRouter } from '@tanstack/react-router';
import { SessionProvider } from './auth/SessionProvider';
import { AppLayout } from './layout/AppLayout';
import { NotFoundPage } from './pages/NotFoundPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { ProductsPage } from './pages/ProductsPage';
import { SignedOutPage } from './pages/SignedOutPage';
import { TestMethodsPage } from './pages/TestMethodsPage';

/**
 * Routes. Everything under the pathless "app" route needs a session (SessionProvider redirects to login);
 * /signed-out does not.
 */
const rootRoute = createRootRoute({ component: Outlet, notFoundComponent: NotFoundPage });

const signedOutRoute = createRoute({ getParentRoute: () => rootRoute, path: '/signed-out', component: SignedOutPage });

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

const indexRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/',
  component: () => <Navigate to="/products" />,
});
const productsRoute = createRoute({ getParentRoute: () => appRoute, path: '/products', component: ProductsPage });
const productRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/products/$productId',
  component: ProductDetailPage,
});
const testMethodsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/test-methods',
  component: TestMethodsPage,
});

const routeTree = rootRoute.addChildren([
  signedOutRoute,
  appRoute.addChildren([indexRoute, productsRoute, productRoute, testMethodsRoute]),
]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
