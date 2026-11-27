import { api } from './http';
import type { StudyDetails, StudyLoginInput, StudySchedule, StudySummary, StudyUpdateInput } from './types';

export const studyKeys = {
  all: ['studies'] as const,
  list: (filter: { q?: string; productId?: string; status?: string }) => ['studies', filter] as const,
  study: (id: string) => ['study', id] as const,
  schedule: (id: string) => ['study-schedule', id] as const,
};

function query(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) {
      search.set(key, value);
    }
  }
  const s = search.toString();
  return s ? `?${s}` : '';
}

/** Studies (URS-STU-001/002/004/005) and their schedules (URS-SCH-001/002). */
export const studiesApi = {
  list: (filter: { q?: string; productId?: string; status?: string }) =>
    api<StudySummary[]>('GET', `/api/v1/studies${query({ ...filter, limit: '200' })}`),
  get: (id: string) => api<StudyDetails>('GET', `/api/v1/studies/${id}`),
  login: (input: StudyLoginInput) => api<StudySummary[]>('POST', '/api/v1/studies', input),
  update: (id: string, expectedVersion: number, input: StudyUpdateInput) =>
    api<StudyDetails>('PUT', `/api/v1/studies/${id}`, { expectedVersion, ...input }),
  submit: (id: string, expectedVersion: number) =>
    api<StudyDetails>('POST', `/api/v1/studies/${id}/submit`, { expectedVersion }),
  approve: (id: string, expectedVersion: number, comment?: string) =>
    api<StudyDetails>('POST', `/api/v1/studies/${id}/approve`, { expectedVersion, comment }),
  reject: (id: string, expectedVersion: number, reason: string) =>
    api<StudyDetails>('POST', `/api/v1/studies/${id}/reject`, { expectedVersion, reason }),
  discard: (id: string, expectedVersion: number, reason: string) =>
    api<void>('POST', `/api/v1/studies/${id}/discard`, { expectedVersion, reason }),
  schedule: (id: string) => api<StudySchedule>('GET', `/api/v1/studies/${id}/schedule`),
};
