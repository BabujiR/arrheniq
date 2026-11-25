import type { ColDef, RowClickedEvent } from 'ag-grid-community';
import { AgGridReact } from 'ag-grid-react';

interface Props<T> {
  rows: T[] | undefined;
  columns: ColDef<T>[];
  rowId: (row: T) => string;
  onRowClick?: (row: T) => void;
  loading?: boolean;
  height?: string;
}

/** AG Grid (Community) with ArrheniQ defaults: sortable/filterable/resizable columns, row click to open. */
export function DataGrid<T>({ rows, columns, rowId, onRowClick, loading, height = 'calc(100vh - 190px)' }: Props<T>) {
  return (
    <div style={{ height, width: '100%' }}>
      <AgGridReact<T>
        rowData={rows ?? []}
        columnDefs={columns}
        defaultColDef={{ sortable: true, filter: true, resizable: true, flex: 1, minWidth: 110 }}
        getRowId={(p) => rowId(p.data)}
        loading={loading}
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
