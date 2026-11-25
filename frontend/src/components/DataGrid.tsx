import { useComputedColorScheme } from '@mantine/core';
import { colorSchemeDark, colorSchemeLight, themeQuartz, type ColDef, type RowClickedEvent } from 'ag-grid-community';
import { AgGridReact } from 'ag-grid-react';

interface Props<T> {
  rows: T[] | undefined;
  columns: ColDef<T>[];
  rowId: (row: T) => string;
  onRowClick?: (row: T) => void;
  loading?: boolean;
  /** Fixed height; by default the grid fills the rest of the page (AppShell.Main is a flex column). */
  height?: string;
  /** Shown when there are no rows (plain text). */
  emptyMessage?: string;
}

const FONT = ['Inter Variable', 'Inter', 'system-ui', 'Segoe UI', 'sans-serif'];
const shared = {
  fontFamily: FONT,
  fontSize: 13.5,
  headerFontSize: 11.5,
  headerFontWeight: 600,
  rowHeight: 42,
  headerHeight: 40,
  wrapperBorderRadius: 10,
  spacing: 7,
};
const lightTheme = themeQuartz.withPart(colorSchemeLight).withParams({
  ...shared,
  accentColor: '#2E3192',
  headerBackgroundColor: '#F6F7FB',
  headerTextColor: '#5C5F7A',
  borderColor: '#E3E5EF',
  rowHoverColor: '#EEF0FB',
});
const darkTheme = themeQuartz.withPart(colorSchemeDark).withParams({
  ...shared,
  accentColor: '#8B91DE',
});

/** AG Grid (Community) with ArrheniQ defaults: sortable/filterable/resizable columns, row click to open, dark mode. */
export function DataGrid<T>({
  rows,
  columns,
  rowId,
  onRowClick,
  loading,
  height,
  emptyMessage = 'Nothing to show yet.',
}: Props<T>) {
  const scheme = useComputedColorScheme('light');
  return (
    <div
      style={height ? { height, width: '100%' } : { flex: '1 1 0', minHeight: 320, width: '100%' }}
      className="arq-fade-in arq-grid"
    >
      <AgGridReact<T>
        theme={scheme === 'dark' ? darkTheme : lightTheme}
        rowData={rows ?? []}
        columnDefs={columns}
        defaultColDef={{ sortable: true, filter: true, resizable: true, flex: 1, minWidth: 110 }}
        getRowId={(p) => rowId(p.data)}
        loading={loading}
        localeText={{ noRowsToShow: emptyMessage }}
        onRowClicked={(e: RowClickedEvent<T>) => {
          if (e.data && onRowClick) {
            onRowClick(e.data);
          }
        }}
        rowStyle={{ cursor: onRowClick ? 'pointer' : 'default' }}
      />
    </div>
  );
}
