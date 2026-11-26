import { useEffect, useMemo, useRef, useState } from 'react';
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
  Stack,
  Switch,
  Text,
  Textarea,
  ThemeIcon,
  Title,
  Tooltip,
  UnstyledButton,
} from '@mantine/core';
import { ApiError } from '../api/http';
import { keys, specificationsApi, testMethodsApi } from '../api/masterdata';
import type { Signature, SpecTest, SpecVersionDetails } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { goToReauth } from '../auth/navigation';
import { formatInstant, label } from '../components/format';
import { IconArchive, IconEdit, IconPlus, IconReject, IconSend, IconSignature, IconSpec } from '../components/icons';
import { notifyError, notifySuccess } from '../components/notify';
import { ReasonModal } from '../components/ReasonModal';
import { StatusBadge } from '../components/StatusBadge';
import { toFormErrors } from '../components/formErrors';
import { rememberPendingSignature, takePendingSignature } from '../signing/pendingSignature';
import { compareVersions } from '../spec/limits';
import { SpecTestEditor } from '../spec/SpecTestEditor';

type ReasonAction = 'reject' | 'retire' | 'discard';

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'gray',
  IN_REVIEW: 'yellow',
  EFFECTIVE: 'green',
  SUPERSEDED: 'orange',
  RETIRED: 'dark',
};

