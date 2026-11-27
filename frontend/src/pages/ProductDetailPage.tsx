import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from '@tanstack/react-router';
import { Alert, Anchor, Badge, Button, Card, Center, Group, Loader, Modal, SimpleGrid, Stack, Text, Textarea, ThemeIcon, Title, Tooltip } from '@mantine/core';
import { ApiError } from '../api/http';
import { keys, productsApi, protocolsApi, specificationsApi } from '../api/masterdata';
import { studiesApi, studyKeys } from '../api/studies';
import type { Product, ProductDetails, Signature } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { goToReauth } from '../auth/navigation';
import { formatInstant, label } from '../components/format';
import { notifyError, notifySuccess } from '../components/notify';
import { ReasonModal } from '../components/ReasonModal';
import { IconArchive, IconCalendar, IconEdit, IconPlus, IconProduct, IconReject, IconSend, IconSignature, IconSpec, IconStudy } from '../components/icons';
import { StatusBadge } from '../components/StatusBadge';
import { RecordHistory } from '../audit/RecordHistory';
import { WorkflowStepper } from '../components/WorkflowStepper';
import { rememberPendingSignature, takePendingSignature } from '../signing/pendingSignature';
import { ProductForm } from './ProductForm';

type ReasonAction = 'reject' | 'retire';

