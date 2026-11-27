import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Button, Group, SegmentedControl, TextInput } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { studiesApi, studyKeys } from '../api/studies';
import type { StudySummary } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { DataGrid } from '../components/DataGrid';
import { IconPlus, IconStudy } from '../components/icons';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { formatDay } from '../scheduling/pullDates';

const columns: ColDef<StudySummary>[] = [
  { field: 'studyCode', headerName: 'Study', maxWidth: 120 },
  {
    colId: 'product',
    headerName: 'Product',
    flex: 2,
    valueGetter: (p) => (p.data ? `${p.data.productCode} · ${p.data.productName}` : ''),
  },
  { field: 'lotNumber', headerName: 'Lot', maxWidth: 140 },
  { field: 'conditionLabel', headerName: 'Condition', flex: 2, tooltipField: 'conditionLabel' },
  {
    colId: 'protocol',
    headerName: 'Protocol',
    maxWidth: 190,
    valueGetter: (p) => (p.data ? `${p.data.protocolNumber} v${p.data.protocolVersionNo}` : ''),
  },
  {
    colId: 'start',
    headerName: 'On stability',
    maxWidth: 150,
    valueGetter: (p) => (p.data ? formatDay(p.data.onStabilityDate) : ''),
  },
  {
    colId: 'next',
    headerName: 'Next pull',
    maxWidth: 150,
    valueGetter: (p) => (p.data?.nextPullDate ? formatDay(p.data.nextPullDate) : ''),
  },
  {
    colId: 'status',
    headerName: 'Status',
    maxWidth: 140,
    cellRenderer: (p: ICellRendererParams<StudySummary>) =>
      p.data ? (
        <Group h="100%">
          <StatusBadge status={p.data.status} />
        </Group>
      ) : null,
  },
];

/** Stability studies (URS-STU-001..005): one row per lot and condition. */
export function StudiesPage() {
  const { can } = useSession();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('ALL');
  const [debouncedQ] = useDebouncedValue(q, 300);
  const filter = { q: debouncedQ || undefined, status: status === 'ALL' ? undefined : status };
  const studies = useQuery({ queryKey: studyKeys.list(filter), queryFn: () => studiesApi.list(filter) });
  const canCreate = can('study.create');

  return (
    <>
      <PageHeader
        icon={<IconStudy size={24} />}
        title="Studies"
        description="Lots on stability, one study per storage condition. Approving a study generates its pull schedule."
        actions={
          <>
            <SegmentedControl
              size="xs"
              value={status}
              onChange={setStatus}
              data={[
                { value: 'ALL', label: 'All' },
                { value: 'DRAFT', label: 'Draft' },
                { value: 'IN_REVIEW', label: 'In review' },
                { value: 'APPROVED', label: 'Approved' },
              ]}
            />
            <TextInput placeholder="Search study, lot or product" value={q} onChange={(e) => setQ(e.currentTarget.value)} />
            {canCreate && (
              <Button leftSection={<IconPlus />} onClick={() => void navigate({ to: '/studies/new' })}>
                Log in study
              </Button>
            )}
          </>
        }
      />
      <DataGrid
        rows={studies.data}
        columns={columns}
        rowId={(s) => s.id}
        loading={studies.isPending}
        emptyMessage={
          q || status !== 'ALL'
            ? 'No study matches.'
            : canCreate
              ? 'No studies yet. Use “Log in study” to place a lot on stability.'
              : 'No studies yet.'
        }
        onRowClick={(s) => void navigate({ to: '/studies/$studyId', params: { studyId: s.id } })}
      />
    </>
  );
}
