import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearch } from '@tanstack/react-router';
import {
  Alert,
  Anchor,
  Button,
  Card,
  Checkbox,
  Group,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core';
import { ApiError } from '../api/http';
import { dictionariesApi, keys, productsApi, protocolsApi, specificationsApi } from '../api/masterdata';
import { studiesApi, studyKeys } from '../api/studies';
import type { DictionaryValue } from '../api/types';
import { toFormErrors } from '../components/formErrors';
import { IconStudy } from '../components/icons';
import { notifyError, notifySuccess } from '../components/notify';
import { PageHeader } from '../components/PageHeader';
import { todayIso } from '../scheduling/pullDates';

interface Chosen {
  protocolVersionId: string;
  storageLocationCode: string | null;
}

/** Active values of a list for a site: tenant-wide values and the site's own. */
function options(values: DictionaryValue[] | undefined, siteId?: string) {
  return (values ?? [])
    .filter((v) => v.status === 'ACTIVE' && (v.siteId === null || v.siteId === siteId))
    .sort((a, b) => a.sortOrder - b.sortOrder || a.code.localeCompare(b.code))
    .map((v) => ({ value: v.code, label: v.label }));
}

/**
 * Study login (URS-STU-001/004): a lot of an approved product, the effective specification, and one or more
 * conditions, each from an approved protocol, with the chamber it's stored in. Creates one draft study per condition.
 */
export function StudyLoginPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const search = useSearch({ strict: false }) as { productId?: string };

  const [productId, setProductId] = useState<string | null>(search.productId ?? null);
  const [specificationId, setSpecificationId] = useState<string | null>(null);
  const [lotNumber, setLotNumber] = useState('');
  const [manufactureDate, setManufactureDate] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [onStabilityDate, setOnStabilityDate] = useState(todayIso());
  const [reasonCode, setReasonCode] = useState<string | null>(null);
  const [studyLengthCode, setStudyLengthCode] = useState<string | null>(null);
  const [proprietaryStudyId, setProprietaryStudyId] = useState('');
  const [notes, setNotes] = useState('');
  const [chosen, setChosen] = useState<Chosen[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const products = useQuery({ queryKey: keys.products({ status: 'APPROVED' }), queryFn: () => productsApi.list({ status: 'APPROVED' }) });
  const product = products.data?.find((p) => p.id === productId);
  const specs = useQuery({
    queryKey: keys.specifications({ productId: productId ?? '' }),
    queryFn: () => specificationsApi.list({ productId: productId ?? '' }),
    enabled: !!productId,
  });
  const protocols = useQuery({
    queryKey: keys.protocols({ productId: productId ?? '' }),
    queryFn: () => protocolsApi.list({ productId: productId ?? '' }),
    enabled: !!productId,
  });
  const reasons = useQuery({ queryKey: keys.dictionaryValues('STUDY_REASON'), queryFn: () => dictionariesApi.values('STUDY_REASON') });
  const lengths = useQuery({ queryKey: keys.dictionaryValues('STUDY_LENGTH'), queryFn: () => dictionariesApi.values('STUDY_LENGTH') });
  const locations = useQuery({
    queryKey: keys.dictionaryValues('STORAGE_LOCATION'),
    queryFn: () => dictionariesApi.values('STORAGE_LOCATION'),
  });
  const conditions = useQuery({
    queryKey: keys.dictionaryValues('STORAGE_CONDITION'),
    queryFn: () => dictionariesApi.values('STORAGE_CONDITION'),
  });

  // a new product: start over with its specification and protocols
  useEffect(() => {
    setSpecificationId(null);
    setChosen([]);
  }, [productId]);
  const effectiveSpecs = (specs.data ?? []).filter((s) => s.effectiveVersionId);
  useEffect(() => {
    if (!specificationId && effectiveSpecs.length === 1) {
      setSpecificationId(effectiveSpecs[0]!.id);
    }
  }, [specs.data]);

  const approvedProtocols = (protocols.data ?? []).filter((p) => p.approvedVersionId);
  const locationOptions = options(locations.data, product?.siteId);
  const conditionLabel = useMemo(() => {
    const m = new Map((conditions.data ?? []).map((c) => [c.code, c.label]));
    return (code: string | null) => (code ? (m.get(code) ?? code) : 'No condition set');
  }, [conditions.data]);

  const toggle = (protocolVersionId: string, on: boolean) =>
    setChosen((c) => (on ? [...c, { protocolVersionId, storageLocationCode: null }] : c.filter((x) => x.protocolVersionId !== protocolVersionId)));
  const setLocation = (protocolVersionId: string, code: string | null) =>
    setChosen((c) => c.map((x) => (x.protocolVersionId === protocolVersionId ? { ...x, storageLocationCode: code } : x)));

  const login = useMutation({
    mutationFn: () => {
      const spec = effectiveSpecs.find((s) => s.id === specificationId);
      return studiesApi.login({
        productId: productId ?? '',
        specVersionId: spec?.effectiveVersionId ?? '',
        lotNumber,
        manufactureDate: manufactureDate || null,
        expiryDate: expiryDate || null,
        onStabilityDate,
        reasonCode,
        studyLengthCode,
        proprietaryStudyId: proprietaryStudyId || null,
        notes: notes || null,
        conditions: chosen,
      });
    },
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: studyKeys.all });
      notifySuccess(
        created.length === 1
          ? `Study ${created[0]!.studyCode} logged in as a draft`
          : `Studies ${created.map((s) => s.studyCode).join(', ')} logged in as drafts`,
      );
      void navigate({ to: '/studies/$studyId', params: { studyId: created[0]!.id } });
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 400 && error.errors.length > 0) {
        setErrors(toFormErrors(error.errors));
      }
      notifyError(error);
    },
  });

  // field errors for a chosen condition come back by its position in the request
  const conditionError = (protocolVersionId: string, field: string) => {
    const i = chosen.findIndex((c) => c.protocolVersionId === protocolVersionId);
    return i < 0 ? undefined : errors[`conditions[${i}].${field}`];
  };

  return (
    <>
      <PageHeader
        icon={<IconStudy size={24} />}
        title="Log in study"
        description="Place a lot on stability. Each condition you choose becomes its own study with its own study ID."
      />
      <Stack maw={1100}>
        <Card withBorder radius="md">
          <Title order={5} mb="sm">
            Product and lot
          </Title>
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <Select label="Product" withAsterisk searchable value={productId} onChange={setProductId}
                    data={(products.data ?? []).map((p) => ({ value: p.id, label: `${p.code} · ${p.name}` }))}
                    placeholder={products.isPending ? 'Loading…' : 'An approved product'}
                    nothingFoundMessage="No approved product" error={errors.productId} />
            <Select label="Specification" withAsterisk value={specificationId} onChange={setSpecificationId}
                    disabled={!productId}
                    data={effectiveSpecs.map((s) => ({ value: s.id, label: `${s.specNumber} v${s.effectiveVersionNo} · ${s.title}` }))}
                    placeholder={productId && specs.data && effectiveSpecs.length === 0 ? 'No effective specification' : 'The effective version is used'}
                    error={errors.specVersionId} />
            <TextInput label="Lot number" withAsterisk value={lotNumber} maxLength={40}
                       onChange={(e) => setLotNumber(e.currentTarget.value)} error={errors.lotNumber} />
            <TextInput label="Your study reference" description="Optional, e.g. a project or protocol number"
                       value={proprietaryStudyId} maxLength={40} inputWrapperOrder={['label', 'input', 'description', 'error']}
                       onChange={(e) => setProprietaryStudyId(e.currentTarget.value)} error={errors.proprietaryStudyId} />
            <TextInput type="date" label="Manufacture date" value={manufactureDate}
                       onChange={(e) => setManufactureDate(e.currentTarget.value)} error={errors.manufactureDate} />
            <TextInput type="date" label="Expiry date" value={expiryDate}
                       onChange={(e) => setExpiryDate(e.currentTarget.value)} error={errors.expiryDate} />
          </SimpleGrid>
        </Card>

        <Card withBorder radius="md">
          <Title order={5} mb="sm">
            Placement
          </Title>
          <SimpleGrid cols={{ base: 1, sm: 3 }}>
            <TextInput type="date" label="On-stability date" withAsterisk value={onStabilityDate}
                       description="Time point 0; pull dates count from here" inputWrapperOrder={['label', 'input', 'description', 'error']}
                       onChange={(e) => setOnStabilityDate(e.currentTarget.value)} error={errors.onStabilityDate} />
            <Select label="Reason for the study" withAsterisk value={reasonCode} onChange={setReasonCode}
                    data={options(reasons.data, product?.siteId)} error={errors.reasonCode} />
            <Select label="Study length" withAsterisk value={studyLengthCode} onChange={setStudyLengthCode}
                    data={options(lengths.data, product?.siteId)} error={errors.studyLengthCode} />
          </SimpleGrid>
        </Card>

        <Card withBorder radius="md">
          <Title order={5}>Conditions</Title>
          <Text size="xs" c="dimmed" mb="sm">
            Choose the approved protocols to run. Each defines a storage condition and its time points.
          </Text>
          {errors.conditions && (
            <Alert color="red" mb="sm">
              {errors.conditions}
            </Alert>
          )}
          {!productId ? (
            <Text size="sm" c="dimmed">
              Choose a product first.
            </Text>
          ) : approvedProtocols.length === 0 ? (
            <Text size="sm" c="dimmed">
              This product has no approved protocol yet.{' '}
              <Anchor size="sm" onClick={() => void navigate({ to: '/protocols', search: { new: '1', productId } })}>
                Create one
              </Anchor>
            </Text>
          ) : (
            <>
              {locationOptions.length === 0 && (
                <Alert color="orange" mb="sm">
                  There are no storage locations for this site yet. Add your chambers in{' '}
                  <Anchor size="sm" onClick={() => void navigate({ to: '/lists' })}>
                    Lists → Storage location
                  </Anchor>
                  .
                </Alert>
              )}
              <Table verticalSpacing="xs">
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th w={40} />
                    <Table.Th>Protocol</Table.Th>
                    <Table.Th>Condition</Table.Th>
                    <Table.Th w={280}>Storage location</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {approvedProtocols.map((p) => {
                    const versionId = p.approvedVersionId!;
                    const row = chosen.find((c) => c.protocolVersionId === versionId);
                    const protocolError = conditionError(versionId, 'protocolVersionId');
                    return (
                      <Table.Tr key={p.id}>
                        <Table.Td>
                          <Checkbox aria-label={`Use ${p.protocolNumber}`} checked={!!row}
                                    onChange={(e) => toggle(versionId, e.currentTarget.checked)} />
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm" fw={600}>
                            {p.protocolNumber} v{p.approvedVersionNo}
                          </Text>
                          <Text size="xs" c={protocolError ? 'red' : 'dimmed'}>
                            {protocolError ?? p.name}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm">{conditionLabel(p.conditionCode)}</Text>
                        </Table.Td>
                        <Table.Td>
                          {row && (
                            <Select size="xs" placeholder="Chamber" value={row.storageLocationCode} data={locationOptions}
                                    onChange={(v) => setLocation(versionId, v)}
                                    error={conditionError(versionId, 'storageLocationCode')} />
                          )}
                        </Table.Td>
                      </Table.Tr>
                    );
                  })}
                </Table.Tbody>
              </Table>
            </>
          )}
        </Card>

        <Card withBorder radius="md">
          <Textarea label="Notes" autosize minRows={2} maxLength={2000} value={notes}
                    onChange={(e) => setNotes(e.currentTarget.value)} error={errors.notes} />
        </Card>

        <Group justify="flex-end">
          <Button variant="default" onClick={() => void navigate({ to: '/studies' })}>
            Cancel
          </Button>
          <Button leftSection={<IconStudy />} loading={login.isPending} disabled={chosen.length === 0}
                  onClick={() => {
                    setErrors({});
                    login.mutate();
                  }}>
            {chosen.length > 1 ? `Log in ${chosen.length} studies` : 'Log in study'}
          </Button>
        </Group>
      </Stack>
    </>
  );
}
