import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Group, NavLink, ScrollArea, SegmentedControl, Stack, Text, TextInput } from '@mantine/core';
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import { dictionariesApi, keys, protocolTemplatesApi } from '../api/masterdata';
import type { Dictionary, DictionaryValue, ProtocolTemplate } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { DataGrid } from '../components/DataGrid';
import { label } from '../components/format';
import { IconCalendar, IconList, IconPlus } from '../components/icons';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { summary } from '../lists/attributes';
import { DictionaryValueForm } from './DictionaryValueForm';

type View = 'lists' | 'templates';

const CATEGORY_ORDER: Dictionary['category'][] = ['STABILITY', 'RESULTS', 'PACKAGING', 'ORGANISATION', 'SYSTEM'];

/** Controlled lists (URS-MD-006) and the ICH time-point templates every tenant starts with (URS-MD-007). */
export function ListsPage() {
  const { can, me } = useSession();
  const canManage = can('dictionary.manage');
  const [view, setView] = useState<View>('lists');
  const [selected, setSelected] = useState('STORAGE_CONDITION');
  const [q, setQ] = useState('');
  const [editing, setEditing] = useState<DictionaryValue | undefined>();
  const [formOpen, setFormOpen] = useState(false);

  const lists = useQuery({ queryKey: keys.dictionaries, queryFn: dictionariesApi.lists });
  const list = lists.data?.find((d) => d.code === selected);
  const values = useQuery({
    queryKey: keys.dictionaryValues(selected),
    queryFn: () => dictionariesApi.values(selected),
    enabled: view === 'lists',
  });
  const templates = useQuery({
    queryKey: keys.protocolTemplates,
    queryFn: protocolTemplatesApi.list,
    enabled: view === 'templates',
  });

  const siteCode = (id: string | null) => (id ? (me.sites.find((s) => s.id === id)?.code ?? '—') : 'All');
  const valueColumns = useMemo<ColDef<DictionaryValue>[]>(
    () => [
      { field: 'code', headerName: 'Code', maxWidth: 170 },
      { field: 'label', headerName: 'Label', flex: 3 },
      {
        colId: 'attributes',
        headerName: 'Attributes',
        flex: 2,
        valueGetter: (p) => (p.data ? summary(p.data.attributes) : ''),
      },
      { colId: 'site', headerName: 'Site', maxWidth: 110, valueGetter: (p) => siteCode(p.data?.siteId ?? null) },
      {
        colId: 'flags',
        headerName: 'Use',
        maxWidth: 190,
        cellRenderer: (p: ICellRendererParams<DictionaryValue>) =>
          p.data ? (
            <Group gap={4} wrap="nowrap" h="100%">
              {p.data.inUse && (
                <Badge size="sm" variant="light" color="arqIndigo">
                  In use
                </Badge>
              )}
              {p.data.fromCatalog && (
                <Badge size="sm" variant="light" color="teal">
                  Catalog
                </Badge>
              )}
            </Group>
          ) : null,
      },
      {
        field: 'status',
        headerName: 'Status',
        maxWidth: 130,
        cellRenderer: (p: ICellRendererParams<DictionaryValue, string>) => (p.value ? <StatusBadge status={p.value} /> : null),
      },
    ],
    [me.sites],
  );

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase();
    return term
      ? values.data?.filter((v) => v.code.toLowerCase().includes(term) || v.label.toLowerCase().includes(term))
      : values.data;
  }, [values.data, q]);

  const grouped = CATEGORY_ORDER.map((category) => ({
    category,
    items: (lists.data ?? []).filter((d) => d.category === category),
  })).filter((g) => g.items.length > 0);

  return (
    <>
      <PageHeader
        icon={view === 'lists' ? <IconList size={24} /> : <IconCalendar size={24} />}
        title={view === 'lists' ? 'Lists' : 'ICH time-point templates'}
        description={
          view === 'lists'
            ? 'Controlled values used across ArrheniQ: storage conditions, reasons, units, packaging and more.'
            : 'Standard time-point plans per ICH Q1A(R2)/Q5C. Protocols start from a copy (coming with the protocol builder).'
        }
        actions={
          <>
            {view === 'lists' && (
              <TextInput placeholder="Search code or label" value={q} onChange={(e) => setQ(e.currentTarget.value)} />
            )}
            <SegmentedControl
              value={view}
              onChange={(v) => setView(v as View)}
              data={[
                { value: 'lists', label: 'Lists' },
                { value: 'templates', label: 'ICH templates' },
              ]}
            />
            {view === 'lists' && canManage && list && !list.systemManaged && (
              <Button
                leftSection={<IconPlus />}
                onClick={() => {
                  setEditing(undefined);
                  setFormOpen(true);
                }}
              >
                Add value
              </Button>
            )}
          </>
        }
      />

      {view === 'lists' ? (
        <div style={{ display: 'flex', gap: 16, flex: '1 1 0', minHeight: 0 }}>
          <ScrollArea w={250} style={{ flexShrink: 0 }} className="arq-fade-in">
            <Stack gap="sm">
              {grouped.map((g) => (
                <div key={g.category}>
                  <Text size="xs" c="dimmed" fw={600} tt="uppercase" px="sm" mb={4} style={{ letterSpacing: '0.05em' }}>
                    {label(g.category)}
                  </Text>
                  {g.items.map((d) => (
                    <NavLink
                      key={d.code}
                      label={d.name}
                      active={d.code === selected}
                      onClick={() => {
                        setSelected(d.code);
                        setQ('');
                      }}
                      rightSection={
                        <Badge size="xs" variant="light" color={d.code === selected ? 'arqIndigo' : 'gray'}>
                          {d.activeValues}
                        </Badge>
                      }
                      style={{ borderRadius: 8 }}
                    />
                  ))}
                </div>
              ))}
            </Stack>
          </ScrollArea>
          <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
            <DataGrid
              rows={filtered}
              columns={valueColumns}
              rowId={(v) => v.id}
              loading={values.isPending || lists.isPending}
              emptyMessage={
                q
                  ? 'No value matches your search.'
                  : canManage && list && !list.systemManaged
                    ? `${list.name} has no values yet. Use “Add value” to add the first one.`
                    : 'This list has no values yet.'
              }
              onRowClick={(v) => {
                setEditing(v);
                setFormOpen(true);
              }}
            />
          </div>
        </div>
      ) : (
        <DataGrid rows={templates.data} columns={templateColumns} rowId={(t) => t.code} loading={templates.isPending} />
      )}

      {list && (
        <DictionaryValueForm
          opened={formOpen}
          list={list}
          existing={editing}
          canManage={canManage}
          onClose={() => setFormOpen(false)}
        />
      )}
    </>
  );
}

const UNIT: Record<ProtocolTemplate['timepointUnit'], string> = { DAY: 'd', WEEK: 'w', MONTH: 'M' };

const templateColumns: ColDef<ProtocolTemplate>[] = [
  { field: 'name', headerName: 'Template', flex: 2 },
  { colId: 'studyType', headerName: 'Study type', maxWidth: 160, valueGetter: (p) => label(p.data?.studyType ?? '') },
  { field: 'conditionLabel', headerName: 'Condition', flex: 3 },
  {
    colId: 'timepoints',
    headerName: 'Time points',
    flex: 2,
    valueGetter: (p) => (p.data ? p.data.timepoints.map((t) => `${t}${UNIT[p.data!.timepointUnit]}`).join(', ') : ''),
  },
  { field: 'source', headerName: 'Source', flex: 2, tooltipField: 'source' },
];
