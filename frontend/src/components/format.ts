/** Date/time display (ADR 0012): the site's local time with zone, plus UTC for the audit-minded reader. */
export function formatInstant(iso: string, timeZone?: string): string {
  const date = new Date(iso);
  const local = new Intl.DateTimeFormat('en-US', {
    // explicit fields: dateStyle/timeStyle can't be combined with timeZoneName (Chrome throws "Invalid option")
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
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
