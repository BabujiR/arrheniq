import { api } from './http';
import type { Product, ProductDetails, ProductInput, TestMethod, TestMethodInput } from './types';

/** Query keys (TanStack Query) and calls for the master-data API (backend module `masterdata`). */
export const keys = {
  me: ['me'] as const,
  testMethods: (filter: { q?: string; status?: string }) => ['test-methods', filter] as const,
  testMethodsAll: ['test-methods'] as const,
  products: (filter: { q?: string; status?: string }) => ['products', filter] as const,
  productsAll: ['products'] as const,
  product: (id: string) => ['product', id] as const,
};

function query(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) {
      search.set(key, value);
    }
  }
  const s = search.toString();
  return s ? `?${s}` : '';
}

export const testMethodsApi = {
  list: (filter: { q?: string; status?: string }) =>
    api<TestMethod[]>('GET', `/api/v1/test-methods${query({ ...filter, limit: '200' })}`),
  create: (input: TestMethodInput) => api<TestMethod>('POST', '/api/v1/test-methods', input),
  update: (id: string, expectedVersion: number, data: TestMethodInput, reason?: string) =>
    api<TestMethod>('PUT', `/api/v1/test-methods/${id}`, { expectedVersion, reason, data }),
  retire: (id: string, expectedVersion: number, reason: string) =>
    api<TestMethod>('POST', `/api/v1/test-methods/${id}/retire`, { expectedVersion, reason }),
};

export const productsApi = {
  list: (filter: { q?: string; status?: string }) =>
    api<Product[]>('GET', `/api/v1/products${query({ ...filter, limit: '200' })}`),
  get: (id: string) => api<ProductDetails>('GET', `/api/v1/products/${id}`),
  create: (input: ProductInput) => api<ProductDetails>('POST', '/api/v1/products', input),
  update: (id: string, expectedVersion: number, data: ProductInput, reason?: string) =>
    api<ProductDetails>('PUT', `/api/v1/products/${id}`, { expectedVersion, reason, data }),
  submit: (id: string, expectedVersion: number) =>
    api<ProductDetails>('POST', `/api/v1/products/${id}/submit`, { expectedVersion }),
  approve: (id: string, expectedVersion: number, comment?: string) =>
    api<ProductDetails>('POST', `/api/v1/products/${id}/approve`, { expectedVersion, comment }),
  reject: (id: string, expectedVersion: number, reason: string) =>
    api<ProductDetails>('POST', `/api/v1/products/${id}/reject`, { expectedVersion, reason }),
  retire: (id: string, expectedVersion: number, reason: string) =>
    api<ProductDetails>('POST', `/api/v1/products/${id}/retire`, { expectedVersion, reason }),
};
