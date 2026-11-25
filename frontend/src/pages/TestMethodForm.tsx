import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Checkbox, Group, Modal, NumberInput, SegmentedControl, SimpleGrid, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { ApiError, isReasonRequired } from '../api/http';
import { keys, testMethodsApi } from '../api/masterdata';
import type { ResultsFormat, TestMethod, TestMethodInput } from '../api/types';
import { toFormErrors } from '../components/formErrors';
import { notifyError, notifySuccess } from '../components/notify';
import { ReasonModal } from '../components/ReasonModal';
import { StatusBadge } from '../components/StatusBadge';

type Num = number | string;

interface Values {
  code: string;
  name: string;
  methodReference: string;
  labNumber: string;
  resultsFormat: ResultsFormat;
  unitCode: string;
  replicates: Num;
  decimals: Num;
  defaultLowerLimit: Num;
  defaultUpperLimit: Num;
  defaultTextLimit: string;
  defaultAlertLower: Num;
  defaultAlertUpper: Num;
  defaultVarianceLowerPct: Num;
  defaultVarianceUpperPct: Num;
  trendable: boolean;
}

const num = (v: Num): number | null => (typeof v === 'number' ? v : v.trim() === '' ? null : Number(v));
const text = (v: string): string | null => (v.trim() === '' ? null : v.trim());
const toNum = (v: number | null | undefined): Num => (v ?? '');

function toValues(t?: TestMethod): Values {
  return {
    code: t?.code ?? '',
    name: t?.name ?? '',
    methodReference: t?.methodReference ?? '',
    labNumber: t?.labNumber ?? '',
    resultsFormat: t?.resultsFormat ?? 'NUMERIC',
    unitCode: t?.unitCode ?? '',
    replicates: t?.replicates ?? 1,
    decimals: toNum(t?.decimals),
    defaultLowerLimit: toNum(t?.defaultLowerLimit),
    defaultUpperLimit: toNum(t?.defaultUpperLimit),
    defaultTextLimit: t?.defaultTextLimit ?? '',
    defaultAlertLower: toNum(t?.defaultAlertLower),
    defaultAlertUpper: toNum(t?.defaultAlertUpper),
    defaultVarianceLowerPct: toNum(t?.defaultVarianceLowerPct),
    defaultVarianceUpperPct: toNum(t?.defaultVarianceUpperPct),
    trendable: t?.trendable ?? true,
  };
}

/** Only the attributes of the chosen results format are sent (the server rejects the others). */
function toInput(v: Values): TestMethodInput {
  const numeric = v.resultsFormat === 'NUMERIC';
  return {
    code: v.code,
    name: v.name,
    methodReference: text(v.methodReference),
    labNumber: text(v.labNumber),
    resultsFormat: v.resultsFormat,
    replicates: num(v.replicates),
    trendable: v.trendable,
    unitCode: numeric ? text(v.unitCode) : null,
    decimals: numeric ? num(v.decimals) : null,
    defaultLowerLimit: numeric ? num(v.defaultLowerLimit) : null,
    defaultUpperLimit: numeric ? num(v.defaultUpperLimit) : null,
    defaultAlertLower: numeric ? num(v.defaultAlertLower) : null,
    defaultAlertUpper: numeric ? num(v.defaultAlertUpper) : null,
    defaultVarianceLowerPct: numeric ? num(v.defaultVarianceLowerPct) : null,
    defaultVarianceUpperPct: numeric ? num(v.defaultVarianceUpperPct) : null,
    defaultTextLimit: numeric ? null : text(v.defaultTextLimit),
  };
}

interface Props {
  opened: boolean;
  existing?: TestMethod;
  canManage: boolean;
  onClose: () => void;
}

