import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Group, Skeleton, Table, Text, TextInput, ThemeIcon, Title, Tooltip } from '@mantine/core';
import { ApiError } from '../api/http';
import { schedulingApi } from '../api/scheduling';
import { IconCalendar } from '../components/icons';
import { formatDay, ruleText, rulesSummary, todayIso } from './pullDates';

/**
 * Pull dates this protocol version would give for an on-stability date, with the site's weekend and holiday
 * rules (URS-SCH-001/002). A preview: nothing is stored. Uses the saved version, so unsaved edits don't show.
 */
export function PullDatePreviewCard({ versionId, revision, unsaved }: { versionId: string; revision: number; unsaved: boolean }) {
  const [onStability, setOnStability] = useState(todayIso());
  const valid = /^\d{4}-\d{2}-\d{2}$/.test(onStability);
  const preview = useQuery({
    queryKey: ['pull-dates', versionId, revision, onStability],
    queryFn: () => schedulingApi.pullDates(versionId, onStability),
    enabled: valid,
  });
  const p = preview.data;

  return (
    <Card withBorder radius="md" className="arq-fade-in">
      <Group justify="space-between" align="flex-end" mb="sm" wrap="wrap">
        <Group gap="xs">
          <ThemeIcon size={28} radius="md" variant="light" color="teal">
            <IconCalendar />
          </ThemeIcon>
          <div>
            <Title order={5}>Pull dates</Title>
            <Text size="xs" c="dimmed">
              Preview for a study placed on stability on this date. Nothing is stored.
            </Text>
          </div>
        </Group>
        <TextInput type="date" label="On-stability date" w={180} value={onStability}
                   onChange={(e) => setOnStability(e.currentTarget.value)} />
      </Group>
      {unsaved && (
        <Text size="xs" c="orange" mb="xs">
          Shows the saved version; save the draft to preview your changes.
        </Text>
      )}
      {preview.isError ? (
        <Alert color="red">
          {preview.error instanceof ApiError && preview.error.errors.length > 0
            ? preview.error.errors.map((e) => e.message ?? e.code).join('; ')
            : preview.error.message}
        </Alert>
      ) : !p ? (
        <Skeleton h={120} />
      ) : (
        <>
          <Table verticalSpacing={4} striped>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Time point</Table.Th>
                <Table.Th>Pull date</Table.Th>
                <Table.Th>Note</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {p.pullDates.map((d) => (
                <Table.Tr key={`${d.value}${d.unit}`}>
                  <Table.Td fw={600}>{d.label}</Table.Td>
                  <Table.Td style={{ fontVariantNumeric: 'tabular-nums' }}>{formatDay(d.pullDate)}</Table.Td>
                  <Table.Td>
                    {d.ruleApplied ? (
                      <Text size="sm" c="orange">
                        {formatDay(d.calculatedDate)}: {ruleText(d.ruleApplied)}
                      </Text>
                    ) : null}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          <Group justify="space-between" mt="xs" gap="xs">
            <Text size="xs" c="dimmed">
              {rulesSummary(p.rules)}
            </Text>
            <Tooltip label="The calculation's name and version, recorded with every schedule">
              <Text size="xs" c="dimmed" ff="monospace">
                {p.algorithm}
              </Text>
            </Tooltip>
          </Group>
        </>
      )}
    </Card>
  );
}
