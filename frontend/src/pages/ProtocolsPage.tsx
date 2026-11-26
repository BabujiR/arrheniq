import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearch } from '@tanstack/react-router';
import { Button, Group, Modal, Select, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useDebouncedValue } from '@mantine/hooks';
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { ApiError } from '../api/http';
import { keys, productsApi, protocolsApi, protocolTemplatesApi, specificationsApi } from '../api/masterdata';
import type { ProtocolSummary } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { DataGrid } from '../components/DataGrid';
import { label } from '../components/format';
import { toFormErrors } from '../components/formErrors';
import { IconCalendar, IconPlus } from '../components/icons';
import { notifyError, notifySuccess } from '../components/notify';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';

const columns: ColDef<ProtocolSummary>[] = [
  { field: 'protocolNumber', headerName: 'Protocol', maxWidth: 170 },
  { field: 'name', headerName: 'Name', flex: 2 },
  {
    colId: 'product',
    headerName: 'Product',
    flex: 1,
    valueGetter: (p) => (p.data ? `${p.data.productCode} · ${p.data.productName}` : ''),
  },
  { colId: 'type', headerName: 'Study type', maxWidth: 160, valueGetter: (p) => label(p.data?.studyType ?? '') },
  {
    colId: 'approved',
    headerName: 'Approved',
    maxWidth: 190,
    valueGetter: (p) =>
      p.data?.approvedVersionNo ? `v${p.data.approvedVersionNo} since ${p.data.approvedFrom ?? ''}` : 'None yet',
  },
  {
    colId: 'open',
    headerName: 'In progress',
    maxWidth: 200,
    cellRenderer: (p: ICellRendererParams<ProtocolSummary>) =>
      p.data?.openStatus ? (
        <Group gap={6} wrap="nowrap" h="100%">
          v{p.data.openVersionNo} <StatusBadge status={p.data.openStatus} />
        </Group>
      ) : null,
  },
];

/** Stability protocols (URS-PRO-001..007): time points, test grid, planned units; versioned and approved. */
export function ProtocolsPage() {
  const { can } = useSession();
  const navigate = useNavigate();
  const search = useSearch({ strict: false }) as { new?: string; productId?: string };
  const [q, setQ] = useState('');
  const [debouncedQ] = useDebouncedValue(q, 300);
  const filter = { q: debouncedQ || undefined };
  const protocols = useQuery({ queryKey: keys.protocols(filter), queryFn: () => protocolsApi.list(filter) });
  const [createOpen, setCreateOpen] = useState(false);
  const canManage = can('protocol.manage');

  useEffect(() => {
    if (search.new === '1' && canManage) {
      setCreateOpen(true);
    }
  }, [search.new, canManage]);

  return (
    <>
      <PageHeader
        icon={<IconCalendar size={24} />}
        title="Protocols"
        description="When each test runs and how many units are pulled. Only approved versions can be used for studies."
        actions={
          <>
            <TextInput placeholder="Search number, name or product" value={q} onChange={(e) => setQ(e.currentTarget.value)} />
            {canManage && (
              <Button leftSection={<IconPlus />} onClick={() => setCreateOpen(true)}>
                New protocol
              </Button>
            )}
          </>
        }
      />
      <DataGrid
        rows={protocols.data}
        columns={columns}
        rowId={(p) => p.id}
        loading={protocols.isPending}
        emptyMessage={
          q
            ? 'No protocol matches your search.'
            : canManage
              ? 'No protocols yet. Use “New protocol” and start from an ICH template.'
              : 'No protocols yet.'
        }
        onRowClick={(p) => void navigate({ to: '/protocols/$protocolId', params: { protocolId: p.id } })}
      />
      <CreateProtocol
        opened={createOpen}
        productId={search.productId}
        onClose={() => setCreateOpen(false)}
        onCreated={(id) => void navigate({ to: '/protocols/$protocolId', params: { protocolId: id } })}
      />
    </>
  );
}

interface CreateValues {
  productId: string | null;
  protocolNumber: string;
  name: string;
  templateCode: string | null;
  specificationId: string | null;
}

