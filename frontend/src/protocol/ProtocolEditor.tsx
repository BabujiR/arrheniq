import { useState } from 'react';
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Checkbox,
  Group,
  NumberInput,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip,
  UnstyledButton,
} from '@mantine/core';
import type { DictionaryValue, ProtocolContent, StudyType, TestMethod, TimeUnit } from '../api/types';
import { label } from '../components/format';
import { IconPlus, IconRemove } from '../components/icons';
import { addTimepoint, planWarnings, removeTimepoint, testsAt, toggleCell, toggleColumn, totalLabel, totalUnits } from './plan';

export const STUDY_TYPES: StudyType[] = [
  'LONG_TERM',
  'INTERMEDIATE',
  'ACCELERATED',
  'STRESS',
  'PHOTOSTABILITY',
  'IN_USE',
  'EXCURSION',
  'COMMITMENT',
  'ANNUAL',
  'OTHER',
];

interface Props {
  content: ProtocolContent;
  editable: boolean;
  onChange: (c: ProtocolContent) => void;
  library: TestMethod[];
  conditions: DictionaryValue[];
  conditionLabel?: string | null;
  errors: Record<string, string>;
}

/**
 * Protocol content (URS-PRO-001/002/003/007): study type and condition, time points with planned units, and the
 * test × time-point grid. Read-only shows the same grid with marks.
 */
