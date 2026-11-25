import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Button, Group, Select, TextInput, Title } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { keys, productsApi } from '../api/masterdata';
import type { Product } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { DataGrid } from '../components/DataGrid';
import { label } from '../components/format';
import { StatusBadge } from '../components/StatusBadge';
import { ProductForm } from './ProductForm';

/** Product list (URS-MD-002); only products of sites the user can see are returned by the server. */
export function ProductsPage() {
  const { me, can } = useSession();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<string | null>(null);
  const [debouncedQ] = useDebouncedValue(q, 300);
  const filter = { q: debouncedQ || undefined, status: status ?? undefined };
  const products = useQuery({ queryKey: keys.products(filter), queryFn: () => productsApi.list(filter) });
  const [creating, setCreating] = useState(false);
  const siteCode = new Map(me.sites.map((s) => [s.id, s.code]));

  const columns: ColDef<Product>[] = [
    { field: 'code', headerName: 'Code', maxWidth: 170 },
    { field: 'name', headerName: 'Name', flex: 2 },
    { field: 'productType', headerName: 'Type', valueFormatter: (p) => (p.value ? label(String(p.value)) : '') },
    { field: 'strength', headerName: 'Strength', maxWidth: 140 },
    {
      headerName: 'Expiration',
      valueGetter: (p) =>
        p.data?.expirationStatus === 'ESTABLISHED'
          ? `${p.data.expirationValue} ${label(p.data.expirationUnit ?? '')}`
          : label(p.data?.expirationStatus ?? ''),
    },
    { field: 'siteId', headerName: 'Site', maxWidth: 110, valueFormatter: (p) => siteCode.get(String(p.value)) ?? '' },
    {
      field: 'status',
      headerName: 'Status',
      maxWidth: 150,
      cellRenderer: (p: ICellRendererParams<Product, string>) => (p.value ? <StatusBadge status={p.value} /> : null),
    },
  ];

  return (
    <>
      <Group justify="space-between" mb="md">
        <Title order={3}>Products</Title>
        <Group>
          <TextInput placeholder="Search code or name" value={q} onChange={(e) => setQ(e.currentTarget.value)} />
          <Select
            placeholder="All statuses"
            clearable
            w={160}
            data={['DRAFT', 'IN_REVIEW', 'APPROVED', 'RETIRED'].map((s) => ({ value: s, label: label(s) }))}
            value={status}
            onChange={setStatus}
          />
          {can('product.manage') && <Button onClick={() => setCreating(true)}>New product</Button>}
        </Group>
      </Group>
      <DataGrid
        rows={products.data}
        columns={columns}
        rowId={(p) => p.id}
        loading={products.isPending}
        onRowClick={(p) => void navigate({ to: '/products/$productId', params: { productId: p.id } })}
      />
      <ProductForm
        opened={creating}
        onClose={() => setCreating(false)}
        onSaved={(d) => void navigate({ to: '/products/$productId', params: { productId: d.product.id } })}
      />
    </>
  );
}