/** One product: details, workflow actions by status and permission, and its signature manifestation. */
export function ProductDetailPage() {
  const { productId = '' } = useParams({ strict: false }) as { productId?: string };
  const { me, can } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const details = useQuery({ queryKey: keys.product(productId), queryFn: () => productsApi.get(productId) });
  const [editing, setEditing] = useState(false);
  const [reasonAction, setReasonAction] = useState<ReasonAction | null>(null);
  const [approveOpen, setApproveOpen] = useState(false);
  const [comment, setComment] = useState('');

  const applied = (d: ProductDetails, message: string) => {
    queryClient.setQueryData(keys.product(productId), d);
    void queryClient.invalidateQueries({ queryKey: keys.productsAll });
    notifySuccess(message);
  };

  const submit = useMutation({
    mutationFn: (p: Product) => productsApi.submit(p.id, p.version),
    onSuccess: (d) => applied(d, `${d.product.code} submitted for approval`),
    onError: notifyError,
  });
  const withReason = useMutation({
    mutationFn: ({ p, action, reason }: { p: Product; action: ReasonAction; reason: string }) =>
      action === 'reject' ? productsApi.reject(p.id, p.version, reason) : productsApi.retire(p.id, p.version, reason),
    onSuccess: (d, v) => {
      setReasonAction(null);
      applied(d, `${d.product.code} ${v.action === 'reject' ? 'rejected' : 'retired'}`);
    },
    onError: notifyError,
  });
  const approve = useMutation({
    mutationFn: ({ id, version, note }: { id: string; version: number; note: string }) =>
      productsApi.approve(id, version, note || undefined),
    onSuccess: (d) =>
      applied(
        d,
        d.product.status === 'APPROVED'
          ? `${d.product.code} approved and signed`
          : `${d.product.code} reviewed and signed; a second approver must now approve it`,
      ),
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'ARQ-AUTH-REAUTH') {
        notifyError(new ApiError(401, error.code, 'Re-authentication expired; approve again'));
      } else if (error instanceof ApiError && error.code === 'ARQ-ESG-004') {
        notifyError(new ApiError(409, error.code, 'The product changed since you started signing; review it and approve again'));
      } else {
        notifyError(error);
      }
    },
  });

  // Back from the IdP after "Approve": apply the remembered approval exactly once (ADR 0019).
  const signingHandled = useRef(false);
  useEffect(() => {
    if (signingHandled.current || !productId) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    if (params.get('signing') !== '1') {
      return;
    }
    signingHandled.current = true;
    void navigate({ to: '/products/$productId', params: { productId }, replace: true });
    const pending = takePendingSignature('PRODUCT', productId);
    if (pending) {
      approve.mutate({ id: pending.recordId, version: pending.expectedVersion, note: pending.comment });
    } else {
      notifyError(new ApiError(401, 'ARQ-AUTH-REAUTH', 'The signing request expired; approve again'));
    }
  }, [productId]);

  if (details.isPending) {
    return (
      <Center h={300}>
        <Loader />
      </Center>
    );
  }
  if (details.isError) {
    return (
      <Alert color="red" title="Product not available">
        {details.error instanceof ApiError && details.error.status === 404
          ? 'This product does not exist or belongs to a site you cannot access.'
          : details.error.message}
      </Alert>
    );
  }

  const p = details.data.product;
  const approval = details.data.approval;
  const site = me.sites.find((s) => s.id === p.siteId);
  const validApprovals = details.data.signatures.filter(
    (s) => s.status === 'VALID' && (s.meaning === 'REVIEWED' || s.meaning === 'APPROVED'),
  );
  const iAlreadySigned = validApprovals.some((s) => s.signerUserId === me.userId);
  // with a double approval, the first signature means Reviewed and the second Approved
  const nextMeaning = approval.required > 1 && approval.collected === 0 ? 'Reviewed' : 'Approved';
  const busy = submit.isPending || withReason.isPending || approve.isPending;

  const startApproval = () => {
    rememberPendingSignature({
      recordType: 'PRODUCT',
      recordId: p.id,
      expectedVersion: p.version,
      comment: comment.trim(),
      createdAt: Date.now(),
    });
    goToReauth('SIGN', `/products/${p.id}?signing=1`, { recordType: 'PRODUCT', recordId: p.id, recordVersion: p.version });
  };

  return (
    <Stack>
      <Group justify="space-between">
        <Group wrap="nowrap" align="flex-start">
          <ThemeIcon size={42} radius="md" variant="light" color="arqIndigo" className="arq-pop">
            <IconProduct size={24} />
          </ThemeIcon>
          <Stack gap={2}>
            <Anchor size="xs" onClick={() => void navigate({ to: '/products' })}>
              ← All products
            </Anchor>
            <Group gap="sm">
              <Title order={3}>
                {p.code} · {p.name}
              </Title>
              <StatusBadge status={p.status} />
            </Group>
          </Stack>
        </Group>
        <Group>
          {can('product.manage') && (p.status === 'DRAFT' || p.status === 'APPROVED') && (
            <Button variant="default" leftSection={<IconEdit />} disabled={busy} onClick={() => setEditing(true)}>
              Edit
            </Button>
          )}
          {can('product.manage') && p.status === 'DRAFT' && (
            <Button leftSection={<IconSend />} disabled={busy} loading={submit.isPending} onClick={() => submit.mutate(p)}>
              Submit for approval
            </Button>
          )}
          {can('product.approve') && p.status === 'IN_REVIEW' && (
            <>
              <Button color="red" variant="light" leftSection={<IconReject />} disabled={busy}
                      onClick={() => setReasonAction('reject')}>
                Reject
              </Button>
              <Tooltip label="You have already signed; a different approver must sign next" disabled={!iAlreadySigned}>
                {/* data-disabled (not disabled) so the tooltip still gets hover events */}
                <Button color="teal" leftSection={<IconSignature />} disabled={busy} loading={approve.isPending}
                        data-disabled={iAlreadySigned || undefined}
                        onClick={(e) => (iAlreadySigned ? e.preventDefault() : setApproveOpen(true))}>
                  {nextMeaning === 'Reviewed' ? 'Review and sign' : 'Approve'}
                </Button>
              </Tooltip>
            </>
          )}
          {can('product.manage') && p.status !== 'RETIRED' && (
            <Button color="gray" variant="subtle" leftSection={<IconArchive />} disabled={busy}
                    onClick={() => setReasonAction('retire')}>
              Retire
            </Button>
          )}
        </Group>
      </Group>

      <Card withBorder radius="md" className="arq-fade-in">
        {p.status === 'RETIRED' ? (
          <Alert color="gray" title="Retired" icon={<IconArchive />}>
            This product can no longer be used for new studies. Its history and signatures remain available.
          </Alert>
        ) : (
          <WorkflowStepper status={p.status} />
        )}
      </Card>

      <Card withBorder radius="md" className="arq-fade-in">
        <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
          <Field name="Site" value={site ? `${site.code} — ${site.name}` : p.siteId} />
          <Field name="Product type" value={label(p.productType)} />
          <Field name="Dosage form" value={p.dosageForm} />
          <Field name="Strength" value={p.strength} />
          <Field
            name="Expiration"
            value={
              p.expirationStatus === 'ESTABLISHED'
                ? `${p.expirationValue} ${label(p.expirationUnit ?? '')}`
                : label(p.expirationStatus)
            }
          />
          <Field name="Controlled substance" value={p.controlledSubstance ? 'Yes' : 'No'} />
          <Field name="Version" value={String(p.version)} />
          <Field name="Created" value={formatInstant(p.createdAt, site?.timeZone)} />
          <Field name="Last changed" value={p.updatedAt ? formatInstant(p.updatedAt, site?.timeZone) : '—'} />
        </SimpleGrid>
        {p.description && (
          <Text size="sm" mt="md">
            {p.description}
          </Text>
        )}
      </Card>

      <ProductSpecifications productId={p.id} canAdd={can('spec.manage') && p.status !== 'RETIRED'} />
      <ProductProtocols productId={p.id} canAdd={can('protocol.manage') && p.status !== 'RETIRED'} />
      {can('study.view') && <ProductStudies productId={p.id} canAdd={can('study.create') && p.status === 'APPROVED'} />}

      <Card withBorder radius="md" className="arq-fade-in">
        <Group gap="xs" mb="sm">
          <ThemeIcon size={28} radius="md" variant="light" color="teal">
            <IconSignature />
          </ThemeIcon>
          <Title order={5}>Electronic signatures</Title>
          {p.status === 'IN_REVIEW' && approval.required > 1 && (
            <Badge variant="light" color="teal" className="arq-pop">
              {approval.collected} of {approval.required} approvals
            </Badge>
          )}
        </Group>
        {details.data.signatures.length === 0 ? (
          <Text size="sm" c="dimmed">
            Not signed yet.
          </Text>
        ) : (
          <Stack gap="xs">
            {details.data.signatures.map((s) => (
              <SignatureLine key={s.id} signature={s} timeZone={site?.timeZone} />
            ))}
          </Stack>
        )}
      </Card>

      {can('audit.view') && <RecordHistory recordIds={[p.id]} revision={p.version} timeZone={site?.timeZone} />}

      <ProductForm opened={editing} existing={p} onClose={() => setEditing(false)} />

      <ReasonModal
        opened={reasonAction !== null}
        title={reasonAction === 'reject' ? `Reject ${p.code}` : `Retire ${p.code}`}
        description={
          reasonAction === 'reject'
            ? 'The product goes back to Draft so it can be corrected.'
            : 'Retired products can no longer be used for new studies.'
        }
        confirmLabel={reasonAction === 'reject' ? 'Reject' : 'Retire'}
        busy={withReason.isPending}
        onCancel={() => setReasonAction(null)}
        onConfirm={(reason) => reasonAction && withReason.mutate({ p, action: reasonAction, reason })}
      />

      <Modal opened={approveOpen} onClose={() => setApproveOpen(false)} title={`Approve ${p.code}`} centered>
        <Stack>
          <Text size="sm">
            You are signing version {p.version} of this product electronically with the meaning <b>{nextMeaning}</b>.
            {nextMeaning === 'Reviewed' && ' A second, different approver signs Approved afterwards.'} Your signature is the
            legally binding equivalent of a handwritten signature (21 CFR Part 11). To confirm it is you, you will be
            asked to sign in again.
          </Text>
          <Textarea
            label="Comment (optional)"
            autosize
            minRows={2}
            value={comment}
            onChange={(e) => setComment(e.currentTarget.value)}
          />
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

function Field({ name, value }: { name: string; value: string | null | undefined }) {
  return (
    <div>
      <Text size="xs" c="dimmed">
        {name}
      </Text>
      <Text size="sm">{value || '—'}</Text>
    </div>
  );
}

/** Part 11 §11.50 manifestation: printed name, date/time and meaning. */
function SignatureLine({ signature: s, timeZone }: { signature: Signature; timeZone?: string }) {
  return (
    <Group justify="space-between" wrap="nowrap">
      <Text size="sm">
        {label(s.meaning)} by <b>{s.signerName}</b> on {formatInstant(s.signedAt, timeZone)} — Meaning:{' '}
        {label(s.meaning)}
        {s.comment ? ` — “${s.comment}”` : ''}
      </Text>
      <StatusBadge status={s.status} />
    </Group>
  );
}

/** The product's specifications, with a shortcut to create one (URS-MD-003). */
function ProductSpecifications({ productId, canAdd }: { productId: string; canAdd: boolean }) {
  const navigate = useNavigate();
  const specs = useQuery({
    queryKey: keys.specifications({ productId }),
    queryFn: () => specificationsApi.list({ productId }),
  });
  return (
    <Card withBorder radius="md" className="arq-fade-in">
      <Group justify="space-between" mb="sm">
        <Group gap="xs">
          <ThemeIcon size={28} radius="md" variant="light" color="arqIndigo">
            <IconSpec />
          </ThemeIcon>
          <Title order={5}>Specifications</Title>
        </Group>
        {canAdd && (
          <Button size="xs" variant="light" leftSection={<IconPlus size={14} />}
                  onClick={() => void navigate({ to: '/specifications', search: { new: '1', productId } })}>
            New specification
          </Button>
        )}
      </Group>
      {specs.data && specs.data.length === 0 ? (
        <Text size="sm" c="dimmed">
          No specification yet.
        </Text>
      ) : (
        <Stack gap={6}>
          {specs.data?.map((sp) => (
            <Group key={sp.id} justify="space-between" wrap="nowrap">
              <Anchor size="sm" onClick={() => void navigate({ to: '/specifications/$specId', params: { specId: sp.id } })}>
                {sp.specNumber} · {sp.title}
              </Anchor>
              <Group gap={6} wrap="nowrap">
                {sp.effectiveVersionNo ? (
                  <Badge size="sm" variant="light" color="green">
                    v{sp.effectiveVersionNo} effective
                  </Badge>
                ) : null}
                {sp.openStatus && <StatusBadge status={sp.openStatus} />}
              </Group>
            </Group>
          ))}
        </Stack>
      )}
    </Card>
  );
}

/** The product's protocols, with a shortcut to create one (URS-PRO-001). */
function ProductProtocols({ productId, canAdd }: { productId: string; canAdd: boolean }) {
  const navigate = useNavigate();
  const protocols = useQuery({
    queryKey: keys.protocols({ productId }),
    queryFn: () => protocolsApi.list({ productId }),
  });
  return (
    <Card withBorder radius="md" className="arq-fade-in">
      <Group justify="space-between" mb="sm">
        <Group gap="xs">
          <ThemeIcon size={28} radius="md" variant="light" color="arqIndigo">
            <IconCalendar />
          </ThemeIcon>
          <Title order={5}>Protocols</Title>
        </Group>
        {canAdd && (
          <Button size="xs" variant="light" leftSection={<IconPlus size={14} />}
                  onClick={() => void navigate({ to: '/protocols', search: { new: '1', productId } })}>
            New protocol
          </Button>
        )}
      </Group>
      {protocols.data && protocols.data.length === 0 ? (
        <Text size="sm" c="dimmed">
          No protocol yet.
        </Text>
      ) : (
        <Stack gap={6}>
          {protocols.data?.map((pr) => (
            <Group key={pr.id} justify="space-between" wrap="nowrap">
              <Anchor size="sm" onClick={() => void navigate({ to: '/protocols/$protocolId', params: { protocolId: pr.id } })}>
                {pr.protocolNumber} · {pr.name}
              </Anchor>
              <Group gap={6} wrap="nowrap">
                {pr.approvedVersionNo ? (
                  <Badge size="sm" variant="light" color="green">
                    v{pr.approvedVersionNo} approved
                  </Badge>
                ) : null}
                {pr.openStatus && <StatusBadge status={pr.openStatus} />}
              </Group>
            </Group>
          ))}
        </Stack>
      )}
    </Card>
  );
}

/** The product's studies, newest first, with a shortcut to log in a lot (URS-STU-001). */
function ProductStudies({ productId, canAdd }: { productId: string; canAdd: boolean }) {
  const navigate = useNavigate();
  const studies = useQuery({ queryKey: studyKeys.list({ productId }), queryFn: () => studiesApi.list({ productId }) });
  return (
    <Card withBorder radius="md" className="arq-fade-in">
      <Group justify="space-between" mb="sm">
        <Group gap="xs">
          <ThemeIcon size={28} radius="md" variant="light" color="arqIndigo">
            <IconStudy />
          </ThemeIcon>
          <Title order={5}>Studies</Title>
        </Group>
        {canAdd && (
          <Button size="xs" variant="light" leftSection={<IconPlus size={14} />}
                  onClick={() => void navigate({ to: '/studies/new', search: { productId } })}>
            Log in study
          </Button>
        )}
      </Group>
      {studies.data && studies.data.length === 0 ? (
        <Text size="sm" c="dimmed">
          No study yet.
        </Text>
      ) : (
        <Stack gap={6}>
          {studies.data?.slice(0, 10).map((st) => (
            <Group key={st.id} justify="space-between" wrap="nowrap">
              <Anchor size="sm" onClick={() => void navigate({ to: '/studies/$studyId', params: { studyId: st.id } })}>
                {st.studyCode} · lot {st.lotNumber} · {st.conditionLabel}
              </Anchor>
              <StatusBadge status={st.status} />
            </Group>
          ))}
        </Stack>
      )}
    </Card>
  );
}

