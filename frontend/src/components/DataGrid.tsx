import { useComputedColorScheme } from '@mantine/core';
import { colorSchemeDark, colorSchemeLight, themeQuartz, type ColDef, type RowClickedEvent } from 'ag-grid-community';
import { AgGridReact } from 'ag-grid-react';

interface Props<T> {
  rows: T[] | undefined;
  columns: ColDef<T>[];
  rowId: (row: T) => string;
  onRowClick?: (row: T) => void;
  loading?: boolean;
  height?: string;
  /** Shown when there are no rows (plain text). */
  emptyMessage?: string;
}

const lightTheme = themeQuartz.withPart(colorSchemeLight);
const darkTheme = themeQuartz.withPart(colorSchemeDark);

/** AG Grid (Community) with ArrheniQ defaults: sortable/filterable/resizable columns, row click to open, dark mode. */
export function DataGrid<T>({
  rows,
  columns,
  rowId,
  onRowClick,
  loading,
  height = 'calc(100vh - 210px)',
  emptyMessage = 'Nothing to show yet.',
}: Props<T>) {
  const scheme = useComputedColorScheme('light');
  return (
    <div style={{ height, width: '100%' }} className="arq-fade-in">
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
