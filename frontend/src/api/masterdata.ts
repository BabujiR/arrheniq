import { api } from './http';
import type {
  Dictionary,
  DictionaryValue,
  DictionaryValueInput,
  Product,
  ProductDetails,
  ProductInput,
  ProtocolTemplate,
  SpecificationDetails,
  SpecificationSummary,
  SpecTestInput,
  SpecVersionDetails,
  TestMethod,
  TestMethodInput,
} from './types';

/** Query keys (TanStack Query) and calls for the master-data API (backend module `masterdata`). */
export const keys = {
  me: ['me'] as const,
  testMethods: (filter: { q?: string; status?: string }) => ['test-methods', filter] as const,
  testMethodsAll: ['test-methods'] as const,
  products: (filter: { q?: string; status?: string }) => ['products', filter] as const,
  productsAll: ['products'] as const,
  product: (id: string) => ['product', id] as const,
  dictionaries: ['dictionaries'] as const,
  dictionaryValues: (code: string) => ['dictionary-values', code] as const,
  protocolTemplates: ['protocol-templates'] as const,
  specifications: (filter: { q?: string; productId?: string }) => ['specifications', filter] as const,
  specificationsAll: ['specifications'] as const,
  specification: (id: string) => ['specification', id] as const,
  specVersion: (id: string) => ['spec-version', id] as const,
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

export const dictionariesApi = {
  lists: () => api<Dictionary[]>('GET', '/api/v1/dictionaries'),
  values: (code: string) => api<DictionaryValue[]>('GET', `/api/v1/dictionaries/${code}/values`),
  create: (code: string, input: DictionaryValueInput) =>
    api<DictionaryValue>('POST', `/api/v1/dictionaries/${code}/values`, input),
  update: (code: string, id: string, expectedVersion: number, data: DictionaryValueInput, reason?: string) =>
    api<DictionaryValue>('PUT', `/api/v1/dictionaries/${code}/values/${id}`, { expectedVersion, reason, data }),
  retire: (code: string, id: string, expectedVersion: number, reason: string) =>
    api<DictionaryValue>('POST', `/api/v1/dictionaries/${code}/values/${id}/retire`, { expectedVersion, reason }),
  reactivate: (code: string, id: string, expectedVersion: number, reason?: string) =>
    api<DictionaryValue>('POST', `/api/v1/dictionaries/${code}/values/${id}/reactivate`, { expectedVersion, reason }),
  remove: (code: string, id: string, expectedVersion: number, reason: string) =>
    api<void>('POST', `/api/v1/dictionaries/${code}/values/${id}/delete`, { expectedVersion, reason }),
};

export const protocolTemplatesApi = {
  list: () => api<ProtocolTemplate[]>('GET', '/api/v1/protocol-templates'),
};

export const specificationsApi = {
  list: (filter: { q?: string; productId?: string }) =>
    api<SpecificationSummary[]>('GET', `/api/v1/specifications${query({ ...filter, limit: '200' })}`),
  get: (id: string) => api<SpecificationDetails>('GET', `/api/v1/specifications/${id}`),
  create: (productId: string, specNumber: string, title: string) =>
    api<SpecificationDetails>('POST', '/api/v1/specifications', { productId, specNumber, title }),
  newVersion: (id: string) => api<SpecVersionDetails>('POST', `/api/v1/specifications/${id}/versions`),
  version: (id: string) => api<SpecVersionDetails>('GET', `/api/v1/spec-versions/${id}`),
  save: (id: string, expectedVersion: number, changeSummary: string | null, tests: SpecTestInput[]) =>
    api<SpecVersionDetails>('PUT', `/api/v1/spec-versions/${id}`, { expectedVersion, data: { changeSummary, tests } }),
  submit: (id: string, expectedVersion: number) =>
    api<SpecVersionDetails>('POST', `/api/v1/spec-versions/${id}/submit`, { expectedVersion }),
  approve: (id: string, expectedVersion: number, comment?: string) =>
    api<SpecVersionDetails>('POST', `/api/v1/spec-versions/${id}/approve`, { expectedVersion, comment }),
  reject: (id: string, expectedVersion: number, reason: string) =>
    api<SpecVersionDetails>('POST', `/api/v1/spec-versions/${id}/reject`, { expectedVersion, reason }),
  retire: (id: string, expectedVersion: number, reason: string) =>
    api<SpecVersionDetails>('POST', `/api/v1/spec-versions/${id}/retire`, { expectedVersion, reason }),
  discard: (id: string, expectedVersion: number, reason: string) =>
    api<void>('POST', `/api/v1/spec-versions/${id}/discard`, { expectedVersion, reason }),
};
