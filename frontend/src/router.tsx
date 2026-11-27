import { Outlet, createRootRoute, createRoute, createRouter } from '@tanstack/react-router';
import { SessionProvider } from './auth/SessionProvider';
import { AppLayout } from './layout/AppLayout';
import { HomePage } from './pages/HomePage';
import { ListsPage } from './pages/ListsPage';
import { ProtocolDetailPage } from './pages/ProtocolDetailPage';
import { ProtocolsPage } from './pages/ProtocolsPage';
import { StudiesPage } from './pages/StudiesPage';
import { StudyDetailPage } from './pages/StudyDetailPage';
import { StudyLoginPage } from './pages/StudyLoginPage';
import { SpecificationDetailPage } from './pages/SpecificationDetailPage';
import { SpecificationsPage } from './pages/SpecificationsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { str } from './searchParams';
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

const listsRoute = createRoute({ getParentRoute: () => appRoute, path: '/lists', component: ListsPage });

/** `?new=1&productId=…` opens the create dialog for a product (from the product page). */
const specificationsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/specifications',
  component: SpecificationsPage,
  validateSearch: (search: Record<string, unknown>): { new?: string; productId?: string } => ({
    new: str(search.new),
    productId: str(search.productId),
  }),
});
/** `?v=<version id>` selects a version; `&signing=1` marks the return from re-authentication. */
const specificationRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/specifications/$specId',
  component: SpecificationDetailPage,
  validateSearch: (search: Record<string, unknown>): { v?: string; signing?: string } => ({
    v: str(search.v),
    signing: str(search.signing),
  }),
});

const protocolsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/protocols',
  component: ProtocolsPage,
  validateSearch: (search: Record<string, unknown>): { new?: string; productId?: string } => ({
    new: str(search.new),
    productId: str(search.productId),
  }),
});
const protocolRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/protocols/$protocolId',
  component: ProtocolDetailPage,
  validateSearch: (search: Record<string, unknown>): { v?: string; signing?: string } => ({
    v: str(search.v),
    signing: str(search.signing),
  }),
});

const studiesRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/studies',
  component: StudiesPage,
});
const studyLoginRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/studies/new',
  component: StudyLoginPage,
  validateSearch: (search: Record<string, unknown>): { productId?: string } => ({ productId: str(search.productId) }),
});
const studyRoute = createRoute({
  getParentRoute: () => appRoute,
  path: '/studies/$studyId',
  component: StudyDetailPage,
  validateSearch: (search: Record<string, unknown>): { signing?: string } => ({ signing: str(search.signing) }),
});

const routeTree = rootRoute.addChildren([
  signedOutRoute,
  welcomeRoute,
  appRoute.addChildren([
    indexRoute,
    productsRoute,
    productRoute,
    specificationsRoute,
    specificationRoute,
    protocolsRoute,
    protocolRoute,
    studiesRoute,
    studyLoginRoute,
    studyRoute,
    testMethodsRoute,
    listsRoute,
  ]),
]);

export const router = createRouter({ routeTree });

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
