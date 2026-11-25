import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Group, Select, TextInput, Title } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { keys, testMethodsApi } from '../api/masterdata';
import type { TestMethod } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { DataGrid } from '../components/DataGrid';
import { StatusBadge } from '../components/StatusBadge';
import { TestMethodForm } from './TestMethodForm';

const columns: ColDef<TestMethod>[] = [
  { field: 'code', headerName: 'Code', maxWidth: 160 },
  { field: 'name', headerName: 'Name', flex: 2 },
  { field: 'resultsFormat', headerName: 'Format', maxWidth: 130 },
  { field: 'unitCode', headerName: 'Unit', maxWidth: 120 },
  { field: 'defaultLowerLimit', headerName: 'Lower', maxWidth: 120 },
  { field: 'defaultUpperLimit', headerName: 'Upper', maxWidth: 120 },
  { field: 'replicates', headerName: 'Replicates', maxWidth: 130 },
  {
    field: 'status',
    headerName: 'Status',
    maxWidth: 140,
    cellRenderer: (p: ICellRendererParams<TestMethod, string>) => (p.value ? <StatusBadge status={p.value} /> : null),
  },
];

/** Test library (URS-MD-001). */
export function TestMethodsPage() {
  const { can } = useSession();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<string | null>('ACTIVE');
  const [debouncedQ] = useDebouncedValue(q, 300);
  const filter = { q: debouncedQ || undefined, status: status ?? undefined };
  const tests = useQuery({ queryKey: keys.testMethods(filter), queryFn: () => testMethodsApi.list(filter) });
  const [editing, setEditing] = useState<TestMethod | undefined>();
  const [formOpen, setFormOpen] = useState(false);
  const canManage = can('test.manage');

  return (
    <>
      <Group justify="space-between" mb="md">
        <Title order={3}>Test library</Title>
        <Group>
          <TextInput placeholder="Search code or name" value={q} onChange={(e) => setQ(e.currentTarget.value)} />
          <Select
            placeholder="All statuses"
            clearable
            w={150}
            data={['ACTIVE', 'RETIRED']}
            value={status}
            onChange={setStatus}
          />
          {canManage && (
            <Button
              onClick={() => {
                setEditing(undefined);
                setFormOpen(true);
              }}
            >
              New test
            </Button>
          )}
        </Group>
      </Group>
      <DataGrid
        rows={tests.data}
        columns={columns}
        rowId={(t) => t.id}
        loading={tests.isPending}
        onRowClick={(t) => {
          setEditing(t);
          setFormOpen(true);
        }}
      />
      <TestMethodForm opened={formOpen} existing={editing} canManage={canManage} onClose={() => setFormOpen(false)} />
    </>
  );
}
