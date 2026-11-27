import { Badge } from '@mantine/core';

const COLORS: Record<string, string> = {
  DRAFT: 'gray',
  IN_REVIEW: 'yellow',
  APPROVED: 'green',
  EFFECTIVE: 'green',
  RETIRED: 'dark',
  ACTIVE: 'green',
  VALID: 'green',
  SUPERSEDED: 'orange',
  REVOKED: 'red',
  SCHEDULED: 'blue',
  PULLED: 'teal',
  IN_TESTING: 'cyan',
  COMPLETED: 'green',
  TERMINATED: 'red',
};

export function StatusBadge({ status }: { status: string }) {
  return (
    <Badge color={COLORS[status] ?? 'gray'} variant="light" style={{ flexShrink: 0 }}>
      {status.replace('_', ' ')}
    </Badge>
  );
}
