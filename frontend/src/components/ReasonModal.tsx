import { useEffect, useState } from 'react';
import { Button, Group, Modal, Stack, Text, Textarea } from '@mantine/core';

interface Props {
  opened: boolean;
  title: string;
  description?: string;
  confirmLabel?: string;
  busy?: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}

/** Asks for the reason for a change. The server decides when one is required (URS-AUD-004). */
export function ReasonModal({ opened, title, description, confirmLabel = 'Confirm', busy, onCancel, onConfirm }: Props) {
  const [reason, setReason] = useState('');
  useEffect(() => {
    if (opened) {
      setReason('');
    }
  }, [opened]);
  const valid = reason.trim().length >= 3;
  return (
    <Modal opened={opened} onClose={onCancel} title={title} centered>
      <Stack>
        {description && <Text size="sm">{description}</Text>}
        <Textarea
          label="Reason for change"
          description="Recorded in the audit trail with your name and the time."
          autosize
          minRows={3}
          value={reason}
          onChange={(e) => setReason(e.currentTarget.value)}
          data-autofocus
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onCancel}>
            Cancel
          </Button>
          <Button disabled={!valid} loading={busy} onClick={() => onConfirm(reason.trim())}>
            {confirmLabel}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
