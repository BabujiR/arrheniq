import { Stepper } from '@mantine/core';
import type { ProductStatus } from '../api/types';
import { IconApproved, IconDraft, IconReview } from './icons';

const ACTIVE: Record<Exclude<ProductStatus, 'RETIRED'>, number> = { DRAFT: 0, IN_REVIEW: 1, APPROVED: 3 };

/** Where the product is in its approval workflow (Draft → In review → Approved). */
export function WorkflowStepper({ status }: { status: ProductStatus }) {
  if (status === 'RETIRED') {
    return null;
  }
  return (
    <Stepper active={ACTIVE[status]} size="sm" color="teal" allowNextStepsSelect={false}>
      <Stepper.Step label="Draft" description="Being prepared" icon={<IconDraft />} />
      <Stepper.Step label="In review" description="Waiting for QA approval" icon={<IconReview />} />
      <Stepper.Step label="Approved" description="Signed electronically" icon={<IconApproved />} />
    </Stepper>
  );
}