export function ProtocolEditor({ content: c, editable, onChange, library, conditions, conditionLabel, errors }: Props) {
  const [newValue, setNewValue] = useState<number | string>('');
  const [newUnit, setNewUnit] = useState<TimeUnit>('MONTH');
  const warnings = planWarnings(c);
  const inProtocol = new Set(c.tests.map((t) => t.testMethodId));
  const addable = library
    .filter((m) => m.status === 'ACTIVE' && !inProtocol.has(m.id))
    .map((m) => ({ value: m.id, label: `${m.code} · ${m.name}` }));
  const firstError = Object.entries(errors).find(([k]) => k.startsWith('timepoints') || k.startsWith('tests'));

  const addTp = () => {
    const v = typeof newValue === 'number' ? newValue : Number(newValue);
    if (newValue === '' || Number.isNaN(v) || v < 0) return;
    onChange(addTimepoint(c, v, newUnit));
    setNewValue('');
  };

  return (
    <Stack>
      <SimpleGrid cols={{ base: 1, sm: 4 }}>
        {editable ? (
          <>
            <Select
              label="Study type"
              data={STUDY_TYPES.map((t) => ({ value: t, label: label(t) }))}
              value={c.studyType}
              error={errors.studyType}
              allowDeselect={false}
              onChange={(v) => v && onChange({ ...c, studyType: v as StudyType })}
            />
            <Select
              label="Storage condition"
              placeholder="Choose a condition"
              searchable
              clearable
              data={conditions.map((d) => ({ value: d.code, label: d.label }))}
              value={c.conditionCode}
              error={errors.conditionCode}
              onChange={(v) => onChange({ ...c, conditionCode: v })}
            />
            <TextInput
              label="Unit of inventory"
              placeholder="e.g. BOTTLE, BLISTER"
              value={c.fillUnitCode ?? ''}
              error={errors.fillUnitCode}
              onChange={(e) => onChange({ ...c, fillUnitCode: e.currentTarget.value || null })}
            />
            <NumberInput
              label="Reserve units"
              description="For re-tests and investigations"
              inputWrapperOrder={['label', 'input', 'description', 'error']}
              min={0}
              value={c.reserveUnits}
              error={errors.reserveUnits}
              onChange={(v) => onChange({ ...c, reserveUnits: typeof v === 'number' ? v : Number(v || 0) })}
            />
          </>
        ) : (
          <>
            <Field name="Study type" value={label(c.studyType)} />
            <Field name="Storage condition" value={conditionLabel ?? '—'} />
            <Field name="Unit of inventory" value={c.fillUnitCode ?? 'units'} />
            <Field name="Reserve units" value={String(c.reserveUnits)} />
          </>
        )}
      </SimpleGrid>

      <div>
        <Group justify="space-between" mb={6}>
          <Title order={5}>Time points and tests</Title>
          <Badge variant="light" color="teal" size="lg" tt="none">
            {totalLabel(totalUnits(c), c.fillUnitCode)}
          </Badge>
        </Group>
        <Table.ScrollContainer minWidth={360 + c.timepoints.length * 72}>
          <Table withColumnBorders withTableBorder verticalSpacing={6} style={{ textAlign: 'center' }}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ textAlign: 'left', minWidth: 220 }}>Test</Table.Th>
                {c.timepoints.map((t, i) => (
                  <Table.Th key={`${t.value}${t.unit}`} style={{ textAlign: 'center', minWidth: 72 }}>
                    <Stack gap={2} align="center">
                      {editable ? (
                        <Tooltip label="Tick or clear the whole column">
                          <UnstyledButton onClick={() => onChange(toggleColumn(c, i))}>
                            <Text fw={600} size="sm">
                              {t.label}
                            </Text>
                          </UnstyledButton>
                        </Tooltip>
                      ) : (
                        <Text fw={600} size="sm">
                          {t.label}
                        </Text>
                      )}
                      {editable && (
                        <Tooltip label={`Remove ${t.label}`}>
                          <ActionIcon size="xs" variant="subtle" color="gray" aria-label={`Remove ${t.label}`}
                                      onClick={() => onChange(removeTimepoint(c, i))}>
                            <IconRemove size={12} />
                          </ActionIcon>
                        </Tooltip>
                      )}
                    </Stack>
                  </Table.Th>
                ))}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {c.tests.map((test, ti) => (
                <Table.Tr key={test.testMethodId}>
                  <Table.Td style={{ textAlign: 'left' }}>
                    <Group gap={6} wrap="nowrap" justify="space-between">
                      <Group gap={6} wrap="nowrap">
                        <Badge variant="light" color="arqIndigo" size="sm">
                          {test.testCode}
                        </Badge>
                        <Text size="sm" lineClamp={1}>
                          {test.testName}
                        </Text>
                        {test.testStatus === 'RETIRED' && (
                          <Badge size="xs" color="gray" variant="outline">
                            retired
                          </Badge>
                        )}
                      </Group>
                      {editable && (
                        <ActionIcon size="sm" variant="subtle" color="gray" aria-label={`Remove ${test.testCode}`}
                                    title={`Remove ${test.testCode} from the plan`}
                                    onClick={() => onChange({ ...c, tests: c.tests.filter((_, j) => j !== ti) })}>
                          <IconRemove size={14} />
                        </ActionIcon>
                      )}
                    </Group>
                    {errors[`tests[${ti}].at`] && (
                      <Text size="xs" c="red">
                        {errors[`tests[${ti}].at`]}
                      </Text>
                    )}
                  </Table.Td>
                  {c.timepoints.map((t, i) => (
                    <Table.Td key={`${t.value}${t.unit}`}>
                      {editable ? (
                        <Checkbox
                          aria-label={`${test.testCode} at ${t.label}`}
                          checked={test.at.includes(i)}
                          onChange={() => onChange(toggleCell(c, ti, i))}
                          styles={{ body: { justifyContent: 'center' } }}
                        />
                      ) : test.at.includes(i) ? (
                        <Text c="teal" fw={700}>
                          ●
                        </Text>
                      ) : null}
                    </Table.Td>
                  ))}
                </Table.Tr>
              ))}
            </Table.Tbody>
            <Table.Tfoot>
              <Table.Tr>
                <Table.Th style={{ textAlign: 'left' }}>
                  <Text size="xs" c="dimmed">
                    Tests · units planned
                  </Text>
                </Table.Th>
                {c.timepoints.map((t, i) => (
                  <Table.Td key={`${t.value}${t.unit}`}>
                    <Stack gap={2} align="center">
                      <Text size="xs" c="dimmed">
                        {testsAt(c, i)} tests
                      </Text>
                      {editable ? (
                        <NumberInput
                          size="xs"
                          w={60}
                          min={0}
                          hideControls
                          aria-label={`Units at ${t.label}`}
                          value={t.unitsPlanned}
                          error={errors[`timepoints[${i}].unitsPlanned`] ? true : undefined}
                          onChange={(v) =>
                            onChange({
                              ...c,
                              timepoints: c.timepoints.map((x, j) =>
                                j === i ? { ...x, unitsPlanned: typeof v === 'number' ? v : Number(v || 0) } : x,
                              ),
                            })
                          }
                        />
                      ) : (
                        <Text size="sm" fw={600}>
                          {t.unitsPlanned}
                        </Text>
                      )}
                    </Stack>
                  </Table.Td>
                ))}
              </Table.Tr>
            </Table.Tfoot>
          </Table>
        </Table.ScrollContainer>
        {c.timepoints.length === 0 && (
          <Text size="sm" c="dimmed" ta="center" py="sm">
            No time points yet{editable ? ': add them below, or start the protocol from an ICH template.' : '.'}
          </Text>
        )}
      </div>

      {editable && (
        <Group gap="xs" align="flex-end">
          <NumberInput label="Add time point" placeholder="e.g. 18" min={0} max={10000} allowDecimal={false}
                       allowNegative={false} w={120} value={newValue}
                       onChange={setNewValue} onKeyDown={(e) => e.key === 'Enter' && addTp()} />
          <Select w={110} data={[{ value: 'MONTH', label: 'months' }, { value: 'WEEK', label: 'weeks' }, { value: 'DAY', label: 'days' }]}
                  value={newUnit} allowDeselect={false} onChange={(v) => v && setNewUnit(v as TimeUnit)} />
          <Button variant="light" leftSection={<IconPlus size={14} />} onClick={addTp}>
            Add
          </Button>
          <Select ml="md" w={320} searchable placeholder="Add a test from the library…" data={addable} value={null}
                  nothingFoundMessage="No other active tests" leftSection={<IconPlus size={14} />}
                  onChange={(id) => {
                    const m = library.find((t) => t.id === id);
                    if (m) {
                      onChange({
                        ...c,
                        tests: [...c.tests, {
                          testMethodId: m.id,
                          testCode: m.code,
                          testName: m.name,
                          testStatus: m.status,
                          resultsFormat: m.resultsFormat,
                          at: c.timepoints.map((_, i) => i),
                        }],
                      });
                    }
                  }} />
        </Group>
      )}

      {firstError && (
        <Alert color="red" variant="light">
          {firstError[1]}
        </Alert>
      )}
      {warnings.length > 0 && (
        <Alert color="yellow" variant="light" title="Check the plan">
          <Stack gap={2}>
            {warnings.map((w) => (
              <Text key={w} size="sm">
                {w}
              </Text>
            ))}
          </Stack>
        </Alert>
      )}
    </Stack>
  );
}

function Field({ name, value }: { name: string; value: string }) {
  return (
    <div>
      <Text size="xs" c="dimmed">
        {name}
      </Text>
      <Text size="sm">{value}</Text>
    </div>
  );
}
