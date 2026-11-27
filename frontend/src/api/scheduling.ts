import { api } from './http';

export type DateRule = 'LEAVE' | 'MOVE_BEFORE' | 'MOVE_AFTER';

/** GET /api/v1/protocol-versions/{id}/pull-dates (URS-SCH-001/002). */
export interface PullDatePreview {
  algorithm: string;
  protocolVersionId: string;
  siteId: string;
  onStabilityDate: string;
  rules: { saturday: DateRule; sunday: DateRule; holiday: DateRule; holidays: string[] };
  pullDates: {
    label: string;
    value: number;
    unit: 'DAY' | 'WEEK' | 'MONTH';
    calculatedDate: string;
    pullDate: string;
    ruleApplied: string | null;
  }[];
}

export const schedulingApi = {
  pullDates: (protocolVersionId: string, onStabilityDate: string) =>
    api<PullDatePreview>(
      'GET',
      `/api/v1/protocol-versions/${protocolVersionId}/pull-dates?onStabilityDate=${encodeURIComponent(onStabilityDate)}`,
    ),
};
