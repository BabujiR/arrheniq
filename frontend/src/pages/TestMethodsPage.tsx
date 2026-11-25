import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Select, TextInput } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { keys, testMethodsApi } from '../api/masterdata';
import type { TestMethod } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { DataGrid } from '../components/DataGrid';
import { IconPlus, IconTest } from '../components/icons';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { useCreateParam } from '../components/useCreateParam';
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
  useCreateParam('test-methods', () => {
    setEditing(undefined);
    setFormOpen(true);
  });

  return (
    <>
      <PageHeader
        icon={<IconTest size={24} />}
        title="Test library"
        description="Reusable tests with results format, units, precision and default limits for specifications."
        actions={
          <>
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
                leftSection={<IconPlus />}
                onClick={() => {
                  setEditing(undefined);
                  setFormOpen(true);
                }}
              >
                New test
              </Button>
            )}
          </>
        }
      />
      <DataGrid
        rows={tests.data}
        columns={columns}
        rowId={(t) => t.id}
        loading={tests.isPending}
        emptyMessage={
          q || status !== 'ACTIVE'
            ? 'No test matches your search.'
            : canManage
              ? 'The test library is empty. Use “New test” to add the first method.'
              : 'The test library is empty.'
        }
        onRowClick={(t) => {
          setEditing(t);
          setFormOpen(true);
        }}
      />
      <TestMethodForm opened={formOpen} existing={editing} canManage={canManage} onClose={() => setFormOpen(false)} />
    </>
  );
}
