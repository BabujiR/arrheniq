import { Anchor, Stack, Text, Title } from '@mantine/core';

export function NotFoundPage() {
  return (
    <Stack p="xl">
      <Title order={3}>Page not found</Title>
      <Text>
        <Anchor href="/">Back to ArrheniQ</Anchor>
      </Text>
    </Stack>
  );
}
