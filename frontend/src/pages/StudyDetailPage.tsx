import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearch } from '@tanstack/react-router';
import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  Center,
  Grid,
  Group,
  Loader,
  Modal,
  Select,
  SimpleGrid,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  ThemeIcon,
  Title,
  Tooltip,
} from '@mantine/core';
import { ApiError } from '../api/http';
import { dictionariesApi, keys } from '../api/masterdata';
import { studiesApi, studyKeys } from '../api/studies';
import type { DictionaryValue, Signature, StudyDetails, StudySchedule, StudyUpdateInput } from '../api/types';
import { RecordHistory } from '../audit/RecordHistory';
import { useSession } from '../auth/SessionProvider';
import { goToReauth } from '../auth/navigation';
import { formatInstant, label } from '../components/format';
import { toFormErrors } from '../components/formErrors';
import { IconCalendar, IconEdit, IconReject, IconSend, IconSignature, IconStudy } from '../components/icons';
import { notifyError, notifySuccess } from '../components/notify';
import { ReasonModal } from '../components/ReasonModal';
import { StatusBadge } from '../components/StatusBadge';
import { PullDatePreviewCard } from '../scheduling/PullDatePreviewCard';
import { formatDay, ruleText } from '../scheduling/pullDates';
import { rememberPendingSignature, takePendingSignature } from '../signing/pendingSignature';

type ReasonAction = 'reject' | 'discard';