/** One specification: version history, the selected version's tests (editable as a draft), workflow and signatures. */
export function SpecificationDetailPage() {
  const { specId = '' } = useParams({ strict: false }) as { specId?: string };
  const search = useSearch({ strict: false }) as { v?: string; signing?: string };
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { me, can } = useSession();

  const details = useQuery({ queryKey: keys.specification(specId), queryFn: () => specificationsApi.get(specId) });
  const spec = details.data?.specification;
  const versions = details.data?.versions ?? [];
  const selectedId = search.v ?? spec?.openVersionId ?? versions[0]?.id;
  const version = useQuery({
    queryKey: keys.specVersion(selectedId ?? ''),
    queryFn: () => specificationsApi.version(selectedId!),
    enabled: !!selectedId,
  });
  const v = version.data;
  const [compare, setCompare] = useState(true);
  const previous = useQuery({
    queryKey: keys.specVersion(v?.previousVersionId ?? ''),
    queryFn: () => specificationsApi.version(v!.previousVersionId!),
    enabled: !!v?.previousVersionId && compare,
  });
  const library = useQuery({
    queryKey: keys.testMethods({}),
    queryFn: () => testMethodsApi.list({}),
    enabled: v?.status === 'DRAFT',
  });

  const canManage = can('spec.manage');
  const canApprove = can('spec.approve');
  const editable = canManage && v?.status === 'DRAFT';

  // ---------------------------------------------------------------- draft editing
  const [rows, setRows] = useState<SpecTest[]>([]);
  const [summary, setSummary] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (v) {
      setRows(v.tests);
      setSummary(v.changeSummary ?? '');
      setErrors({});
    }
  }, [v?.id, v?.version]);
  const dirty = !!v && (summary !== (v.changeSummary ?? '') || JSON.stringify(rows) !== JSON.stringify(v.tests));

  const select = (versionId: string) =>
    void navigate({ to: '/specifications/$specId', params: { specId }, search: { v: versionId }, replace: true });

  const applied = (d: SpecVersionDetails, message: string) => {
    queryClient.setQueryData(keys.specVersion(d.id), d);
    void queryClient.invalidateQueries({ queryKey: keys.specification(specId) });
    void queryClient.invalidateQueries({ queryKey: keys.specificationsAll });
    notifySuccess(message);
  };

  const save = useMutation({
    mutationFn: () =>
      specificationsApi.save(
        v!.id,
        v!.version,
        summary.trim() || null,
        rows.map((r) => ({
          testMethodId: r.testMethodId,
          displayName: r.displayName,
          unitCode: r.unitCode,
          replicates: r.replicates,
          decimals: r.decimals,
          lowerLimit: r.lowerLimit,
          upperLimit: r.upperLimit,
          textLimit: r.textLimit,
          alertLower: r.alertLower,
          alertUpper: r.alertUpper,
          trendable: r.trendable,
        })),
      ),
    onSuccess: (d) => applied(d, `Draft v${d.versionNo} saved`),
    onError: (error) => {
      if (error instanceof ApiError && error.status === 400) {
        setErrors(toFormErrors(error.errors));
        notifyError(new ApiError(400, error.code, 'Some limits need attention (marked in red)'));
      } else {
        notifyError(error);
      }
    },
  });
  const submit = useMutation({
    mutationFn: () => specificationsApi.submit(v!.id, v!.version),
    onSuccess: (d) =>
      applied(d, d.status === 'EFFECTIVE' ? `v${d.versionNo} is effective (no approval configured)` : `v${d.versionNo} submitted for approval`),
    onError: (error) => {
      if (error instanceof ApiError && error.status === 400) {
        setErrors(toFormErrors(error.errors));
      }
      notifyError(error);
    },
  });
  const newVersion = useMutation({
    mutationFn: () => specificationsApi.newVersion(specId),
    onSuccess: (d) => {
      applied(d, `Draft v${d.versionNo} created from the current version`);
      select(d.id);
    },
    onError: notifyError,
  });
  const [reasonAction, setReasonAction] = useState<ReasonAction | null>(null);
  const withReason = useMutation({
    mutationFn: async ({ action, reason }: { action: ReasonAction; reason: string }): Promise<SpecVersionDetails | null> => {
      if (action === 'reject') return specificationsApi.reject(v!.id, v!.version, reason);
      if (action === 'retire') return specificationsApi.retire(v!.id, v!.version, reason);
      await specificationsApi.discard(v!.id, v!.version, reason);
      return null;
    },
    onSuccess: (d, { action }) => {
      setReasonAction(null);
      if (d) {
        applied(d, `v${d.versionNo} ${action === 'reject' ? 'rejected' : 'retired'}`);
        return;
      }
      notifySuccess('Draft discarded');
      void queryClient.invalidateQueries({ queryKey: keys.specificationsAll });
      if (versions.length <= 1) {
        void navigate({ to: '/specifications' });
      } else {
        void queryClient.invalidateQueries({ queryKey: keys.specification(specId) });
        void navigate({ to: '/specifications/$specId', params: { specId }, search: {}, replace: true });
      }
    },
    onError: notifyError,
  });

  // ---------------------------------------------------------------- approval (e-signature, ADR 0019)
  const [approveOpen, setApproveOpen] = useState(false);
  const [comment, setComment] = useState('');
  const approve = useMutation({
    mutationFn: ({ id, expectedVersion, note }: { id: string; expectedVersion: number; note: string }) =>
      specificationsApi.approve(id, expectedVersion, note || undefined),
    onSuccess: (d) =>
      applied(
        d,
        d.status === 'EFFECTIVE'
          ? `v${d.versionNo} approved and signed: effective from ${d.effectiveFrom ?? 'today'}`
          : `v${d.versionNo} reviewed and signed; a second approver must now approve it`,
      ),
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'ARQ-ESG-004') {
        notifyError(new ApiError(409, error.code, 'The version changed since you started signing; review it and sign again'));
      } else {
        notifyError(error);
      }
    },
  });
  const signingHandled = useRef(false);
  useEffect(() => {
    if (signingHandled.current || search.signing !== '1' || !selectedId) {
      return;
    }
    signingHandled.current = true;
    void navigate({ to: '/specifications/$specId', params: { specId }, search: { v: selectedId }, replace: true });
    const pending = takePendingSignature('SPEC_VERSION', selectedId);
    if (pending) {
      approve.mutate({ id: pending.recordId, expectedVersion: pending.expectedVersion, note: pending.comment });
    } else {
      notifyError(new ApiError(401, 'ARQ-AUTH-REAUTH', 'The signing request expired; approve again'));
    }
  }, [search.signing, selectedId]);

  const changes = useMemo(
    () => (compare && previous.data && v ? compareVersions(previous.data.tests, editable ? rows : v.tests) : undefined),
    [compare, previous.data, v, rows, editable],
  );

  if (details.isPending) {
    return (
      <Center h={300}>
        <Loader />
      </Center>
    );
  }
  if (details.isError || !spec) {
    return (
      <Alert color="red" title="Specification not available">
        {details.error instanceof ApiError && details.error.status === 404
          ? 'This specification does not exist or belongs to a site you cannot access.'
          : details.error?.message}
      </Alert>
    );
  }

  const site = me.sites.find((s) => s.id === spec.siteId);
  const busy = save.isPending || submit.isPending || withReason.isPending || approve.isPending || newVersion.isPending;
  const iAlreadySigned = !!v?.signatures.some(
    (s) => s.status === 'VALID' && s.signerUserId === me.userId && (s.meaning === 'REVIEWED' || s.meaning === 'APPROVED'),
  );
  const nextMeaning = v && v.approval.required > 1 && v.approval.collected === 0 ? 'Reviewed' : 'Approved';

  const startApproval = () => {
    if (!v) return;
    rememberPendingSignature({
      recordType: 'SPEC_VERSION',
      recordId: v.id,
      expectedVersion: v.version,
      comment: comment.trim(),
      createdAt: Date.now(),
    });
    goToReauth('SIGN', `/specifications/${specId}?v=${v.id}&signing=1`, {
      recordType: 'SPEC_VERSION',
      recordId: v.id,
      recordVersion: v.version,
    });
  };

  return (
    <Stack>
      <Group justify="space-between" align="flex-start">
        <Group wrap="nowrap" align="flex-start">
          <ThemeIcon size={38} radius="md" variant="gradient" gradient={{ from: 'arqIndigo.8', to: 'teal.6', deg: 135 }}
                     className="arq-pop arq-draw arq-tile">
            <IconSpec size={22} />
          </ThemeIcon>
          <Stack gap={2}>
            <Anchor size="xs" onClick={() => void navigate({ to: '/specifications' })}>
              ← All specifications
            </Anchor>
            <Title order={2} fz={20} fw={650}>
              {spec.specNumber} · {spec.title}
            </Title>
            <Text size="xs" c="dimmed">
              Product{' '}
              <Anchor size="xs" onClick={() => void navigate({ to: '/products/$productId', params: { productId: spec.productId } })}>
                {spec.productCode} · {spec.productName}
              </Anchor>
              {site && ` · ${site.code}`}
              {spec.effectiveVersionNo
                ? ` · effective: v${spec.effectiveVersionNo} since ${spec.effectiveFrom}`
                : ' · not effective yet'}
            </Text>
          </Stack>
        </Group>
        {canManage && !spec.openVersionId && versions.length > 0 && (
          <Button variant="light" leftSection={<IconPlus />} loading={newVersion.isPending} disabled={busy}
                  onClick={() => newVersion.mutate()}>
            New version
          </Button>
        )}
      </Group>

      <Grid>
        <Grid.Col span={{ base: 12, md: 3 }}>
          <Card withBorder radius="md" className="arq-fade-in">
            <Text size="xs" c="dimmed" fw={600} tt="uppercase" mb="xs" style={{ letterSpacing: '0.05em' }}>
              Versions
            </Text>
            <Stack gap={4}>
              {versions.map((sv) => (
                <UnstyledButton
                  key={sv.id}
                  onClick={() => select(sv.id)}
                  p="xs"
                  style={{
                    borderRadius: 8,
                    background: sv.id === selectedId ? 'var(--mantine-color-arqIndigo-light)' : undefined,
                  }}
                >
                  <Group justify="space-between" wrap="nowrap">
                    <Text size="sm" fw={600}>
                      v{sv.versionNo}
                    </Text>
                    <Badge size="xs" variant="light" color={STATUS_COLOR[sv.status] ?? 'gray'}>
                      {label(sv.status)}
                    </Badge>
                  </Group>
                  <Text size="xs" c="dimmed" lineClamp={2}>
                    {sv.effectiveFrom ? `Effective ${sv.effectiveFrom} · ` : ''}
                    {sv.testCount} tests{sv.changeSummary ? ` · ${sv.changeSummary}` : ''}
                  </Text>
                </UnstyledButton>
              ))}
            </Stack>
          </Card>
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 9 }}>
          {!v ? (
            <Center h={200}>
              <Loader />
            </Center>
          ) : (
            <Stack>
              <Card withBorder radius="md" className="arq-fade-in">
                <Group justify="space-between" wrap="wrap" gap="sm">
                  <Group gap="sm">
                    <Title order={4}>Version {v.versionNo}</Title>
                    <StatusBadge status={v.status} />
                    {v.effectiveFrom && (
                      <Text size="sm" c="dimmed">
                        effective from {v.effectiveFrom}
                      </Text>
                    )}
                    {v.status === 'IN_REVIEW' && v.approval.required > 1 && (
                      <Badge variant="light" color="teal">
                        {v.approval.collected} of {v.approval.required} approvals
                      </Badge>
                    )}
                  </Group>
                  <Group gap="xs">
                    {editable && (
                      <>
                        <Button variant="subtle" color="red" disabled={busy} onClick={() => setReasonAction('discard')}>
                          Discard draft
                        </Button>
                        <Button variant="default" leftSection={<IconEdit />} disabled={!dirty || busy}
                                loading={save.isPending} onClick={() => save.mutate()}>
                          Save draft
                        </Button>
                        <Tooltip label="Save your changes first" disabled={!dirty}>
                          <Button leftSection={<IconSend />} loading={submit.isPending}
                                  disabled={busy || rows.length === 0}
                                  data-disabled={dirty || undefined}
                                  onClick={(e) => (dirty ? e.preventDefault() : submit.mutate())}>
                            Submit for approval
                          </Button>
                        </Tooltip>
                      </>
                    )}
                    {canApprove && v.status === 'IN_REVIEW' && (
                      <>
                        <Button color="red" variant="light" leftSection={<IconReject />} disabled={busy}
                                onClick={() => setReasonAction('reject')}>
                          Reject
                        </Button>
                        <Tooltip label="You have already signed; a different approver must sign next" disabled={!iAlreadySigned}>
                          <Button color="teal" leftSection={<IconSignature />} disabled={busy}
                                  loading={approve.isPending} data-disabled={iAlreadySigned || undefined}
                                  onClick={(e) => (iAlreadySigned ? e.preventDefault() : setApproveOpen(true))}>
                            {nextMeaning === 'Reviewed' ? 'Review and sign' : 'Approve'}
                          </Button>
                        </Tooltip>
                      </>
                    )}
                    {canManage && v.status === 'EFFECTIVE' && (
                      <Button variant="subtle" color="gray" leftSection={<IconArchive />} disabled={busy}
                              onClick={() => setReasonAction('retire')}>
                        Retire
                      </Button>
                    )}
                  </Group>
                </Group>
                {v.status === 'EFFECTIVE' && canManage && !spec.openVersionId && (
                  <Text size="xs" c="dimmed" mt="xs">
                    An effective version never changes. Use “New version” to make a change; it is approved again.
                  </Text>
                )}
                {v.status === 'IN_REVIEW' && (
                  <Text size="xs" c="dimmed" mt="xs">
                    Waiting for approval. To change it, an approver rejects it back to draft.
                  </Text>
                )}
              </Card>

              <Card withBorder radius="md" className="arq-fade-in">
                <Group justify="space-between" mb="sm">
                  <Title order={5}>Tests and acceptance criteria</Title>
                  {v.previousVersionId && (
                    <Switch size="xs" label="Highlight changes from the previous version" checked={compare}
                            onChange={(e) => setCompare(e.currentTarget.checked)} />
                  )}
                </Group>
                <SpecTestEditor
                  rows={editable ? rows : v.tests}
                  editable={editable}
                  onChange={setRows}
                  errors={errors}
                  library={library.data ?? []}
                  changes={changes}
                />
                {editable ? (
                  <Textarea mt="md" label="What changed in this version" placeholder="e.g. Tightened impurity B to NMT 0.3 % per CC-2026-014"
                            autosize minRows={2} value={summary} error={errors.changeSummary}
                            onChange={(e) => setSummary(e.currentTarget.value)} />
                ) : (
                  v.changeSummary && (
                    <Text size="sm" mt="md">
                      <Text span c="dimmed">
                        Change summary:{' '}
                      </Text>
                      {v.changeSummary}
                    </Text>
                  )
                )}
              </Card>

              <Card withBorder radius="md" className="arq-fade-in">
                <Group gap="xs" mb="sm">
                  <ThemeIcon size={28} radius="md" variant="light" color="teal">
                    <IconSignature />
                  </ThemeIcon>
                  <Title order={5}>Electronic signatures</Title>
                </Group>
                {v.signatures.length === 0 ? (
                  <Text size="sm" c="dimmed">
                    Not signed yet.
                  </Text>
                ) : (
                  <Stack gap="xs">
                    {v.signatures.map((s) => (
                      <SignatureLine key={s.id} signature={s} timeZone={site?.timeZone} />
                    ))}
                  </Stack>
                )}
              </Card>
            </Stack>
          )}
        </Grid.Col>
      </Grid>

      <ReasonModal
        opened={reasonAction !== null}
        title={reasonAction === 'reject' ? `Reject v${v?.versionNo}` : reasonAction === 'retire' ? `Retire v${v?.versionNo}` : `Discard draft v${v?.versionNo}`}
        description={
          reasonAction === 'reject'
            ? 'The version goes back to draft so it can be corrected.'
            : reasonAction === 'retire'
              ? 'The specification can no longer be used for new studies. Its history and signatures remain.'
              : 'The draft is removed. It stays in the audit trail.'
        }
        confirmLabel={reasonAction === 'reject' ? 'Reject' : reasonAction === 'retire' ? 'Retire' : 'Discard'}
        busy={withReason.isPending}
        onCancel={() => setReasonAction(null)}
        onConfirm={(reason) => reasonAction && withReason.mutate({ action: reasonAction, reason })}
      />

      <Modal opened={approveOpen} onClose={() => setApproveOpen(false)} title={`Sign ${spec.specNumber} v${v?.versionNo}`} centered>
        <Stack>
          <Text size="sm">
            You are signing version {v?.versionNo} of this specification electronically with the meaning{' '}
            <b>{nextMeaning}</b>.{nextMeaning === 'Reviewed' && ' A second, different approver signs Approved afterwards.'}{' '}
            When approved it becomes effective today and replaces the current version. Your signature is the legally binding
            equivalent of a handwritten signature (21 CFR Part 11). To confirm it is you, you will be asked to sign in again.
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
    </Stack>
  );
}

/** Part 11 §11.50 manifestation: printed name, date/time and meaning. */
function SignatureLine({ signature: s, timeZone }: { signature: Signature; timeZone?: string }) {
  return (
    <Group justify="space-between" wrap="nowrap">
      <Text size="sm">
        {label(s.meaning)} by <b>{s.signerName}</b> on {formatInstant(s.signedAt, timeZone)}
        {s.comment ? ` — “${s.comment}”` : ''}
      </Text>
      <StatusBadge status={s.status} />
    </Group>
  );
}
