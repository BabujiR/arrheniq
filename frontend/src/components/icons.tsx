import { Children, cloneElement, isValidElement, type ReactElement, type ReactNode } from 'react';

/**
 * ArrheniQ icon set: 24×24 two-tone line icons drawn for the product (no icon dependency), coloured with
 * currentColor. Each has one meaning, used consistently across the app.
 *
 * Motion (brand.css): inside `.arq-draw` (page header tile, active menu item) the outline draws itself and the
 * soft fill fades in; on hover (`.arq-hover-icon`) each icon has its own small gesture, e.g. the capsule
 * wiggles, the tube fills, the arrow slides. Everything is off with prefers-reduced-motion.
 */
const STROKES = new Set(['path', 'rect', 'circle', 'line', 'polyline']);

/** Gives every outline element pathLength=1 (so CSS can draw it) and leaves the soft fill ("d") alone. */
function prepare(node: ReactNode): ReactNode {
  return Children.map(node, (child) => {
    if (!isValidElement(child)) {
      return child;
    }
    const el = child as ReactElement<{ className?: string; children?: ReactNode; pathLength?: number }>;
    if (el.type === 'g') {
      return cloneElement(el, {}, prepare(el.props.children));
    }
    if (typeof el.type === 'string' && STROKES.has(el.type) && el.props.className !== 'd') {
      return cloneElement(el, { pathLength: 1, className: el.props.className ? `s ${el.props.className}` : 's' });
    }
    return el;
  });
}