/** One study: details, its other conditions, the schedule (planned or generated), workflow and signatures. */
export function StudyDetailPage() {
  const { studyId = '' } = useParams({ strict: false }) as { studyId?: string };
  const search = useSearch({ strict: false }) as { signing?: string };
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { me, can } = useSession();

  const details = useQuery({ queryKey: studyKeys.study(studyId), queryFn: () => studiesApi.get(studyId) });
  const d = details.data;
  const approved = d?.study.status === 'APPROVED' || d?.study.status === 'COMPLETED';
  const schedule = useQuery({
    queryKey: studyKeys.schedule(studyId),
    queryFn: () => studiesApi.schedule(studyId),
    enabled: !!d && approved && can('schedule.view'),
  });

  const applied = (next: StudyDetails, message: string) => {
    queryClient.setQueryData(studyKeys.study(studyId), next);
    void queryClient.invalidateQueries({ queryKey: studyKeys.all });
    void queryClient.invalidateQueries({ queryKey: studyKeys.schedule(studyId) });
    notifySuccess(message);
  };

  const submit = useMutation({
    mutationFn: () => studiesApi.submit(studyId, d!.version),
    onSuccess: (n) =>
      applied(n, n.study.status === 'APPROVED' ? `${n.study.studyCode} is approved (no approval configured)` : `${n.study.studyCode} submitted for approval`),
    onError: notifyError,
  });
  const [reasonAction, setReasonAction] = useState<ReasonAction | null>(null);
  const withReason = useMutation({
    mutationFn: async ({ action, reason }: { action: ReasonAction; reason: string }): Promise<StudyDetails | null> => {
      if (action === 'reject') return studiesApi.reject(studyId, d!.version, reason);
      await studiesApi.discard(studyId, d!.version, reason);
      return null;
    },
    onSuccess: (n) => {
      setReasonAction(null);
      if (n) {
        applied(n, `${n.study.studyCode} rejected; it is a draft again`);
        return;
      }
      notifySuccess('Draft study discarded');
      void queryClient.invalidateQueries({ queryKey: studyKeys.all });
      void navigate({ to: '/studies' });
    },
    onError: notifyError,
  });

  // ---------------------------------------------------------------- approval (e-signature)
  const [approveOpen, setApproveOpen] = useState(false);
  const [comment, setComment] = useState('');
  const approve = useMutation({
    mutationFn: ({ expectedVersion, note }: { expectedVersion: number; note: string }) =>
      studiesApi.approve(studyId, expectedVersion, note || undefined),
    onSuccess: (n) =>
      applied(
        n,
        n.study.status === 'APPROVED'
          ? `${n.study.studyCode} approved and signed; its pull schedule is generated`
          : `${n.study.studyCode} reviewed and signed; a second approver must now approve it`,
      ),
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'ARQ-ESG-004') {
        notifyError(new ApiError(409, error.code, 'The study changed since you started signing; review it and sign again'));
      } else {
        notifyError(error);
      }
    },
  });
  const signingHandled = useRef(false);
  useEffect(() => {
    if (signingHandled.current || search.signing !== '1') {
      return;
    }
    signingHandled.current = true;
    void navigate({ to: '/studies/$studyId', params: { studyId }, search: {}, replace: true });
    const pending = takePendingSignature('STUDY', studyId);
    if (pending) {
      approve.mutate({ expectedVersion: pending.expectedVersion, note: pending.comment });
    } else {
      notifyError(new ApiError(401, 'ARQ-AUTH-REAUTH', 'The signing request expired; approve again'));
    }
  }, [search.signing, studyId]);

  // ---------------------------------------------------------------- editing a draft
  const [editOpen, setEditOpen] = useState(false);

  if (details.isPending) {
    return (
      <Center h={300}>
        <Loader />
      </Center>
    );
  }
  if (details.isError || !d) {
    return (
      <Alert color="red" title="Study not available">
        {details.error instanceof ApiError && details.error.status === 404
          ? 'This study does not exist or belongs to a site you cannot access.'
          : details.error?.message}
      </Alert>
    );
  }

  const s = d.study;
  const site = me.sites.find((x) => x.id === s.siteId);
  const canCreate = can('study.create');
  const canApprove = can('study.approve');
  const busy = submit.isPending || withReason.isPending || approve.isPending;
  const iAlreadySigned = d.signatures.some(
    (x) => x.status === 'VALID' && x.signerUserId === me.userId && (x.meaning === 'REVIEWED' || x.meaning === 'APPROVED'),
  );
  const nextMeaning = d.approval.required > 1 && d.approval.collected === 0 ? 'Reviewed' : 'Approved';
  const startApproval = () => {
    rememberPendingSignature({ recordType: 'STUDY', recordId: s.id, expectedVersion: d.version, comment: comment.trim(), createdAt: Date.now() });
    goToReauth('SIGN', `/studies/${s.id}?signing=1`, { recordType: 'STUDY', recordId: s.id, recordVersion: d.version });
  };

  return (
    <Stack>
      <Group justify="space-between" align="flex-start">
        <Group wrap="nowrap" align="flex-start">
          <ThemeIcon size={38} radius="md" variant="gradient" gradient={{ from: 'arqIndigo.8', to: 'teal.6', deg: 135 }}
                     className="arq-pop arq-draw arq-tile">
            <IconStudy size={22} />
          </ThemeIcon>
          <Stack gap={2}>
            <Anchor size="xs" onClick={() => void navigate({ to: '/studies' })}>
              ← All studies
            </Anchor>
            <Group gap="sm">
              <Title order={2} fz={20} fw={650}>
                Study {s.studyCode} · lot {s.lotNumber}
              </Title>
              <StatusBadge status={s.status} />
              {s.status === 'IN_REVIEW' && d.approval.required > 1 && (
                <Badge variant="light" color="teal">
                  {d.approval.collected} of {d.approval.required} approvals
                </Badge>
              )}
            </Group>
            <Text size="xs" c="dimmed">
              <Anchor size="xs" onClick={() => void navigate({ to: '/products/$productId', params: { productId: s.productId } })}>
                {s.productCode} · {s.productName}
              </Anchor>
              {' · '}
              {s.conditionLabel}
              {site && ` · ${site.code}`}
            </Text>
          </Stack>
        </Group>
        <Group gap="xs">
          {canCreate && s.status === 'DRAFT' && (
            <>
              <Button variant="subtle" color="red" disabled={busy} onClick={() => setReasonAction('discard')}>
                Discard draft
              </Button>
              <Button variant="default" leftSection={<IconEdit />} disabled={busy} onClick={() => setEditOpen(true)}>
                Edit
              </Button>
              <Button leftSection={<IconSend />} loading={submit.isPending} disabled={busy} onClick={() => submit.mutate()}>
                Submit for approval
              </Button>
            </>
          )}
          {canApprove && s.status === 'IN_REVIEW' && (
            <>
              <Button color="red" variant="light" leftSection={<IconReject />} disabled={busy} onClick={() => setReasonAction('reject')}>
                Reject
              </Button>
              <Tooltip label="You have already signed; a different approver must sign next" disabled={!iAlreadySigned}>
                <Button color="teal" leftSection={<IconSignature />} disabled={busy} loading={approve.isPending}
                        data-disabled={iAlreadySigned || undefined}
                        onClick={(e) => (iAlreadySigned ? e.preventDefault() : setApproveOpen(true))}>
                  {nextMeaning === 'Reviewed' ? 'Review and sign' : 'Approve'}
                </Button>
              </Tooltip>
            </>
          )}
        </Group>
      </Group>

      <Grid>
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Stack>
            <Card withBorder radius="md" className="arq-fade-in">
              <Title order={5} mb="sm">
                Study
              </Title>
              <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="xs" verticalSpacing={6}>
                <Field name="Specification" value={`${s.specNumber} v${s.specVersionNo}`} />
                <Field name="Protocol" value={`${s.protocolNumber} v${s.protocolVersionNo}${s.studyType ? ` · ${label(s.studyType)}` : ''}`}
                       onClick={() => void navigate({ to: '/protocols/$protocolId', params: { protocolId: s.protocolId }, search: { v: s.protocolVersionId } })} />
                <Field name="Condition" value={s.conditionLabel} />
                <Field name="Storage location" value={d.storageLocationLabel} />
                <Field name="On-stability date" value={formatDay(s.onStabilityDate)} />
                <Field name="Reason" value={d.reasonLabel} />
                <Field name="Study length" value={d.studyLengthLabel} />
                <Field name="Units placed"
                       value={`${d.unitsPlaced + d.reserveUnits}${d.fillUnitCode ? ` ${d.fillUnitCode.toLowerCase()}` : ''} (${d.unitsPlaced} for testing + ${d.reserveUnits} reserve)`} />
                <Field name="Manufacture date" value={d.manufactureDate ? formatDay(d.manufactureDate) : '—'} />
                <Field name="Expiry date" value={d.expiryDate ? formatDay(d.expiryDate) : '—'} />
                {d.proprietaryStudyId && <Field name="Your reference" value={d.proprietaryStudyId} />}
              </SimpleGrid>
              {d.notes && (
                <Text size="sm" mt="sm">
                  <Text span c="dimmed">
                    Notes:{' '}
                  </Text>
                  {d.notes}
                </Text>
              )}
            </Card>

            {approved
              ? can('schedule.view') && <ScheduleCard loading={schedule.isPending} data={schedule.data} />
              : can('masterdata.view') && (
                  <PullDatePreviewCard versionId={s.protocolVersionId} revision={d.version} fixedDate={s.onStabilityDate} />
                )}

            <Card withBorder radius="md" className="arq-fade-in">
              <Group gap="xs" mb="sm">
                <ThemeIcon size={28} radius="md" variant="light" color="teal">
                  <IconSignature />
                </ThemeIcon>
                <Title order={5}>Electronic signatures</Title>
              </Group>
              {d.signatures.length === 0 ? (
                <Text size="sm" c="dimmed">
                  Not signed yet.
                </Text>
              ) : (
                <Stack gap="xs">
                  {d.signatures.map((x) => (
                    <SignatureLine key={x.id} signature={x} timeZone={site?.timeZone} />
                  ))}
                </Stack>
              )}
            </Card>

            {can('audit.view') && <RecordHistory recordIds={[s.id]} revision={d.version} timeZone={site?.timeZone} />}
          </Stack>
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 4 }}>
          <Card withBorder radius="md" className="arq-fade-in">
            <Text size="xs" c="dimmed" fw={600} tt="uppercase" mb="xs" style={{ letterSpacing: '0.05em' }}>
              Same lot, other conditions
            </Text>
            {d.siblings.length === 0 ? (
              <Text size="sm" c="dimmed">
                This lot was logged in under one condition.
              </Text>
            ) : (
              <Stack gap={6}>
                {d.siblings.map((x) => (
                  <Group key={x.id} justify="space-between" wrap="nowrap">
                    <div>
                      <Anchor size="sm" fw={600} onClick={() => void navigate({ to: '/studies/$studyId', params: { studyId: x.id } })}>
                        {x.studyCode}
                      </Anchor>
                      <Text size="xs" c="dimmed" lineClamp={1}>
                        {x.conditionLabel}
                      </Text>
                    </div>
                    <StatusBadge status={x.status} />
                  </Group>
                ))}
              </Stack>
            )}
          </Card>
        </Grid.Col>
      </Grid>

      <ReasonModal
        opened={reasonAction !== null}
        title={reasonAction === 'reject' ? `Reject study ${s.studyCode}` : `Discard draft study ${s.studyCode}`}
        description={
          reasonAction === 'reject'
            ? 'The study goes back to draft so it can be corrected.'
            : 'The draft is removed; its study ID is not reused. It stays in the audit trail.'
        }
        confirmLabel={reasonAction === 'reject' ? 'Reject' : 'Discard'}
        busy={withReason.isPending}
        onCancel={() => setReasonAction(null)}
        onConfirm={(reason) => reasonAction && withReason.mutate({ action: reasonAction, reason })}
      />

      <Modal opened={approveOpen} onClose={() => setApproveOpen(false)} title={`Sign study ${s.studyCode}`} centered>
        <Stack>
          <Text size="sm">
            You are signing study {s.studyCode} electronically with the meaning <b>{nextMeaning}</b>.
            {nextMeaning === 'Reviewed' && ' A second, different approver signs Approved afterwards.'} When it is approved,
            its pull schedule is generated and the first pulls become due. Your signature is the legally binding equivalent
            of a handwritten signature (21 CFR Part 11). To confirm it is you, you will be asked to sign in again.
          </Text>
          <Textarea label="Comment (optional)" autosize minRows={2} value={comment} onChange={(e) => setComment(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setApproveOpen(false)}>
              Cancel
            </Button>
            <Button color="teal" leftSection={<IconSignature />} onClick={startApproval}>
              Sign in again and sign
            </Button>
          </Group>
        </Stack>
      </Modal>

      <EditStudy opened={editOpen} details={d} onClose={() => setEditOpen(false)}
                 onSaved={(n) => {
                   setEditOpen(false);
                   applied(n, `Study ${n.study.studyCode} saved`);
                 }} />
    </Stack>
  );
}