function CreateProtocol({
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
  const blank: CreateValues = { productId: productId ?? null, protocolNumber: '', name: '', templateCode: null, specificationId: null };
  const form = useForm<CreateValues>({ initialValues: blank });
  const values = form.getValues();
  const products = useQuery({ queryKey: keys.products({}), queryFn: () => productsApi.list({}), enabled: opened });
  const templates = useQuery({ queryKey: keys.protocolTemplates, queryFn: protocolTemplatesApi.list, enabled: opened });
  const specs = useQuery({
    queryKey: keys.specifications({ productId: values.productId ?? '' }),
    queryFn: () => specificationsApi.list({ productId: values.productId ?? '' }),
    enabled: opened && !!values.productId,
  });

  useEffect(() => {
    if (opened) {
      form.setValues(blank);
      form.clearErrors();
    }
  }, [opened, productId]);

  const create = useMutation({
    mutationFn: async (v: CreateValues) => {
      // the protocol takes its tests from the specification's effective version (or its version in progress)
      let specVersionId: string | null = null;
      if (v.specificationId) {
        const d = await specificationsApi.get(v.specificationId);
        specVersionId = d.versions.find((x) => x.status === 'EFFECTIVE')?.id ?? d.versions[0]?.id ?? null;
      }
      return protocolsApi.create({
        productId: v.productId ?? '',
        protocolNumber: v.protocolNumber,
        name: v.name,
        templateCode: v.templateCode,
        specVersionId,
      });
    },
    onSuccess: (d) => {
      void queryClient.invalidateQueries({ queryKey: keys.protocolsAll });
      notifySuccess(`${d.protocol.protocolNumber} created as a draft`);
      onClose();
      onCreated(d.protocol.id);
    },
    onError: (error) => {
      if (error instanceof ApiError && (error.status === 400 || error.status === 409) && error.errors.length > 0) {
        form.setErrors(toFormErrors(error.errors));
      } else {
        notifyError(error);
      }
    },
  });

  const productOptions = (products.data ?? [])
    .filter((p) => p.status !== 'RETIRED')
    .map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }));
  const templateOptions = (templates.data ?? []).map((t) => ({
    value: t.code,
    label: `${t.name} (${t.timepoints.join(', ')} ${t.timepointUnit === 'MONTH' ? 'M' : t.timepointUnit === 'WEEK' ? 'W' : 'D'})`,
  }));
  const specOptions = (specs.data ?? []).map((s) => ({
    value: s.id,
    label: `${s.specNumber} · ${s.title}${s.effectiveVersionNo ? ` (v${s.effectiveVersionNo})` : ' (not effective yet)'}`,
  }));

  return (
    <Modal opened={opened} onClose={onClose} title="New protocol" centered size="lg">
      <form onSubmit={form.onSubmit((v) => create.mutate(v))}>
        <Stack>
          <Select label="Product" withAsterisk searchable data={productOptions}
                  placeholder={products.isPending ? 'Loading products…' : 'Choose a product'}
                  {...form.getInputProps('productId')}
                  onChange={(v) => {
                    form.setFieldValue('productId', v);
                    form.setFieldValue('specificationId', null);
                  }} />
          <Group grow align="flex-start">
            <TextInput label="Protocol number" withAsterisk description="Your document number, e.g. PRT-MET-500-LT"
                       {...form.getInputProps('protocolNumber')} />
            <TextInput label="Name" withAsterisk description="e.g. Long-term 25 °C/60 % RH, 36 months"
                       {...form.getInputProps('name')} />
          </Group>
          <Select label="Start from an ICH template" description="Copies study type, storage condition and time points (you can change them)"
                  placeholder="None: start empty" clearable searchable data={templateOptions}
                  {...form.getInputProps('templateCode')} />
          <Select label="Take the tests from a specification" description="Every test of it is ticked at every time point; untick what doesn't apply"
                  placeholder={values.productId ? 'None: add tests later' : 'Choose a product first'} clearable
                  disabled={!values.productId} data={specOptions} {...form.getInputProps('specificationId')} />
          {values.productId && specs.data?.length === 0 && (
            <Text size="xs" c="dimmed">
              This product has no specification yet.
            </Text>
          )}
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
