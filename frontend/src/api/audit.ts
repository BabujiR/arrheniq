import { api } from './http';

/** One line of a record's audit trail (GET /api/v1/audit/history). */
export interface HistoryEntry {
  seq: number;
  occurredAt: string;
  actorName: string;
  actorType: 'USER' | 'API_KEY' | 'SYSTEM' | 'MIGRATION' | 'PLATFORM';
  action: string;
  recordType: string;
  recordId: string | null;
  recordVersion: number | null;
  businessKey: string | null;
  changes: { field: string; oldValue: unknown; newValue: unknown }[];
  reason: string | null;
}

export const auditApi = {
  history: (recordIds: string[]) =>
    api<HistoryEntry[]>('GET', `/api/v1/audit/history?${recordIds.map((id) => `recordId=${encodeURIComponent(id)}`).join('&')}`),
};
