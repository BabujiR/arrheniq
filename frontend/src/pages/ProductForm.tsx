import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Checkbox, Group, Modal, NumberInput, Select, SimpleGrid, Stack, Textarea, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { ApiError, isReasonRequired } from '../api/http';
import { keys, productsApi } from '../api/masterdata';
import type { ExpirationStatus, ExpirationUnit, Product, ProductDetails, ProductInput, ProductType } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { toFormErrors } from '../components/formErrors';
import { label } from '../components/format';
import { notifyError, notifySuccess } from '../components/notify';
import { ReasonModal } from '../components/ReasonModal';

const TYPES: ProductType[] = ['DRUG_PRODUCT', 'DRUG_SUBSTANCE', 'BIOLOGIC_DP', 'BIOLOGIC_DS', 'EXCIPIENT', 'OTHER'];
const EXP_STATUSES: ExpirationStatus[] = ['ESTABLISHED', 'TO_BE_DETERMINED', 'NOT_APPLICABLE'];
const UNITS: ExpirationUnit[] = ['MONTH', 'WEEK', 'DAY'];

interface Values {
  siteId: string;
  code: string;
  name: string;
  description: string;
  productType: ProductType;
  dosageForm: string;
  strength: string;
  expirationStatus: ExpirationStatus;
  expirationValue: number | string;
  expirationUnit: ExpirationUnit;
  controlledSubstance: boolean;
}

const text = (v: string): string | null => (v.trim() === '' ? null : v.trim());

function toValues(p: Product | undefined, defaultSite: string): Values {
  return {
    siteId: p?.siteId ?? defaultSite,
    code: p?.code ?? '',
    name: p?.name ?? '',
    description: p?.description ?? '',
    productType: p?.productType ?? 'DRUG_PRODUCT',
    dosageForm: p?.dosageForm ?? '',
    strength: p?.strength ?? '',
    expirationStatus: p?.expirationStatus ?? 'ESTABLISHED',
    expirationValue: p?.expirationValue ?? '',
    expirationUnit: p?.expirationUnit ?? 'MONTH',
    controlledSubstance: p?.controlledSubstance ?? false,
  };
}

function toInput(v: Values): ProductInput {
  const established = v.expirationStatus === 'ESTABLISHED';
  return {
    siteId: v.siteId,
    code: v.code,
    name: v.name,
    description: text(v.description),
    productType: v.productType,
    dosageForm: text(v.dosageForm),
    strength: text(v.strength),
    expirationStatus: v.expirationStatus,
    expirationValue: established && typeof v.expirationValue === 'number' ? v.expirationValue : null,
    expirationUnit: established ? v.expirationUnit : null,
    controlledSubstance: v.controlledSubstance,
  };
}

interface Props {
  opened: boolean;
  existing?: Product;
  onClose: () => void;
  onSaved?: (details: ProductDetails) => void;
}

/** Create or edit a product (URS-MD-002). Editing an APPROVED product asks for a reason first. */
export function ProductForm({ opened, existing, onClose, onSaved }: Props) {
  const { me } = useSession();
  const queryClient = useQueryClient();
  const defaultSite = me.sites[0]?.id ?? '';
  const form = useForm<Values>({ initialValues: toValues(existing, defaultSite) });
  const [askReason, setAskReason] = useState(false);
  const approved = existing?.status === 'APPROVED';

  useEffect(() => {
    if (opened) {
      form.setValues(toValues(existing, defaultSite));
      form.clearErrors();
    }
  }, [opened, existing?.id, existing?.version]);

  const save = useMutation({
    mutationFn: (reason?: string) =>
      existing
        ? productsApi.update(existing.id, existing.version, toInput(form.getValues()), reason)
        : productsApi.create(toInput(form.getValues())),
    onSuccess: (d) => {
      void queryClient.invalidateQueries({ queryKey: keys.productsAll });
      queryClient.setQueryData(keys.product(d.product.id), d);
      notifySuccess(existing ? `Product ${d.product.code} saved` : `Product ${d.product.code} created`);
      setAskReason(false);
      onClose();
      onSaved?.(d);
    },
    onError: (error) => {
      if (isReasonRequired(error)) {
        setAskReason(true);
      } else if (error instanceof ApiError && error.status === 400) {
        setAskReason(false);
        form.setErrors(toFormErrors(error.errors));
      } else {
        notifyError(error);
      }
    },
  });

  const established = form.getValues().expirationStatus === 'ESTABLISHED';

  return (
    <>
      <Modal opened={opened} onClose={onClose} size="xl" title={existing ? `Edit ${existing.code}` : 'New product'}>
        <form onSubmit={form.onSubmit(() => (approved ? setAskReason(true) : save.mutate(undefined)))}>
          <Stack>
            {approved && (
              <Alert color="yellow" title="This product is approved">
                Saving a change needs a reason. The current approval signature will be superseded and the product
                goes back to Draft for re-approval.
              </Alert>
            )}
            <SimpleGrid cols={2}>
              <Select
                label="Site"
                required
                allowDeselect={false}
                data={me.sites.map((s) => ({ value: s.id, label: `${s.code} — ${s.name}` }))}
                {...form.getInputProps('siteId')}
              />
              <TextInput label="Code" required {...form.getInputProps('code')} />
              <TextInput label="Name" required {...form.getInputProps('name')} />
              <Select
                label="Product type"
                required
                allowDeselect={false}
                data={TYPES.map((t) => ({ value: t, label: label(t) }))}
                {...form.getInputProps('productType')}
              />
              <TextInput label="Dosage form" {...form.getInputProps('dosageForm')} />
              <TextInput label="Strength" {...form.getInputProps('strength')} />
            </SimpleGrid>
            <Textarea label="Description" autosize minRows={2} {...form.getInputProps('description')} />
            <SimpleGrid cols={3}>
              <Select
                label="Expiration"
                allowDeselect={false}
                data={EXP_STATUSES.map((s) => ({ value: s, label: label(s) }))}
                {...form.getInputProps('expirationStatus')}
              />
              {established && (
                <>
                  <NumberInput label="Period" min={1} max={1200} required {...form.getInputProps('expirationValue')} />
                  <Select
                    label="Unit"
                    allowDeselect={false}
                    data={UNITS.map((u) => ({ value: u, label: label(u) }))}
                    {...form.getInputProps('expirationUnit')}
                  />
                </>
              )}
            </SimpleGrid>
            <Checkbox label="Controlled substance" {...form.getInputProps('controlledSubstance', { type: 'checkbox' })} />
            <Group justify="flex-end">
              <Button variant="default" onClick={onClose}>
                Cancel
              </Button>
              <Button type="submit" loading={save.isPending}>
                Save
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>
      <ReasonModal
        opened={askReason}
        title="Reason for change"
        confirmLabel="Save change"
        busy={save.isPending}
        onCancel={() => setAskReason(false)}
        onConfirm={(reason) => save.mutate(reason)}
      />
    </>
  );
}
