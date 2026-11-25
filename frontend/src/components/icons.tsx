import type { ReactNode } from 'react';

/**
 * ArrheniQ icon set: simple 24×24 line icons drawn for the product (no icon dependency), coloured with
 * currentColor. Each has a meaning that is used consistently across the app.
 */
function Svg({ size = 18, children }: { size?: number; children: ReactNode }) {
  return (
    <svg
      className="arq-icon"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      {children}
    </svg>
  );
}

type P = { size?: number };

export const IconHome = (p: P) => (
  <Svg {...p}>
    <path d="M3.5 11 12 4l8.5 7" />
    <path d="M5.5 9.5V20h13V9.5" />
    <path d="M10 20v-5.5h4V20" />
  </Svg>
);

/** Products: a capsule. */
export const IconProduct = (p: P) => (
  <Svg {...p}>
    <rect x="2.5" y="8.5" width="19" height="7" rx="3.5" transform="rotate(-45 12 12)" />
    <path d="M9.5 9.5l5 5" />
  </Svg>
);

/** Test library: a test tube. */
export const IconTest = (p: P) => (
  <Svg {...p}>
    <path d="M8.5 3h7" />
    <path d="M9.5 3v13.5a2.5 2.5 0 0 0 5 0V3" />
    <path d="M9.5 12.5h5" />
  </Svg>
);

export const IconPlus = (p: P) => (
  <Svg {...p}>
    <path d="M12 5v14M5 12h14" />
  </Svg>
);

export const IconEdit = (p: P) => (
  <Svg {...p}>
    <path d="M4 20h4L19 9l-4-4L4 16z" />
    <path d="M13.5 6.5l4 4" />
  </Svg>
);

/** Submit for approval: paper plane. */
export const IconSend = (p: P) => (
  <Svg {...p}>
    <path d="M20.5 3.5 3.5 10.5l7 3 3 7z" />
    <path d="M10.5 13.5 20.5 3.5" />
  </Svg>
);

/** Approve = electronic signature: a signature stroke on a line. */
export const IconSignature = (p: P) => (
  <Svg {...p}>
    <path d="M3 16c2.5-5 4.5-6.5 5.5-4.5S9 16 11 14s2.5-4.5 3.5-3 1 3 2.5 2 2.5-2 4-2" />
    <path d="M3 20.5h18" />
  </Svg>
);

export const IconCheck = (p: P) => (
  <Svg {...p}>
    <path d="m5 12.5 4.5 4.5L19.5 7" />
  </Svg>
);

export const IconReject = (p: P) => (
  <Svg {...p}>
    <path d="M9 14 4.5 9.5 9 5" />
    <path d="M4.5 9.5h10a5 5 0 0 1 0 10H11" />
  </Svg>
);

/** Retire: archive box. */
export const IconArchive = (p: P) => (
  <Svg {...p}>
    <rect x="3" y="4" width="18" height="4.5" rx="1" />
    <path d="M5 8.5V20h14V8.5" />
    <path d="M10 12.5h4" />
  </Svg>
);

export const IconLogout = (p: P) => (
  <Svg {...p}>
    <path d="M9.5 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3.5" />
    <path d="M15 7.5 19.5 12 15 16.5" />
    <path d="M19.5 12H9.5" />
  </Svg>
);

export const IconLock = (p: P) => (
  <Svg {...p}>
    <rect x="5" y="11" width="14" height="9.5" rx="2" />
    <path d="M8.5 11V8a3.5 3.5 0 0 1 7 0v3" />
  </Svg>
);

/** Waiting for approval: clipboard with a tick. */
export const IconReview = (p: P) => (
  <Svg {...p}>
    <rect x="5.5" y="4.5" width="13" height="16.5" rx="2" />
    <path d="M9 4.5h6V7H9z" />
    <path d="m9.5 14 2 2 3.5-4" />
  </Svg>
);

/** Drafts: a document with a fold. */
export const IconDraft = (p: P) => (
  <Svg {...p}>
    <path d="M14 3.5H7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8.5z" />
    <path d="M14 3.5v5h5" />
    <path d="M9 13h6M9 16.5h4" />
  </Svg>
);

/** Approved: a seal with a tick. */
export const IconApproved = (p: P) => (
  <Svg {...p}>
    <path d="M12 3.5 14 5.5l2.8-.4.6 2.8 2.4 1.5-1.2 2.6 1.2 2.6-2.4 1.5-.6 2.8-2.8-.4L12 20.5 10 18.5l-2.8.4-.6-2.8-2.4-1.5L5.4 12 4.2 9.4l2.4-1.5.6-2.8 2.8.4z" />
    <path d="m9.3 12.2 1.9 1.9 3.6-3.8" />
  </Svg>
);

export const IconSun = (p: P) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" />
  </Svg>
);

export const IconMoon = (p: P) => (
  <Svg {...p}>
    <path d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z" />
  </Svg>
);
