import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Button, Card, Group, SimpleGrid, Skeleton, Stack, Text, ThemeIcon, UnstyledButton } from '@mantine/core';
import { keys, productsApi, protocolsApi, specificationsApi, testMethodsApi } from '../api/masterdata';
import type { Product } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { EmptyState } from '../components/EmptyState';
import { formatInstant } from '../components/format';
import { IconApproved, IconCalendar, IconCheck, IconDraft, IconHome, IconPlus, IconProduct, IconReview, IconSpec, IconTest } from '../components/icons';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { useCountUp } from '../components/useCountUp';

/** One thing on a work list: a product, a specification version or a protocol version. */
interface WorkItem {
  key: string;
  icon: ReactNode;
  title: string;
  detail: string;
  status: string;
  open: () => void;
}

function greeting(date = new Date()): string {
  const h = date.getHours();
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
}

/** Start page: what's waiting for me, where things stand, and the most common next steps. */
export function HomePage() {
  const { me, can } = useSession();
  const navigate = useNavigate();
  const canView = can('masterdata.view');
  const products = useQuery({
    queryKey: keys.products({}),
    queryFn: () => productsApi.list({}),
    enabled: canView,
  });
  const tests = useQuery({
    queryKey: keys.testMethods({ status: 'ACTIVE' }),
    queryFn: () => testMethodsApi.list({ status: 'ACTIVE' }),
    enabled: canView,
  });

  const specs = useQuery({
    queryKey: keys.specifications({}),
    queryFn: () => specificationsApi.list({}),
    enabled: canView,
  });
  const protocols = useQuery({
    queryKey: keys.protocols({}),
    queryFn: () => protocolsApi.list({}),
    enabled: canView,
  });

  const all = products.data ?? [];
  const count = (status: Product['status']) => all.filter((p) => p.status === status).length;
  const firstName = me.displayName.split(' ')[0] ?? me.displayName;
  const loading = products.isPending || tests.isPending || specs.isPending || protocols.isPending;

  // work lists across products, specifications and protocols, limited to what this user may act on
  const items = (status: 'DRAFT' | 'IN_REVIEW', kind: 'manage' | 'approve'): WorkItem[] => [
    ...(can(`product.${kind}`) ? all : [])
      .filter((p) => p.status === status)
      .map((p) => ({
        key: `p-${p.id}`,
        icon: <IconProduct size={16} />,
        title: `${p.code} · ${p.name}`,
        detail: `Product · ${p.updatedAt ? `last changed ${formatInstant(p.updatedAt)}` : `created ${formatInstant(p.createdAt)}`}`,
        status: p.status,
        open: () => void navigate({ to: '/products/$productId', params: { productId: p.id } }),
      })),
    ...(can(`spec.${kind}`) ? (specs.data ?? []) : [])
      .filter((sp) => sp.openStatus === status && sp.openVersionId)
      .map((sp) => ({
        key: `s-${sp.id}`,
        icon: <IconSpec size={16} />,
        title: `${sp.specNumber} v${sp.openVersionNo} · ${sp.title}`,
        detail: `Specification · ${sp.productCode}`,
        status,
        open: () =>
          void navigate({ to: '/specifications/$specId', params: { specId: sp.id }, search: { v: sp.openVersionId! } }),
      })),
    ...(can(`protocol.${kind}`) ? (protocols.data ?? []) : [])
      .filter((pr) => pr.openStatus === status && pr.openVersionId)
      .map((pr) => ({
        key: `r-${pr.id}`,
        icon: <IconCalendar size={16} />,
        title: `${pr.protocolNumber} v${pr.openVersionNo} · ${pr.name}`,
        detail: `Protocol · ${pr.productCode}`,
        status,
        open: () => void navigate({ to: '/protocols/$protocolId', params: { protocolId: pr.id }, search: { v: pr.openVersionId! } }),
      })),
  ];
  const waiting = items('IN_REVIEW', 'approve');
  const drafts = items('DRAFT', 'manage');
  const canApproveAny = can('product.approve') || can('spec.approve') || can('protocol.approve');
  const canManageAny = can('product.manage') || can('spec.manage') || can('protocol.manage');
  const inReviewTotal =
    count('IN_REVIEW') +
    (specs.data ?? []).filter((x) => x.openStatus === 'IN_REVIEW').length +
    (protocols.data ?? []).filter((x) => x.openStatus === 'IN_REVIEW').length;
  const draftTotal =
    count('DRAFT') +
    (specs.data ?? []).filter((x) => x.openStatus === 'DRAFT').length +
    (protocols.data ?? []).filter((x) => x.openStatus === 'DRAFT').length;

  return (
    <Stack gap="lg">
      <PageHeader
        icon={<IconHome size={24} />}
        title={`${greeting()}, ${firstName}`}
        description={`${new Intl.DateTimeFormat('en-US', { dateStyle: 'full' }).format(new Date())} · ${me.tenant}`}
      />

      {canView && (
        <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }}>
          <StatCard label="Waiting for approval" value={inReviewTotal} icon={<IconReview size={22} />} color="yellow"
                    loading={loading} onClick={() => void navigate({ to: '/products' })} />
          <StatCard label="Drafts" value={draftTotal} icon={<IconDraft size={22} />} color="gray" loading={loading}
                    onClick={() => void navigate({ to: '/products' })} />
          <StatCard label="Approved products" value={count('APPROVED')} icon={<IconApproved size={22} />} color="teal"
                    loading={loading} onClick={() => void navigate({ to: '/products' })} />
          <StatCard label="Active tests" value={tests.data?.length ?? 0} icon={<IconTest size={22} />} color="arqIndigo"
                    loading={loading} onClick={() => void navigate({ to: '/test-methods' })} />
        </SimpleGrid>
      )}

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        {canApproveAny && (
          <Card withBorder radius="md" className="arq-fade-in">
            <CardTitle icon={<IconReview />} color="yellow" title="Waiting for your approval" />
            <WorkList loading={loading} items={waiting} empty={
              <EmptyState icon={<IconCheck />} title="All caught up" hint="Nothing is waiting for your approval." />
            } />
          </Card>
        )}

        {canManageAny && (
          <Card withBorder radius="md" className="arq-fade-in">
            <CardTitle icon={<IconDraft />} color="gray" title="Drafts to finish" />
            <WorkList loading={loading} items={drafts} empty={
              <EmptyState icon={<IconProduct />} title="No drafts" hint="Create a product, specification or protocol, then submit it for approval." />
            } />
          </Card>
        )}

        <Card withBorder radius="md" className="arq-fade-in">
          <CardTitle icon={<IconPlus />} color="arqIndigo" title="Quick actions" />
          <Group>
            {can('product.manage') && (
              <Button leftSection={<IconProduct />} variant="light"
                      onClick={() => void navigate({ to: '/products', search: { new: '1' } })}>
                New product
              </Button>
            )}
            {can('test.manage') && (
              <Button leftSection={<IconTest />} variant="light"
                      onClick={() => void navigate({ to: '/test-methods', search: { new: '1' } })}>
                New test
              </Button>
            )}
            {canView && (
              <Button variant="subtle" onClick={() => void navigate({ to: '/products' })}>
                Browse products
              </Button>
            )}
          </Group>
          {!canView && (
            <Text size="sm" c="dimmed" mt="sm">
              Your account has no master-data access yet. Ask your administrator for a role.
            </Text>
          )}
        </Card>
      </SimpleGrid>
    </Stack>
  );
}