function Field({ name, value, onClick }: { name: string; value: string; onClick?: () => void }) {
  return (
    <div>
      <Text size="xs" c="dimmed">
        {name}
      </Text>
      {onClick ? (
        <Anchor size="sm" onClick={onClick}>
          {value}
        </Anchor>
      ) : (
        <Text size="sm">{value}</Text>
      )}
    </div>
  );
}

/** The stored schedule of an approved study (URS-SCH-001/002/004). */
function ScheduleCard({ loading, data }: { loading: boolean; data?: StudySchedule }) {
  return (
    <Card withBorder radius="md" className="arq-fade-in">
      <Group gap="xs" mb="sm">
        <ThemeIcon size={28} radius="md" variant="light" color="teal">
          <IconCalendar />
        </ThemeIcon>
        <div>
          <Title order={5}>Pull schedule</Title>
          <Text size="xs" c="dimmed">
            Generated when the study was approved{data?.algorithm ? ` (${data.algorithm})` : ''}.
          </Text>
        </div>
      </Group>
      {loading || !data ? (
        <Center h={80}>
          <Loader size="sm" />
        </Center>
      ) : (
        <Table verticalSpacing={4} striped>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Time point</Table.Th>
              <Table.Th>Pull date</Table.Th>
              <Table.Th>Window</Table.Th>
              <Table.Th ta="right">Units</Table.Th>
              <Table.Th>Status</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data.timepoints.map((t) => (
              <Table.Tr key={t.id}>
                <Table.Td fw={600}>{t.label}</Table.Td>
                <Table.Td style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {formatDay(t.pullDate)}
                  {t.ruleApplied && (
                    <Text size="xs" c="orange">
                      {formatDay(t.calculatedDate)}: {ruleText(t.ruleApplied)}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <Text size="xs" c="dimmed" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {t.windowFrom === t.windowTo ? 'on the day' : `${formatDay(t.windowFrom)} – ${formatDay(t.windowTo)}`}
                  </Text>
                </Table.Td>
                <Table.Td ta="right">{t.unitsPlanned}</Table.Td>
                <Table.Td>
                  <StatusBadge status={t.status} />
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Card>
  );
}

/** Part 11 §11.50 manifestation: printed name, date/time and meaning. */
function SignatureLine({ signature: x, timeZone }: { signature: Signature; timeZone?: string }) {
  return (
    <Group justify="space-between" wrap="nowrap">
      <Text size="sm">
        {label(x.meaning)} by <b>{x.signerName}</b> on {formatInstant(x.signedAt, timeZone)}
        {x.comment ? ` — “${x.comment}”` : ''}
      </Text>
      <StatusBadge status={x.status} />
    </Group>
  );
}

function listOptions(values: DictionaryValue[] | undefined, siteId: string, current: string) {
  return (values ?? [])
    .filter((v) => (v.status === 'ACTIVE' || v.code === current) && (v.siteId === null || v.siteId === siteId))
    .sort((a, b) => a.sortOrder - b.sortOrder || a.code.localeCompare(b.code))
    .map((v) => ({ value: v.code, label: v.label }));
}

/** Edit a DRAFT study: lot and dates, reason, length, storage location, reference, notes. */
function EditStudy({
  opened,
  details: d,
  onClose,
  onSaved,
}: {
  opened: boolean;
  details: StudyDetails;
  onClose: () => void;
  onSaved: (d: StudyDetails) => void;
}) {
  const initial = (): StudyUpdateInput => ({
    lotNumber: d.study.lotNumber,
    manufactureDate: d.manufactureDate,
    expiryDate: d.expiryDate,
    onStabilityDate: d.study.onStabilityDate,
    reasonCode: d.reasonCode,
    studyLengthCode: d.studyLengthCode,
    storageLocationCode: d.storageLocationCode,
    proprietaryStudyId: d.proprietaryStudyId,
    notes: d.notes,
  });
  const [v, setV] = useState<StudyUpdateInput>(initial);
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (opened) {
      setV(initial());
      setErrors({});
    }
  }, [opened, d.version]);
  const reasons = useQuery({ queryKey: keys.dictionaryValues('STUDY_REASON'), queryFn: () => dictionariesApi.values('STUDY_REASON'), enabled: opened });
  const lengths = useQuery({ queryKey: keys.dictionaryValues('STUDY_LENGTH'), queryFn: () => dictionariesApi.values('STUDY_LENGTH'), enabled: opened });
  const locations = useQuery({
    queryKey: keys.dictionaryValues('STORAGE_LOCATION'),
    queryFn: () => dictionariesApi.values('STORAGE_LOCATION'),
    enabled: opened,
  });
  const save = useMutation({
    mutationFn: () => studiesApi.update(d.study.id, d.version, v),
    onSuccess: onSaved,
    onError: (error) => {
      if (error instanceof ApiError && error.status === 400) {
        setErrors(toFormErrors(error.errors));
      }
      notifyError(error);
    },
  });
  const set = (patch: Partial<StudyUpdateInput>) => setV((x) => ({ ...x, ...patch }));

  return (
    <Modal opened={opened} onClose={onClose} title={`Edit study ${d.study.studyCode}`} centered size="lg">
      <Stack>
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <TextInput label="Lot number" withAsterisk value={v.lotNumber} maxLength={40}
                     onChange={(e) => set({ lotNumber: e.currentTarget.value })} error={errors.lotNumber} />
          <TextInput label="Your study reference" value={v.proprietaryStudyId ?? ''} maxLength={40}
                     onChange={(e) => set({ proprietaryStudyId: e.currentTarget.value || null })} error={errors.proprietaryStudyId} />
          <TextInput type="date" label="Manufacture date" value={v.manufactureDate ?? ''}
                     onChange={(e) => set({ manufactureDate: e.currentTarget.value || null })} error={errors.manufactureDate} />
          <TextInput type="date" label="Expiry date" value={v.expiryDate ?? ''}
                     onChange={(e) => set({ expiryDate: e.currentTarget.value || null })} error={errors.expiryDate} />
          <TextInput type="date" label="On-stability date" withAsterisk value={v.onStabilityDate}
                     onChange={(e) => set({ onStabilityDate: e.currentTarget.value })} error={errors.onStabilityDate} />
          <Select label="Storage location" withAsterisk value={v.storageLocationCode}
                  data={listOptions(locations.data, d.study.siteId, d.storageLocationCode)}
                  onChange={(x) => x && set({ storageLocationCode: x })} error={errors.storageLocationCode} />
          <Select label="Reason for the study" withAsterisk value={v.reasonCode}
                  data={listOptions(reasons.data, d.study.siteId, d.reasonCode)}
                  onChange={(x) => x && set({ reasonCode: x })} error={errors.reasonCode} />
          <Select label="Study length" withAsterisk value={v.studyLengthCode}
                  data={listOptions(lengths.data, d.study.siteId, d.studyLengthCode)}
                  onChange={(x) => x && set({ studyLengthCode: x })} error={errors.studyLengthCode} />
        </SimpleGrid>
        <Textarea label="Notes" autosize minRows={2} maxLength={2000} value={v.notes ?? ''}
                  onChange={(e) => set({ notes: e.currentTarget.value || null })} error={errors.notes} />
        <Text size="xs" c="dimmed">
          Product, specification and protocol are fixed. To change them, discard this draft and log the lot in again.
        </Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={save.isPending} onClick={() => save.mutate()}>
            Save
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
