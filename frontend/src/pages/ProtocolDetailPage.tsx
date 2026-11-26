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
  Stack,
  Text,
  Textarea,
  ThemeIcon,
  Title,
  Tooltip,
  UnstyledButton,
} from '@mantine/core';
import { ApiError } from '../api/http';
import { dictionariesApi, keys, protocolsApi, testMethodsApi } from '../api/masterdata';
import type { ProtocolContent, ProtocolVersionDetails, Signature } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { goToReauth } from '../auth/navigation';
import { formatInstant, label } from '../components/format';
import { toFormErrors } from '../components/formErrors';
import { IconArchive, IconCalendar, IconEdit, IconPlus, IconReject, IconSend, IconSignature } from '../components/icons';
import { notifyError, notifySuccess } from '../components/notify';
import { ReasonModal } from '../components/ReasonModal';
import { StatusBadge } from '../components/StatusBadge';
import { RecordHistory } from '../audit/RecordHistory';
import { ProtocolEditor } from '../protocol/ProtocolEditor';
import { rememberPendingSignature, takePendingSignature } from '../signing/pendingSignature';

type ReasonAction = 'reject' | 'retire' | 'discard';

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'gray',
  IN_REVIEW: 'yellow',
  APPROVED: 'green',
  SUPERSEDED: 'orange',
  RETIRED: 'dark',
};

