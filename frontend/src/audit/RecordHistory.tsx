import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Badge, Button, Card, Group, Skeleton, Stack, Text, ThemeIcon, Timeline, Title } from '@mantine/core';
import { auditApi, type HistoryEntry } from '../api/audit';
import { formatInstant } from '../components/format';
import { IconList } from '../components/icons';
import { actionLabel, fieldLabel, formatValue } from './history';

const SHOWN_CHANGES = 6;

/**
 * The audit trail of one or more records (e.g. a protocol and the selected version): who, when, what changed from
 * what to what, and why. Loaded when opened; reloaded when {@code revision} changes (the record was saved).
 */
export function RecordHistory({ recordIds, revision, timeZone }: { recordIds: string[]; revision: number | string; timeZone?: string }) {
  const [open, setOpen] = useState(false);
  const history = useQuery({
    queryKey: ['audit-history', recordIds, revision],
    queryFn: () => auditApi.history(recordIds),
    enabled: open && recordIds.length > 0,
  });
  const entries = [...(history.data ?? [])].reverse(); // newest first

  return (
    <Card withBorder radius="md" className="arq-fade-in">
      <Group justify="space-between">
        <Group gap="xs">
          <ThemeIcon size={28} radius="md" variant="light" color="gray">
            <IconList />
          </ThemeIcon>
          <Title order={5}>History</Title>
          {history.data && (
            <Text size="xs" c="dimmed">
              {history.data.length} {history.data.length === 1 ? 'entry' : 'entries'} in the audit trail
            </Text>
          )}
        </Group>
        <Button size="xs" variant="subtle" onClick={() => setOpen((o) => !o)}>
          {open ? 'Hide' : 'Show history'}
        </Button>
      </Group>
      {open && (
        <div style={{ marginTop: 'var(--mantine-spacing-md)' }}>
          {history.isPending ? (
            <Skeleton h={80} />
          ) : history.isError ? (
            <Alert color="red">{history.error.message}</Alert>
          ) : entries.length === 0 ? (
            <Text size="sm" c="dimmed">
              No entries.
            </Text>
          ) : (
            <Timeline bulletSize={12} lineWidth={2}>
              {entries.map((e) => (
                <Timeline.Item key={e.seq} title={<EntryTitle entry={e} />}>
                  <Text size="xs" c="dimmed">
                    {formatInstant(e.occurredAt, timeZone)} · {e.actorName}
                    {e.businessKey ? ` · ${e.businessKey}` : ''}
                  </Text>
                  {e.reason && (
                    <Text size="sm" mt={2}>
                      <Text span c="dimmed">
                        Reason:{' '}
                      </Text>
                      {e.reason}
                    </Text>
                  )}
                  <Changes entry={e} />
                </Timeline.Item>
              ))}
            </Timeline>
          )}
        </div>
      )}
    </Card>
  );
}

function EntryTitle({ entry: e }: { entry: HistoryEntry }) {
  return (
    <Group gap={6}>
      <Text size="sm" fw={600}>
        {actionLabel(e.action)}
      </Text>
      {e.actorType !== 'USER' && (
        <Badge size="xs" variant="light" color="gray">
          {e.actorType.toLowerCase()}
        </Badge>
      )}
    </Group>
  );
}

function Changes({ entry: e }: { entry: HistoryEntry }) {
  const [all, setAll] = useState(false);
  if (e.changes.length === 0) {
    return null;
  }
  const shown = all ? e.changes : e.changes.slice(0, SHOWN_CHANGES);
  return (
    <Stack gap={0} mt={4}>
      {shown.map((c) => (
        <Text key={c.field} size="xs" style={{ fontVariantNumeric: 'tabular-nums' }}>
          <Text span fw={500}>
            {fieldLabel(c.field)}
          </Text>
          {': '}
          {e.action === 'CREATE' ? formatValue(c.newValue) : `${formatValue(c.oldValue)} → ${formatValue(c.newValue)}`}
        </Text>
      ))}
      {e.changes.length > SHOWN_CHANGES && (
        <Button size="compact-xs" variant="subtle" w="fit-content" px={0} onClick={() => setAll((a) => !a)}>
          {all ? 'Show fewer' : `Show all ${e.changes.length} changes`}
        </Button>
      )}
    </Stack>
  );
}
