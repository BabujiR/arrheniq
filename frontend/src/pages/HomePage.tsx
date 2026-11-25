import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Button, Card, Group, SimpleGrid, Skeleton, Stack, Text, ThemeIcon, Title, UnstyledButton } from '@mantine/core';
import { keys, productsApi, testMethodsApi } from '../api/masterdata';
import type { Product } from '../api/types';
import { useSession } from '../auth/SessionProvider';
import { EmptyState } from '../components/EmptyState';
import { formatInstant } from '../components/format';
import { IconApproved, IconCheck, IconDraft, IconPlus, IconProduct, IconReview, IconTest } from '../components/icons';
import { StatusBadge } from '../components/StatusBadge';
import { useCountUp } from '../components/useCountUp';

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

  const all = products.data ?? [];
  const count = (status: Product['status']) => all.filter((p) => p.status === status).length;
  const waiting = all.filter((p) => p.status === 'IN_REVIEW');
  const drafts = all.filter((p) => p.status === 'DRAFT');
  const firstName = me.displayName.split(' ')[0] ?? me.displayName;
  const loading = products.isPending || tests.isPending;
  const openProduct = (p: Product) => void navigate({ to: '/products/$productId', params: { productId: p.id } });

  return (
    <Stack gap="lg">
      <div className="arq-fade-in">
        <Title order={2}>
          {greeting()}, {firstName}
        </Title>
        <Text c="dimmed">
          {new Intl.DateTimeFormat('en-US', { dateStyle: 'full' }).format(new Date())} · {me.tenant}
        </Text>
      </div>

      {canView && (
        <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }}>
          <StatCard label="Waiting for approval" value={count('IN_REVIEW')} icon={<IconReview size={22} />} color="yellow"
                    loading={loading} onClick={() => void navigate({ to: '/products' })} />
          <StatCard label="Drafts" value={count('DRAFT')} icon={<IconDraft size={22} />} color="gray" loading={loading}
                    onClick={() => void navigate({ to: '/products' })} />
          <StatCard label="Approved products" value={count('APPROVED')} icon={<IconApproved size={22} />} color="teal"
                    loading={loading} onClick={() => void navigate({ to: '/products' })} />
          <StatCard label="Active tests" value={tests.data?.length ?? 0} icon={<IconTest size={22} />} color="indigo"
                    loading={loading} onClick={() => void navigate({ to: '/test-methods' })} />
        </SimpleGrid>
      )}

      <SimpleGrid cols={{ base: 1, md: 2 }}>
        {can('product.approve') && (
          <Card withBorder radius="md" className="arq-fade-in">
            <CardTitle icon={<IconReview />} color="yellow" title="Waiting for your approval" />
            {loading ? (
              <Skeleton h={80} />
            ) : waiting.length === 0 ? (
              <EmptyState icon={<IconCheck />} title="All caught up" hint="No product is waiting for approval." />
            ) : (
              <Stack gap={4}>
                {waiting.slice(0, 6).map((p) => (
                  <ProductRow key={p.id} product={p} onOpen={openProduct} />
                ))}
              </Stack>
            )}
          </Card>
        )}

        {can('product.manage') && (
          <Card withBorder radius="md" className="arq-fade-in">
            <CardTitle icon={<IconDraft />} color="gray" title="Drafts to finish" />
            {loading ? (
              <Skeleton h={80} />
            ) : drafts.length === 0 ? (
              <EmptyState icon={<IconProduct />} title="No drafts" hint="Create a product, then submit it for approval." />
            ) : (
              <Stack gap={4}>
                {drafts.slice(0, 6).map((p) => (
                  <ProductRow key={p.id} product={p} onOpen={openProduct} />
                ))}
              </Stack>
            )}
          </Card>
        )}

        <Card withBorder radius="md" className="arq-fade-in">
          <CardTitle icon={<IconPlus />} color="indigo" title="Quick actions" />
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

function ProductRow({ product: p, onOpen }: { product: Product; onOpen: (p: Product) => void }) {
  return (
    <UnstyledButton onClick={() => onOpen(p)} className="arq-card-link" p={6} style={{ borderRadius: 8 }}>
      <Group justify="space-between" wrap="nowrap">
        <div>
          <Text size="sm" fw={500}>
            {p.code} · {p.name}
          </Text>
          <Text size="xs" c="dimmed">
            {p.updatedAt ? `Last changed ${formatInstant(p.updatedAt)}` : `Created ${formatInstant(p.createdAt)}`}
          </Text>
        </div>
        <StatusBadge status={p.status} />
      </Group>
    </UnstyledButton>
  );
}