/** One protocol: version history, the protocol builder (draft) or plan view, workflow and signatures. */
export function ProtocolDetailPage() {
  const { protocolId = '' } = useParams({ strict: false }) as { protocolId?: string };
  const search = useSearch({ strict: false }) as { v?: string; signing?: string };
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { me, can } = useSession();

  const details = useQuery({ queryKey: keys.protocol(protocolId), queryFn: () => protocolsApi.get(protocolId) });
  const protocol = details.data?.protocol;
  const versions = details.data?.versions ?? [];
  const selectedId = search.v ?? protocol?.openVersionId ?? versions[0]?.id;
  const version = useQuery({
    queryKey: keys.protocolVersion(selectedId ?? ''),
    queryFn: () => protocolsApi.version(selectedId!),
    enabled: !!selectedId,
  });
  const v = version.data;
  const canManage = can('protocol.manage');
  const canApprove = can('protocol.approve');
  const editable = canManage && v?.status === 'DRAFT';
  const library = useQuery({ queryKey: keys.testMethods({}), queryFn: () => testMethodsApi.list({}), enabled: editable });
  const conditions = useQuery({
    queryKey: keys.dictionaryValues('STORAGE_CONDITION'),
    queryFn: () => dictionariesApi.values('STORAGE_CONDITION'),
    enabled: editable,
  });

  // ---------------------------------------------------------------- draft editing
  const [content, setContent] = useState<ProtocolContent | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (v) {
      setContent(v.content);
      setErrors({});
    }
  }, [v?.id, v?.version]);
  const dirty = !!v && !!content && JSON.stringify(content) !== JSON.stringify(v.content);

  const select = (versionId: string) =>
    void navigate({ to: '/protocols/$protocolId', params: { protocolId }, search: { v: versionId }, replace: true });
  const applied = (d: ProtocolVersionDetails, message: string) => {
    queryClient.setQueryData(keys.protocolVersion(d.id), d);
    void queryClient.invalidateQueries({ queryKey: keys.protocol(protocolId) });
    void queryClient.invalidateQueries({ queryKey: keys.protocolsAll });
    notifySuccess(message);
  };

  const save = useMutation({
    mutationFn: () => protocolsApi.save(v!.id, v!.version, content!),
    onSuccess: (d) => applied(d, `Draft v${d.versionNo} saved`),
    onError: (error) => {
      if (error instanceof ApiError && error.status === 400) {
        setErrors(toFormErrors(error.errors));
      }
      notifyError(error);
    },
  });
  const submit = useMutation({
    mutationFn: () => protocolsApi.submit(v!.id, v!.version),
    onSuccess: (d) =>
      applied(d, d.status === 'APPROVED' ? `v${d.versionNo} is approved (no approval configured)` : `v${d.versionNo} submitted for approval`),
    onError: notifyError,
  });
  const newVersion = useMutation({
    mutationFn: () => protocolsApi.newVersion(protocolId),
    onSuccess: (d) => {
      applied(d, `Draft v${d.versionNo} created from the current version`);
      select(d.id);
    },
    onError: notifyError,
  });
  const [reasonAction, setReasonAction] = useState<ReasonAction | null>(null);
  const withReason = useMutation({
    mutationFn: async ({ action, reason }: { action: ReasonAction; reason: string }): Promise<ProtocolVersionDetails | null> => {
      if (action === 'reject') return protocolsApi.reject(v!.id, v!.version, reason);
      if (action === 'retire') return protocolsApi.retire(v!.id, v!.version, reason);
      await protocolsApi.discard(v!.id, v!.version, reason);
      return null;
    },
    onSuccess: (d, { action }) => {
      setReasonAction(null);
      if (d) {
        applied(d, `v${d.versionNo} ${action === 'reject' ? 'rejected' : 'retired'}`);
        return;
      }
      notifySuccess('Draft discarded');
      void queryClient.invalidateQueries({ queryKey: keys.protocolsAll });
      if (versions.length <= 1) {
        void navigate({ to: '/protocols' });
      } else {
        void queryClient.invalidateQueries({ queryKey: keys.protocol(protocolId) });
        void navigate({ to: '/protocols/$protocolId', params: { protocolId }, search: {}, replace: true });
      }
    },
    onError: notifyError,
  });

  // ---------------------------------------------------------------- approval (e-signature)
  const [approveOpen, setApproveOpen] = useState(false);
  const [comment, setComment] = useState('');
  const approve = useMutation({
    mutationFn: ({ id, expectedVersion, note }: { id: string; expectedVersion: number; note: string }) =>
      protocolsApi.approve(id, expectedVersion, note || undefined),
    onSuccess: (d) =>
      applied(
        d,
        d.status === 'APPROVED'
          ? `v${d.versionNo} approved and signed: it can now be used for studies`
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
    void navigate({ to: '/protocols/$protocolId', params: { protocolId }, search: { v: selectedId }, replace: true });
    const pending = takePendingSignature('PROTOCOL_VERSION', selectedId);
    if (pending) {
      approve.mutate({ id: pending.recordId, expectedVersion: pending.expectedVersion, note: pending.comment });
    } else {
      notifyError(new ApiError(401, 'ARQ-AUTH-REAUTH', 'The signing request expired; approve again'));
    }
  }, [search.signing, selectedId]);

  if (details.isPending) {
    return (
      <Center h={300}>
        <Loader />
      </Center>
    );
  }
  if (details.isError || !protocol) {
    return (
      <Alert color="red" title="Protocol not available">
        {details.error instanceof ApiError && details.error.status === 404
          ? 'This protocol does not exist or belongs to a site you cannot access.'
          : details.error?.message}
      </Alert>
    );
  }

  const site = me.sites.find((s) => s.id === protocol.siteId);
  const busy = save.isPending || submit.isPending || withReason.isPending || approve.isPending || newVersion.isPending;
  const iAlreadySigned = !!v?.signatures.some(
    (s) => s.status === 'VALID' && s.signerUserId === me.userId && (s.meaning === 'REVIEWED' || s.meaning === 'APPROVED'),
  );
  const nextMeaning = v && v.approval.required > 1 && v.approval.collected === 0 ? 'Reviewed' : 'Approved';
  const startApproval = () => {
    if (!v) return;
    rememberPendingSignature({
      recordType: 'PROTOCOL_VERSION',
      recordId: v.id,
      expectedVersion: v.version,
      comment: comment.trim(),
      createdAt: Date.now(),
    });
    goToReauth('SIGN', `/protocols/${protocolId}?v=${v.id}&signing=1`, {
      recordType: 'PROTOCOL_VERSION',
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
            <IconCalendar size={22} />
          </ThemeIcon>
          <Stack gap={2}>
            <Anchor size="xs" onClick={() => void navigate({ to: '/protocols' })}>
              ← All protocols
            </Anchor>
            <Title order={2} fz={20} fw={650}>
              {protocol.protocolNumber} · {protocol.name}
            </Title>
            <Text size="xs" c="dimmed">
              Product{' '}
              <Anchor size="xs" onClick={() => void navigate({ to: '/products/$productId', params: { productId: protocol.productId } })}>
                {protocol.productCode} · {protocol.productName}
              </Anchor>
              {site && ` · ${site.code}`}
              {protocol.approvedVersionNo
                ? ` · approved: v${protocol.approvedVersionNo} since ${protocol.approvedFrom}`
                : ' · not approved yet'}
            </Text>
          </Stack>
        </Group>
        {canManage && !protocol.openVersionId && versions.length > 0 && (
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
              {versions.map((pv) => (
                <UnstyledButton key={pv.id} onClick={() => select(pv.id)} p="xs"
                                style={{ borderRadius: 8, background: pv.id === selectedId ? 'var(--mantine-color-arqIndigo-light)' : undefined }}>
                  <Group justify="space-between" wrap="nowrap">
                    <Text size="sm" fw={600}>
                      v{pv.versionNo}
                    </Text>
                    <Badge size="xs" variant="light" color={STATUS_COLOR[pv.status] ?? 'gray'}>
                      {label(pv.status)}
                    </Badge>
                  </Group>
                  <Text size="xs" c="dimmed" lineClamp={2}>
                    {label(pv.studyType)} · {pv.timepointCount} time points · {pv.testCount} tests
                    {pv.changeSummary ? ` · ${pv.changeSummary}` : ''}
                  </Text>
                </UnstyledButton>
              ))}
            </Stack>
          </Card>
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 9 }}>
          {!v || !content ? (
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
                        approved {v.effectiveFrom}
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
                          <Button leftSection={<IconSend />} loading={submit.isPending} disabled={busy}
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
                          <Button color="teal" leftSection={<IconSignature />} disabled={busy} loading={approve.isPending}
                                  data-disabled={iAlreadySigned || undefined}
                                  onClick={(e) => (iAlreadySigned ? e.preventDefault() : setApproveOpen(true))}>
                            {nextMeaning === 'Reviewed' ? 'Review and sign' : 'Approve'}
                          </Button>
                        </Tooltip>
                      </>
                    )}
                    {canManage && v.status === 'APPROVED' && (
                      <Button variant="subtle" color="gray" leftSection={<IconArchive />} disabled={busy}
                              onClick={() => setReasonAction('retire')}>
                        Retire
                      </Button>
                    )}
                  </Group>
                </Group>
                {v.status === 'APPROVED' && canManage && !protocol.openVersionId && (
                  <Text size="xs" c="dimmed" mt="xs">
                    An approved version never changes. Use “New version” to make a change; it is approved again.
                  </Text>
                )}
              </Card>

              <Card withBorder radius="md" className="arq-fade-in">
                <ProtocolEditor
                  content={editable ? content : v.content}
                  editable={editable}
                  onChange={setContent}
                  library={library.data ?? []}
                  conditions={(conditions.data ?? []).filter((d) => d.status === 'ACTIVE')}
                  conditionLabel={v.conditionLabel}
                  errors={errors}
                />
                {editable ? (
                  <Textarea mt="md" label="What changed in this version" autosize minRows={2}
                            value={content.changeSummary ?? ''} error={errors.changeSummary}
                            onChange={(e) => setContent({ ...content, changeSummary: e.currentTarget.value || null })} />
                ) : (
                  v.content.changeSummary && (
                    <Text size="sm" mt="md">
                      <Text span c="dimmed">
                        Change summary:{' '}
                      </Text>
                      {v.content.changeSummary}
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
              {can('audit.view') && (
                <RecordHistory recordIds={[protocol.id, v.id]} revision={v.version} timeZone={site?.timeZone} />
              )}
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
              ? 'The protocol can no longer be used for new studies. Its history and signatures remain.'
              : 'The draft is removed. It stays in the audit trail.'
        }
        confirmLabel={reasonAction === 'reject' ? 'Reject' : reasonAction === 'retire' ? 'Retire' : 'Discard'}
        busy={withReason.isPending}
        onCancel={() => setReasonAction(null)}
        onConfirm={(reason) => reasonAction && withReason.mutate({ action: reasonAction, reason })}
      />

      <Modal opened={approveOpen} onClose={() => setApproveOpen(false)} title={`Sign ${protocol.protocolNumber} v${v?.versionNo}`} centered>
        <Stack>
          <Text size="sm">
            You are signing version {v?.versionNo} of this protocol electronically with the meaning <b>{nextMeaning}</b>.
            {nextMeaning === 'Reviewed' && ' A second, different approver signs Approved afterwards.'} When approved it
            replaces the current version for new studies. Your signature is the legally binding equivalent of a handwritten
            signature (21 CFR Part 11). To confirm it is you, you will be asked to sign in again.
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
