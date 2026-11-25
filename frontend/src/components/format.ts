/** Date/time display (ADR 0012): the site's local time with zone, plus UTC for the audit-minded reader. */
export function formatInstant(iso: string, timeZone?: string): string {
  const date = new Date(iso);
  const local = new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone,
    timeZoneName: 'short',
  }).format(date);
  const utc = date.toISOString().slice(0, 16).replace('T', ' ');
  return `${local} (${utc} UTC)`;
}

export function label(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((w) => (w ? w[0]!.toUpperCase() + w.slice(1) : w))
    .join(' ');
}
