import { ActionIcon, Badge, Group, NumberInput, Select, Table, Text, TextInput, Tooltip } from '@mantine/core';
import type { SpecTest, TestMethod } from '../api/types';
import { IconPlus, IconRemove } from '../components/icons';
import { alertText, limitText, type VersionChanges } from './limits';

type NumField = 'lowerLimit' | 'upperLimit' | 'alertLower' | 'alertUpper' | 'decimals' | 'replicates';

const toNum = (v: string | number): number | null => (v === '' ? null : typeof v === 'number' ? v : Number(v));

/** A new row for a library test, with the library's defaults (the server fills the same defaults). */
export function rowFromLibrary(m: TestMethod, sortOrder: number): SpecTest {
  const numeric = m.resultsFormat === 'NUMERIC';
  return {
    testMethodId: m.id,
    testCode: m.code,
    testStatus: m.status === 'RETIRED' ? 'RETIRED' : 'ACTIVE',
    sortOrder,
    displayName: m.name,
    resultsFormat: m.resultsFormat,
    unitCode: numeric ? m.unitCode : null,
    replicates: m.replicates,
    decimals: numeric ? m.decimals : null,
    lowerLimit: numeric ? m.defaultLowerLimit : null,
    upperLimit: numeric ? m.defaultUpperLimit : null,
    textLimit: numeric ? null : m.defaultTextLimit,
    alertLower: numeric ? m.defaultAlertLower : null,
    alertUpper: numeric ? m.defaultAlertUpper : null,
    trendable: m.trendable,
  };
}

interface Props {
  rows: SpecTest[];
  editable: boolean;
  onChange: (rows: SpecTest[]) => void;
  /** server field errors, keyed tests[i].field */
  errors: Record<string, string>;
  library: TestMethod[];
  changes?: VersionChanges;
}

/**
 * The tests of a specification version. Read-only: acceptance criterion as written ("NMT 0.5 %"), with changes
 * against the previous version highlighted. Editable (DRAFT): limits, alert limits, unit, decimals, replicates,
 * order; add tests from the library, remove tests.
 */