function StatCard(props: { label: string; value: number; icon: ReactNode; color: string; loading: boolean; onClick: () => void }) {
  const shown = useCountUp(props.loading ? 0 : props.value);
  return (
    <Card withBorder radius="md" className="arq-card-link arq-hover-icon arq-fade-in" onClick={props.onClick}>
      <Group justify="space-between" wrap="nowrap">
        <div>
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
            {props.label}
          </Text>
          {props.loading ? <Skeleton h={32} w={48} mt={4} /> : <Text fz={30} fw={700}>{shown}</Text>}
        </div>
        <ThemeIcon size={46} radius="md" variant="light" color={props.color} className="arq-pop">
          {props.icon}
        </ThemeIcon>
      </Group>
    </Card>
  );
}

function CardTitle({ icon, color, title }: { icon: ReactNode; color: string; title: string }) {
  return (
    <Group gap="xs" mb="sm">
      <ThemeIcon size={28} radius="md" variant="light" color={color}>
        {icon}
      </ThemeIcon>
      <Text fw={600}>{title}</Text>
    </Group>
  );
}

function WorkList({ loading, items, empty }: { loading: boolean; items: WorkItem[]; empty: ReactNode }) {
  if (loading) {
    return <Skeleton h={80} />;
  }
  if (items.length === 0) {
    return <>{empty}</>;
  }
  return (
    <Stack gap={4}>
      {items.slice(0, 8).map((item) => (
        <UnstyledButton key={item.key} onClick={item.open} className="arq-card-link" p={6} style={{ borderRadius: 8 }}>
          <Group justify="space-between" wrap="nowrap">
            <Group gap="xs" wrap="nowrap">
              <ThemeIcon size={26} radius="md" variant="light" color="gray">
                {item.icon}
              </ThemeIcon>
              <div>
                <Text size="sm" fw={500} lineClamp={1}>
                  {item.title}
                </Text>
                <Text size="xs" c="dimmed">
                  {item.detail}
                </Text>
              </div>
            </Group>
            <StatusBadge status={item.status} />
          </Group>
        </UnstyledButton>
      ))}
      {items.length > 8 && (
        <Text size="xs" c="dimmed" ta="center">
          and {items.length - 8} more
        </Text>
      )}
    </Stack>
  );
}
