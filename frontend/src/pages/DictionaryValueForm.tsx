import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Text,
  Textarea,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { ApiError, isReasonRequired } from '../api/http';
import { dictionariesApi, keys } from '../api/masterdata';
import type { Dictionary, DictionaryValue, DictionaryValueInput } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { toFormErrors } from '../components/formErrors';
import { IconArchive, IconPlus, IconReject } from '../components/icons';
import { notifyError, notifySuccess } from '../components/notify';
import { ReasonModal } from '../components/ReasonModal';
import { StatusBadge } from '../components/StatusBadge';
import { type AttributeRow, fromRows, toRows } from '../lists/attributes';

interface Values {
  code: string;
  label: string;
  description: string;
  sortOrder: number | string;
  siteId: string | null;
}

type ReasonFor = 'save' | 'retire' | 'reactivate' | 'delete';

interface Props {
  opened: boolean;
  list: Dictionary;
  existing?: DictionaryValue;
  canManage: boolean;
  onClose: () => void;
}

/** Add or change one value of a controlled list; retire, reactivate or delete it (URS-MD-006). */
export function DictionaryValueForm({ opened, list, existing, canManage, onClose }: Props) {
  const { me } = useSession();
  const queryClient = useQueryClient();
  const form = useForm<Values>({ initialValues: toValues(existing) });
  const [rows, setRows] = useState<AttributeRow[]>([]);
  const [attributeError, setAttributeError] = useState<string | null>(null);
  const [reasonFor, setReasonFor] = useState<ReasonFor | null>(null);
  const readOnly = !canManage || list.systemManaged || existing?.status === 'RETIRED';

  useEffect(() => {
    if (opened) {
      form.setValues(toValues(existing));
      form.resetDirty(toValues(existing));
      form.clearErrors();
      setRows(existing ? toRows(existing.attributes) : []);
      setAttributeError(null);
    }
  }, [opened, existing?.id, existing?.version]);

  const done = (message: string) => {
    void queryClient.invalidateQueries({ queryKey: keys.dictionaryValues(list.code) });
    void queryClient.invalidateQueries({ queryKey: keys.dictionaries });
    notifySuccess(message);
    setReasonFor(null);
    onClose();
  };

  const input = (): DictionaryValueInput | undefined => {
    const parsed = fromRows(rows);
    if ('error' in parsed) {
      setAttributeError(parsed.error);
      return undefined;
    }
    setAttributeError(null);
    const v = form.getValues();
    return {
      code: v.code,
      label: v.label,
      description: v.description.trim() || null,
      sortOrder: typeof v.sortOrder === 'number' ? v.sortOrder : Number(v.sortOrder || 0),
      siteId: v.siteId,
      attributes: parsed.attributes,
    };
  };

  const save = useMutation({
    mutationFn: ({ data, reason }: { data: DictionaryValueInput; reason?: string }) =>
      existing
        ? dictionariesApi.update(list.code, existing.id, existing.version, data, reason)
        : dictionariesApi.create(list.code, data),
    onSuccess: (v) => done(existing ? `${v.code} saved` : `${v.code} added to ${list.name}`),
    onError: (error) => {
      if (isReasonRequired(error)) {
        setReasonFor('save');
      } else if (error instanceof ApiError && error.status === 400) {
        form.setErrors(toFormErrors(error.errors));
      } else {
        notifyError(error);
      }
    },
  });

  const statusChange = useMutation({
    // the dialog closes and the list reloads afterwards, so no result is needed
    mutationFn: async ({ action, reason }: { action: Exclude<ReasonFor, 'save'>; reason: string }): Promise<void> => {
      const e = existing!;
      if (action === 'retire') {
        await dictionariesApi.retire(list.code, e.id, e.version, reason);
      } else if (action === 'reactivate') {
        await dictionariesApi.reactivate(list.code, e.id, e.version, reason);
      } else {
        await dictionariesApi.remove(list.code, e.id, e.version, reason);
      }
    },
    onSuccess: (_, v) =>
      done(`${existing!.code} ${v.action === 'retire' ? 'retired' : v.action === 'reactivate' ? 'reactivated' : 'deleted'}`),
    onError: notifyError,
  });

  const submit = (reason?: string) => {
    const data = input();
    if (data) {
      save.mutate({ data, reason });
    }
  };

  const siteOptions = me.sites.map((s) => ({ value: s.id, label: `${s.code} — ${s.name}` }));
  const site = existing?.siteId ? me.sites.find((s) => s.id === existing.siteId) : undefined;
  const deleteBlocked = existing?.fromCatalog
    ? 'Shipped with ArrheniQ: retire it instead'
    : existing?.inUse
      ? 'Used by existing records: retire it instead'
      : null;

  return (
    <>
      <Modal
        opened={opened && reasonFor === null}
        onClose={onClose}
        centered
        size="lg"
        title={
          <Group gap="xs">
            <Text fw={600}>{existing ? `${list.name}: ${existing.code}` : `New value in ${list.name}`}</Text>
            {existing && <StatusBadge status={existing.status} />}
            {existing?.inUse && (
              <Badge variant="light" color="arqIndigo">
                In use
              </Badge>
            )}
            {existing?.fromCatalog && (
              <Badge variant="light" color="teal">
                ArrheniQ catalog
              </Badge>
            )}
          </Group>
        }
      >
        <Stack>
          {list.systemManaged && (
            <Alert color="gray">The values of this list are managed by ArrheniQ and can't be changed.</Alert>
          )}
          {existing?.inUse && !readOnly && (
            <Alert color="arqIndigo" variant="light">
              Records use this value. Changes show on all of them, so you'll be asked for a reason.
            </Alert>
          )}
          <Group grow align="flex-start">
            <TextInput
              label="Code"
              description={existing ? 'Fixed: records refer to the code' : 'A-Z, 0-9, . _ / - (e.g. 25C60RH)'}
              disabled={!!existing || readOnly}
              {...form.getInputProps('code')}
            />
            <NumberInput label="Sort order" min={0} max={100000} disabled={readOnly} {...form.getInputProps('sortOrder')} />
          </Group>
          <TextInput label="Label" withAsterisk disabled={readOnly} {...form.getInputProps('label')} />
          <Textarea label="Description" autosize minRows={2} disabled={readOnly} {...form.getInputProps('description')} />
          {existing ? (
            <Text size="sm">
              <Text span c="dimmed">
                Available at:{' '}
              </Text>
              {site ? `${site.code} — ${site.name} only` : 'all sites'}
            </Text>
          ) : (
            <Select
              label="Available at"
              description="Leave empty for all sites; choose a site for site-specific values such as storage locations."
              placeholder="All sites"
              clearable
              data={siteOptions}
              disabled={readOnly}
              {...form.getInputProps('siteId')}
            />
          )}

          <Stack gap={6}>
            <Group justify="space-between">
              <Text size="sm" fw={500}>
                Attributes
              </Text>
              {!readOnly && (
                <Button size="compact-xs" variant="subtle" leftSection={<IconPlus size={14} />}
                        onClick={() => setRows([...rows, { key: '', value: '' }])}>
                  Add attribute
                </Button>
              )}
            </Group>
            {rows.length === 0 && (
              <Text size="xs" c="dimmed">
                None. Storage conditions use e.g. temp_c, rh and role.
              </Text>
            )}
            {rows.map((row, i) => (
              <Group key={i} gap="xs" wrap="nowrap">
                <TextInput
                  placeholder="name (e.g. temp_c)"
                  value={row.key}
                  disabled={readOnly}
                  onChange={(e) => setRows(rows.map((r, j) => (j === i ? { ...r, key: e.currentTarget.value } : r)))}
                  style={{ flex: 1 }}
                />
                <TextInput
                  placeholder="value (e.g. 25)"
                  value={row.value}
                  disabled={readOnly}
                  onChange={(e) => setRows(rows.map((r, j) => (j === i ? { ...r, value: e.currentTarget.value } : r)))}
                  style={{ flex: 1 }}
                />
                {!readOnly && (
                  <ActionIcon variant="subtle" color="gray" aria-label="Remove attribute"
                              onClick={() => setRows(rows.filter((_, j) => j !== i))}>
                    <IconReject size={16} />
                  </ActionIcon>
                )}
              </Group>
            ))}
            {attributeError && (
              <Text size="xs" c="red">
                {attributeError}
              </Text>
            )}
          </Stack>

          <Group justify="space-between" mt="sm">
            <Group gap="xs">
              {existing && canManage && !list.systemManaged && existing.status === 'ACTIVE' && (
                <Button variant="subtle" color="gray" leftSection={<IconArchive />} onClick={() => setReasonFor('retire')}>
                  Retire
                </Button>
              )}
              {existing && canManage && !list.systemManaged && existing.status === 'RETIRED' && (
                <Button variant="subtle" onClick={() => setReasonFor('reactivate')}>
                  Reactivate
                </Button>
              )}
              {existing && canManage && !list.systemManaged && (
                <Tooltip label={deleteBlocked} disabled={!deleteBlocked}>
                  <Button variant="subtle" color="red" data-disabled={deleteBlocked ? true : undefined}
                          onClick={(e) => (deleteBlocked ? e.preventDefault() : setReasonFor('delete'))}>
                    Delete
                  </Button>
                </Tooltip>
              )}
            </Group>
            <Group gap="xs">
              <Button variant="default" onClick={onClose}>
                {readOnly ? 'Close' : 'Cancel'}
              </Button>
              {!readOnly && (
                <Button loading={save.isPending} onClick={() => submit()}>
                  {existing ? 'Save' : 'Add value'}
                </Button>
              )}
            </Group>
          </Group>
        </Stack>
      </Modal>

      <ReasonModal
        opened={reasonFor !== null}
        title={
          reasonFor === 'save'
            ? `Change ${existing?.code ?? ''}`
            : reasonFor === 'retire'
              ? `Retire ${existing?.code ?? ''}`
              : reasonFor === 'reactivate'
                ? `Reactivate ${existing?.code ?? ''}`
                : `Delete ${existing?.code ?? ''}`
        }
        description={
          reasonFor === 'save'
            ? 'Records use this value, so the change is recorded with a reason.'
            : reasonFor === 'retire'
              ? 'Retired values can no longer be chosen for new records. Existing records keep them.'
              : reasonFor === 'reactivate'
                ? 'The value can be chosen again for new records.'
                : 'The value was added by mistake and nothing uses it. It stays in the audit trail.'
        }
        confirmLabel={reasonFor === 'save' ? 'Save' : reasonFor === 'retire' ? 'Retire' : reasonFor === 'reactivate' ? 'Reactivate' : 'Delete'}
        busy={save.isPending || statusChange.isPending}
        onCancel={() => setReasonFor(null)}
        onConfirm={(reason) => {
          if (reasonFor === 'save') {
            submit(reason);
          } else if (reasonFor) {
            statusChange.mutate({ action: reasonFor, reason });
          }
        }}
      />
    </>
  );
}

function toValues(v?: DictionaryValue): Values {
  return {
    code: v?.code ?? '',
    label: v?.label ?? '',
    description: v?.description ?? '',
    sortOrder: v?.sortOrder ?? 0,
    siteId: v?.siteId ?? null,
  };
}