export function SpecTestEditor({ rows, editable, onChange, errors, library, changes }: Props) {
  const update = (i: number, patch: Partial<SpecTest>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  const move = (i: number, delta: number) => {
    const j = i + delta;
    if (j < 0 || j >= rows.length) return;
    const next = [...rows];
    [next[i], next[j]] = [next[j]!, next[i]!];
    onChange(next);
  };
  const err = (i: number, field: string) => errors[`tests[${i}].${field}`];
  const num = (i: number, field: NumField, row: SpecTest, width: number, placeholder?: string) => (
    <NumberInput
      size="xs"
      w={width}
      hideControls
      placeholder={placeholder}
      value={row[field] ?? ''}
      error={err(i, field)}
      onChange={(v) => update(i, { [field]: toNum(v) } as Partial<SpecTest>)}
      allowNegative
    />
  );
  const changed = (row: SpecTest, ...fields: (keyof SpecTest)[]) =>
    changes && (changes.added.has(row.testMethodId) || fields.some((f) => changes.changed.get(row.testMethodId)?.has(f)));
  const mark = (on: boolean | undefined) => (on ? { backgroundColor: 'var(--mantine-color-yellow-light)' } : undefined);

  const inSpec = new Set(rows.map((r) => r.testMethodId));
  const addable = library
    .filter((m) => m.status === 'ACTIVE' && !inSpec.has(m.id))
    .map((m) => ({ value: m.id, label: `${m.code} · ${m.name}` }));

  return (
    <>
      <Table.ScrollContainer minWidth={editable ? 980 : 640}>
        <Table verticalSpacing={editable ? 6 : 'sm'} highlightOnHover={!editable}>
          <Table.Thead>
            <Table.Tr>
              <Table.Th w={36}>#</Table.Th>
              <Table.Th>Test</Table.Th>
              <Table.Th>{editable ? 'Lower / upper limit or text' : 'Acceptance criterion'}</Table.Th>
              <Table.Th>Alert limits</Table.Th>
              {editable && <Table.Th>Unit</Table.Th>}
              {editable && <Table.Th>Dec.</Table.Th>}
              <Table.Th>Replicates</Table.Th>
              {editable && <Table.Th w={96} />}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((row, i) => (
              <Table.Tr key={row.testMethodId}>
                <Table.Td>
                  <Text size="xs" c="dimmed">
                    {i + 1}
                  </Text>
                </Table.Td>
                <Table.Td style={mark(changed(row, 'displayName'))}>
                  <Group gap={6} wrap="nowrap">
                    <Badge variant="light" color="arqIndigo" size="sm" style={{ flexShrink: 0 }}>
                      {row.testCode}
                    </Badge>
                    {editable ? (
                      <TextInput
                        size="xs"
                        value={row.displayName}
                        error={err(i, 'displayName')}
                        onChange={(e) => update(i, { displayName: e.currentTarget.value })}
                        style={{ flex: 1, minWidth: 140 }}
                      />
                    ) : (
                      <Text size="sm">{row.displayName}</Text>
                    )}
                    {row.testStatus === 'RETIRED' && (
                      <Tooltip label="Retired in the test library; kept because the previous version had it">
                        <Badge size="xs" color="gray" variant="outline">
                          retired
                        </Badge>
                      </Tooltip>
                    )}
                    {changes?.added.has(row.testMethodId) && (
                      <Badge size="xs" color="yellow">
                        new
                      </Badge>
                    )}
                  </Group>
                </Table.Td>
                <Table.Td style={mark(changed(row, 'lowerLimit', 'upperLimit', 'textLimit', 'unitCode'))}>
                  {!editable ? (
                    <Text size="sm" fw={500}>
                      {limitText(row)}
                    </Text>
                  ) : row.resultsFormat === 'TEXT' ? (
                    <TextInput
                      size="xs"
                      placeholder="e.g. Conforms (empty = report)"
                      value={row.textLimit ?? ''}
                      error={err(i, 'textLimit')}
                      onChange={(e) => update(i, { textLimit: e.currentTarget.value || null })}
                    />
                  ) : (
                    <Group gap={4} wrap="nowrap">
                      {num(i, 'lowerLimit', row, 84, 'NLT')}
                      <Text size="xs" c="dimmed">
                        –
                      </Text>
                      {num(i, 'upperLimit', row, 84, 'NMT')}
                    </Group>
                  )}
                </Table.Td>
                <Table.Td style={mark(changed(row, 'alertLower', 'alertUpper'))}>
                  {!editable ? (
                    <Text size="sm" c="dimmed">
                      {alertText(row)}
                    </Text>
                  ) : row.resultsFormat === 'NUMERIC' ? (
                    <Group gap={4} wrap="nowrap">
                      {num(i, 'alertLower', row, 72)}
                      <Text size="xs" c="dimmed">
                        –
                      </Text>
                      {num(i, 'alertUpper', row, 72)}
                    </Group>
                  ) : null}
                </Table.Td>
                {editable && (
                  <Table.Td>
                    {row.resultsFormat === 'NUMERIC' && (
                      <TextInput
                        size="xs"
                        w={80}
                        value={row.unitCode ?? ''}
                        error={err(i, 'unitCode')}
                        onChange={(e) => update(i, { unitCode: e.currentTarget.value || null })}
                      />
                    )}
                  </Table.Td>
                )}
                {editable && <Table.Td>{row.resultsFormat === 'NUMERIC' && num(i, 'decimals', row, 52)}</Table.Td>}
                <Table.Td style={mark(changed(row, 'replicates'))}>
                  {editable ? num(i, 'replicates', row, 56) : <Text size="sm">{row.replicates}</Text>}
                </Table.Td>
                {editable && (
                  <Table.Td>
                    <Group gap={2} wrap="nowrap">
                      <ActionIcon size="sm" variant="subtle" color="gray" aria-label="Move up" disabled={i === 0}
                                  onClick={() => move(i, -1)}>
                        ↑
                      </ActionIcon>
                      <ActionIcon size="sm" variant="subtle" color="gray" aria-label="Move down"
                                  disabled={i === rows.length - 1} onClick={() => move(i, 1)}>
                        ↓
                      </ActionIcon>
                      <ActionIcon size="sm" variant="subtle" color="red" aria-label="Remove test"
                                  onClick={() => onChange(rows.filter((_, j) => j !== i))}>
                        <IconRemove size={14} />
                      </ActionIcon>
                    </Group>
                  </Table.Td>
                )}
              </Table.Tr>
            ))}
            {changes?.removed.map((row) => (
              <Table.Tr key={`removed-${row.testMethodId}`} style={{ opacity: 0.6 }}>
                <Table.Td />
                <Table.Td colSpan={editable ? 7 : 4}>
                  <Group gap={6}>
                    <Badge size="xs" color="red">
                      removed
                    </Badge>
                    <Text size="sm" td="line-through">
                      {row.testCode} · {row.displayName} ({limitText(row)})
                    </Text>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {rows.length === 0 && (
        <Text size="sm" c="dimmed" ta="center" py="md">
          {editable ? 'No tests yet. Add tests from the test library below.' : 'This version has no tests.'}
        </Text>
      )}
      {editable && (
        <Group mt="sm" gap="xs">
          <Select
            size="sm"
            w={360}
            searchable
            placeholder="Add a test from the library…"
            data={addable}
            value={null}
            nothingFoundMessage="No other active tests"
            leftSection={<IconPlus size={14} />}
            onChange={(id) => {
              const m = library.find((t) => t.id === id);
              if (m) onChange([...rows, rowFromLibrary(m, (rows.length + 1) * 10)]);
            }}
          />
          {errors.tests && (
            <Text size="xs" c="red">
              {errors.tests}
            </Text>
          )}
        </Group>
      )}
    </>
  );
}