function Svg({ size = 18, name, children }: { size?: number; name: string; children: ReactNode }) {
  return (
    <svg
      className={`arq-icon arq-icon--${name}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      {prepare(children)}
    </svg>
  );
}

type P = { size?: number };

export const IconHome = (p: P) => (
  <Svg name="home" {...p}>
    <path className="d" d="M5.5 10.2 12 5l6.5 5.2V20h-13z" />
    <path d="M3.5 11 12 4l8.5 7" />
    <path d="M5.5 9.5V20h13V9.5" />
    <path d="M10 20v-5a2 2 0 0 1 4 0v5" />
  </Svg>
);

/** Products: a two-part capsule. */
export const IconProduct = (p: P) => (
  <Svg name="product" {...p}>
    <g transform="rotate(-45 12 12)">
      <path className="d" d="M12 8.5H6a3.5 3.5 0 0 0 0 7h6z" />
      <rect x="2.5" y="8.5" width="19" height="7" rx="3.5" />
      <path d="M12 8.5v7" />
      <path d="M15.5 10.8h2.5" />
    </g>
  </Svg>
);

/** Test library: a test tube with sample. */
export const IconTest = (p: P) => (
  <Svg name="test" {...p}>
    <path className="d liquid" d="M9.5 12h5v4.5a2.5 2.5 0 0 1-5 0z" />
    <path d="M8 3h8" />
    <path d="M9.5 3v13.5a2.5 2.5 0 0 0 5 0V3" />
    <path d="M9.5 12h5" />
    <circle className="bubble" cx="12" cy="9" r=".6" />
  </Svg>
);

/** Specification: a document with a check list. */
export const IconSpec = (p: P) => (
  <Svg name="spec" {...p}>
    <path className="d" d="M6 3.5h9l3.5 3.5v13.5H6z" />
    <path d="M15 3.5H6.5a1 1 0 0 0-1 1v15a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1V7z" />
    <path d="M15 3.5V7h3.5" />
    <path className="ticks" d="m8.5 11 1.2 1.2 2-2.2M8.5 16l1.2 1.2 2-2.2" />
    <path d="M13.5 11.5h2.5M13.5 16.5h2.5" />
  </Svg>
);

/** Controlled lists: a list with bullets. */
export const IconList = (p: P) => (
  <Svg name="list" {...p}>
    <rect className="d" x="3.5" y="4" width="17" height="16" rx="2.5" />
    <rect x="3.5" y="4" width="17" height="16" rx="2.5" />
    <circle cx="7.5" cy="9" r=".9" />
    <circle cx="7.5" cy="12.5" r=".9" />
    <circle cx="7.5" cy="16" r=".9" />
    <path className="lines" d="M10.5 9h6.5M10.5 12.5h6.5M10.5 16h4.5" />
  </Svg>
);

/** Time-point plan: a calendar. */
export const IconCalendar = (p: P) => (
  <Svg name="calendar" {...p}>
    <rect className="d" x="3.5" y="5" width="17" height="15.5" rx="2.5" />
    <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
    <path d="M3.5 9.5h17M8 3v4M16 3v4" />
    <path d="M8 13h2M12 13h2M8 16.5h2" />
  </Svg>
);

/** Study: a stability chamber with its shelves and a thermometer. */
export const IconStudy = (p: P) => (
  <Svg name="study" {...p}>
    <rect className="d" x="4" y="3" width="16" height="18" rx="2.5" />
    <rect x="4" y="3" width="16" height="18" rx="2.5" />
    <path d="M4 9h11M4 15h11" />
    <path d="M17.5 6.5v8.2" />
    <circle cx="17.5" cy="16.3" r="1.3" />
  </Svg>
);

export const IconPlus = (p: P) => (
  <Svg name="plus" {...p}>
    <path d="M12 5v14M5 12h14" />
  </Svg>
);

export const IconEdit = (p: P) => (
  <Svg name="edit" {...p}>
    <path className="d" d="M4 20h4L19 9l-4-4L4 16z" />
    <path d="M4 20h4L19 9l-4-4L4 16z" />
    <path d="M13.5 6.5l4 4" />
  </Svg>
);

/** Submit for approval: paper plane. */
export const IconSend = (p: P) => (
  <Svg name="send" {...p}>
    <path className="d" d="M20.5 3.5 3.5 10.5l7 3 3 7z" />
    <path d="M20.5 3.5 3.5 10.5l7 3 3 7z" />
    <path d="M10.5 13.5 20.5 3.5" />
  </Svg>
);

/** Approve = electronic signature: a signature stroke on a line. */
export const IconSignature = (p: P) => (
  <Svg name="signature" {...p}>
    <path className="sig" d="M3 16c2.5-5 4.5-6.5 5.5-4.5S9 16 11 14s2.5-4.5 3.5-3 1 3 2.5 2 2.5-2 4-2" />
    <path d="M3 20.5h18" />
  </Svg>
);

export const IconCheck = (p: P) => (
  <Svg name="check" {...p}>
    <path d="m5 12.5 4.5 4.5L19.5 7" />
  </Svg>
);

export const IconReject = (p: P) => (
  <Svg name="reject" {...p}>
    <path d="M9 14 4.5 9.5 9 5" />
    <path d="M4.5 9.5h10a5 5 0 0 1 0 10H11" />
  </Svg>
);

/** Remove a row or column from an editor (not a workflow step). */
export const IconRemove = (p: P) => (
  <Svg name="remove" {...p}>
    <path d="M6 6l12 12" />
    <path d="M18 6 6 18" />
  </Svg>
);

/** Retire: archive box. */
export const IconArchive = (p: P) => (
  <Svg name="archive" {...p}>
    <rect className="d" x="5" y="8.5" width="14" height="11.5" />
    <rect className="lid" x="3" y="4" width="18" height="4.5" rx="1" />
    <path d="M5 8.5V20h14V8.5" />
    <path d="M10 12.5h4" />
  </Svg>
);

export const IconLogout = (p: P) => (
  <Svg name="logout" {...p}>
    <path d="M9.5 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3.5" />
    <g className="arrow">
      <path d="M15 7.5 19.5 12 15 16.5" />
      <path d="M19.5 12H9.5" />
    </g>
  </Svg>
);

export const IconLock = (p: P) => (
  <Svg name="lock" {...p}>
    <rect className="d" x="5" y="11" width="14" height="9.5" rx="2" />
    <rect x="5" y="11" width="14" height="9.5" rx="2" />
    <path className="shackle" d="M8.5 11V8a3.5 3.5 0 0 1 7 0v3" />
    <path d="M12 14.5v2.5" />
  </Svg>
);

/** Waiting for approval: clipboard with a tick. */
export const IconReview = (p: P) => (
  <Svg name="review" {...p}>
    <rect className="d" x="5.5" y="4.5" width="13" height="16.5" rx="2" />
    <rect x="5.5" y="4.5" width="13" height="16.5" rx="2" />
    <path d="M9 4.5h6V7H9z" />
    <path d="m9.5 14 2 2 3.5-4" />
  </Svg>
);

/** Drafts: a document with a fold. */
export const IconDraft = (p: P) => (
  <Svg name="draft" {...p}>
    <path className="d" d="M14 3.5H7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8.5z" />
    <path d="M14 3.5H7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8.5z" />
    <path d="M14 3.5v5h5" />
    <path d="M9 13h6M9 16.5h4" />
  </Svg>
);

/** Approved: a seal with a tick. */
const SEAL =
  'M12 3.5 14 5.5l2.8-.4.6 2.8 2.4 1.5-1.2 2.6 1.2 2.6-2.4 1.5-.6 2.8-2.8-.4L12 20.5 10 18.5l-2.8.4-.6-2.8-2.4-1.5L5.4 12 4.2 9.4l2.4-1.5.6-2.8 2.8.4z';
export const IconApproved = (p: P) => (
  <Svg name="approved" {...p}>
    <path className="d" d={SEAL} />
    <path d={SEAL} />
    <path d="m9.3 12.2 1.9 1.9 3.6-3.8" />
  </Svg>
);

export const IconSun = (p: P) => (
  <Svg name="sun" {...p}>
    <circle className="d" cx="12" cy="12" r="4" />
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" />
  </Svg>
);

export const IconMoon = (p: P) => (
  <Svg name="moon" {...p}>
    <path className="d" d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z" />
    <path d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z" />
  </Svg>
);

/** Collapse the side menu. */
export const IconCollapse = (p: P) => (
  <Svg name="collapse" {...p}>
    <rect className="d" x="3.5" y="4.5" width="5.5" height="15" />
    <rect x="3.5" y="4.5" width="17" height="15" rx="2" />
    <path d="M9 4.5v15" />
    <path className="chev" d="m15.5 9.5-2.5 2.5 2.5 2.5" />
  </Svg>
);

/** Expand the side menu. */
export const IconExpand = (p: P) => (
  <Svg name="expand" {...p}>
    <rect className="d" x="3.5" y="4.5" width="5.5" height="15" />
    <rect x="3.5" y="4.5" width="17" height="15" rx="2" />
    <path d="M9 4.5v15" />
    <path className="chev" d="m13 9.5 2.5 2.5-2.5 2.5" />
  </Svg>
);