export function TestMethodForm({ opened, existing, canManage, onClose }: Props) {
  const queryClient = useQueryClient();
  const form = useForm<Values>({ initialValues: toValues(existing) });
  const [reasonFor, setReasonFor] = useState<'save' | 'retire' | null>(null);
  const readOnly = !canManage || existing?.status === 'RETIRED';

  useEffect(() => {
    if (opened) {
      form.setValues(toValues(existing));
      form.resetDirty(toValues(existing));
      form.clearErrors();
    }
    // reset whenever the dialog opens (again) for a record
  }, [opened, existing?.id, existing?.version]);

  const save = useMutation({
    mutationFn: (reason?: string) =>
      existing
        ? testMethodsApi.update(existing.id, existing.version, toInput(form.getValues()), reason)
        : testMethodsApi.create(toInput(form.getValues())),
    onSuccess: (t) => {
      void queryClient.invalidateQueries({ queryKey: keys.testMethodsAll });
      notifySuccess(existing ? `Test ${t.code} saved` : `Test ${t.code} created`);
      setReasonFor(null);
      onClose();
    },
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

  const retire = useMutation({
    mutationFn: (reason: string) => testMethodsApi.retire(existing!.id, existing!.version, reason),
    onSuccess: (t) => {
      void queryClient.invalidateQueries({ queryKey: keys.testMethodsAll });
      notifySuccess(`Test ${t.code} retired`);
      setReasonFor(null);
      onClose();
    },
    onError: notifyError,
  });

  const numeric = form.getValues().resultsFormat === 'NUMERIC';

  return (
    <>
      <Modal opened={opened} onClose={onClose} size="xl" title={existing ? `Test ${existing.code}` : 'New test'}>
        <form onSubmit={form.onSubmit(() => save.mutate(undefined))}>
          <Stack>
            {existing && (
              <Group gap="xs">
                <StatusBadge status={existing.status} />
                <Text size="xs" c="dimmed">
                  Version {existing.version}
                </Text>
              </Group>
            )}
            <SimpleGrid cols={2}>
              <TextInput label="Code" required disabled={readOnly} {...form.getInputProps('code')} />
              <TextInput label="Name" required disabled={readOnly} {...form.getInputProps('name')} />
              <TextInput label="Method reference" disabled={readOnly} {...form.getInputProps('methodReference')} />
              <TextInput label="Lab number" disabled={readOnly} {...form.getInputProps('labNumber')} />
            </SimpleGrid>
            <Group align="flex-end">
              <div>
                <Text size="sm" fw={500} mb={4}>
                  Results format
                </Text>
                <SegmentedControl
                  disabled={readOnly}
                  data={[
                    { value: 'NUMERIC', label: 'Numeric' },
                    { value: 'TEXT', label: 'Text' },
                  ]}
                  value={form.getValues().resultsFormat}
                  onChange={(v) => form.setFieldValue('resultsFormat', v as ResultsFormat)}
                />
              </div>
              <NumberInput label="Replicates" min={1} max={100} w={120} disabled={readOnly} {...form.getInputProps('replicates')} />
              <Checkbox label="Trendable" disabled={readOnly} {...form.getInputProps('trendable', { type: 'checkbox' })} />
            </Group>
            {numeric ? (
              <SimpleGrid cols={4}>
                <TextInput label="Unit" disabled={readOnly} {...form.getInputProps('unitCode')} />
                <NumberInput label="Decimals" min={0} max={10} disabled={readOnly} {...form.getInputProps('decimals')} />
                <NumberInput label="Lower limit" disabled={readOnly} {...form.getInputProps('defaultLowerLimit')} />
                <NumberInput label="Upper limit" disabled={readOnly} {...form.getInputProps('defaultUpperLimit')} />
                <NumberInput label="Alert lower" disabled={readOnly} {...form.getInputProps('defaultAlertLower')} />
                <NumberInput label="Alert upper" disabled={readOnly} {...form.getInputProps('defaultAlertUpper')} />
                <NumberInput label="Variance lower %" min={0} disabled={readOnly} {...form.getInputProps('defaultVarianceLowerPct')} />
                <NumberInput label="Variance upper %" min={0} disabled={readOnly} {...form.getInputProps('defaultVarianceUpperPct')} />
              </SimpleGrid>
            ) : (
              <TextInput label="Text limit" disabled={readOnly} {...form.getInputProps('defaultTextLimit')} />
            )}
            <Group justify="space-between">
              <div>
                {existing && canManage && existing.status === 'ACTIVE' && (
                  <Button color="red" variant="light" onClick={() => setReasonFor('retire')}>
                    Retire
                  </Button>
                )}
              </div>
              <Group>
                <Button variant="default" onClick={onClose}>
                  {readOnly ? 'Close' : 'Cancel'}
                </Button>
                {!readOnly && (
                  <Button type="submit" loading={save.isPending}>
                    Save
                  </Button>
                )}
              </Group>
            </Group>
          </Stack>
        </form>
      </Modal>
      <ReasonModal
        opened={reasonFor !== null}
        title={reasonFor === 'retire' ? 'Retire test' : 'Reason for change'}
        description={reasonFor === 'retire' ? 'Retired tests can no longer be added to specifications.' : undefined}
        confirmLabel={reasonFor === 'retire' ? 'Retire' : 'Save'}
        busy={save.isPending || retire.isPending}
        onCancel={() => setReasonFor(null)}
        onConfirm={(reason) => (reasonFor === 'retire' ? retire.mutate(reason) : save.mutate(reason))}
      />
    </>
  );
}
