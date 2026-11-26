import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearch } from '@tanstack/react-router';
import { Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDebouncedValue } from '@mantine/hooks';
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { ApiError } from '../api/http';
import { keys, productsApi, specificationsApi } from '../api/masterdata';
import type { SpecificationSummary } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { DataGrid } from '../components/DataGrid';
import { toFormErrors } from '../components/formErrors';
import { IconPlus, IconSpec } from '../components/icons';
import { notifyError, notifySuccess } from '../components/notify';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';

const columns: ColDef<SpecificationSummary>[] = [
  { field: 'specNumber', headerName: 'Spec number', maxWidth: 170 },
  { field: 'title', headerName: 'Title', flex: 2 },
  {
    colId: 'product',
    headerName: 'Product',
    flex: 1,
    valueGetter: (p) => (p.data ? `${p.data.productCode} · ${p.data.productName}` : ''),
  },
  {
    colId: 'effective',
    headerName: 'Effective',
    maxWidth: 190,
    valueGetter: (p) =>
      p.data?.effectiveVersionNo ? `v${p.data.effectiveVersionNo} since ${p.data.effectiveFrom ?? ''}` : 'None yet',
  },
  {
    colId: 'open',
    headerName: 'In progress',
    maxWidth: 200,
    cellRenderer: (p: ICellRendererParams<SpecificationSummary>) =>
      p.data?.openStatus ? (
        <Group gap={6} wrap="nowrap" h="100%">
          v{p.data.openVersionNo} <StatusBadge status={p.data.openStatus} />
        </Group>
      ) : null,
  },
];

/** Specifications (URS-MD-003/004): one per product and purpose, versioned, approved by e-signature. */
export function SpecificationsPage() {
  const { can } = useSession();
  const navigate = useNavigate();
  const search = useSearch({ strict: false }) as { new?: string; productId?: string };
  const [q, setQ] = useState('');
  const [debouncedQ] = useDebouncedValue(q, 300);
  const filter = { q: debouncedQ || undefined };
  const specs = useQuery({ queryKey: keys.specifications(filter), queryFn: () => specificationsApi.list(filter) });
  const [createOpen, setCreateOpen] = useState(false);
  const canManage = can('spec.manage');

  useEffect(() => {
    if (search.new === '1' && canManage) {
      setCreateOpen(true);
    }
  }, [search.new, canManage]);

  return (
    <>
      <PageHeader
        icon={<IconSpec size={24} />}
        title="Specifications"
        description="Acceptance criteria per product, built from the test library. Each version is approved by electronic signature."
        actions={
          <>
            <TextInput placeholder="Search number, title or product" value={q} onChange={(e) => setQ(e.currentTarget.value)} />
            {canManage && (
              <Button leftSection={<IconPlus />} onClick={() => setCreateOpen(true)}>
                New specification
              </Button>
            )}
          </>
        }
      />
      <DataGrid
        rows={specs.data}
        columns={columns}
        rowId={(s) => s.id}
        loading={specs.isPending}
        emptyMessage={
          q
            ? 'No specification matches your search.'
            : canManage
              ? 'No specifications yet. Use “New specification” to create the first one.'
              : 'No specifications yet.'
        }
        onRowClick={(s) => void navigate({ to: '/specifications/$specId', params: { specId: s.id } })}
      />
      <CreateSpecification
        opened={createOpen}
        productId={search.productId}
        onClose={() => setCreateOpen(false)}
        onCreated={(id) => void navigate({ to: '/specifications/$specId', params: { specId: id } })}
      />
    </>
  );
}

interface CreateValues {
  productId: string | null;
  specNumber: string;
  title: string;
}

function CreateSpecification({
  opened,
  productId,
  onClose,
  onCreated,
}: {
  opened: boolean;
  productId?: string;
  onClose: () => void;
  onCreated: (id: string) => void;
}) {
  const queryClient = useQueryClient();
  const products = useQuery({ queryKey: keys.products({}), queryFn: () => productsApi.list({}), enabled: opened });
  const form = useForm<CreateValues>({ initialValues: { productId: productId ?? null, specNumber: '', title: '' } });

  useEffect(() => {
    if (opened) {
      form.setValues({ productId: productId ?? null, specNumber: '', title: '' });
      form.clearErrors();
    }
  }, [opened, productId]);

  const create = useMutation({
    mutationFn: (v: CreateValues) => specificationsApi.create(v.productId ?? '', v.specNumber, v.title),
    onSuccess: (d) => {
      void queryClient.invalidateQueries({ queryKey: keys.specificationsAll });
      notifySuccess(`${d.specification.specNumber} created as a draft`);
      onClose();
      onCreated(d.specification.id);
    },
    onError: (error) => {
      if (error instanceof ApiError && (error.status === 400 || error.status === 409) && error.errors.length > 0) {
        form.setErrors(toFormErrors(error.errors));
      } else {
        notifyError(error);
      }
    },
  });

  const options = (products.data ?? [])
    .filter((p) => p.status !== 'RETIRED')
    .map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }));

  return (
    <Modal opened={opened} onClose={onClose} title="New specification" centered>
      <form onSubmit={form.onSubmit((v) => create.mutate(v))}>
        <Stack>
          <Select
            label="Product"
            withAsterisk
            searchable
            data={options}
            placeholder={products.isPending ? 'Loading products…' : 'Choose a product'}
            {...form.getInputProps('productId')}
          />
          <TextInput
            label="Spec number"
            withAsterisk
            description="Your document number, e.g. SPEC-MET-500-STB"
            {...form.getInputProps('specNumber')}
          />
          <TextInput
            label="Title"
            withAsterisk
            placeholder="e.g. Metformin ER 500 mg – release and stability"
            {...form.getInputProps('title')}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Create draft
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
