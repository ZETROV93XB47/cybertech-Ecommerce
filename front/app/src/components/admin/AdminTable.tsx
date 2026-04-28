import type { ReactNode } from "react";

export type AdminColumn<T> = {
  key: string;
  header: ReactNode;
  /** Cell renderer; default is `String(row[key])`. */
  cell?: (row: T) => ReactNode;
  className?: string;
  headerClassName?: string;
  /** When true, the column never wraps. */
  nowrap?: boolean;
};

interface Props<T> {
  columns: AdminColumn<T>[];
  rows: T[];
  /** Stable key for each row (default: index). */
  rowKey?: (row: T, index: number) => string;
  /** Rendered when `rows` is empty. Default: a neutral message. */
  emptyMessage?: ReactNode;
  /** Optional extra row at the end (e.g. action buttons). */
  footer?: ReactNode;
  className?: string;
}

/**
 * Server-renderable, generic admin table. Pagination is owned by the page —
 * this component is purely presentational so we can also use it inside
 * client components that already have data.
 */
export function AdminTable<T>({
  columns,
  rows,
  rowKey,
  emptyMessage = "No records to display.",
  footer,
  className,
}: Props<T>) {
  return (
    <div
      className={
        "overflow-x-auto rounded-xl border border-slate-200 bg-white " +
        (className ?? "")
      }
    >
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                scope="col"
                className={
                  "px-4 py-3 text-left font-label-caps uppercase tracking-wider text-xs text-slate-500 " +
                  (col.headerClassName ?? "")
                }
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.length === 0 ? (
            <tr>
              <td
                colSpan={columns.length}
                className="px-4 py-10 text-center text-slate-500"
              >
                {emptyMessage}
              </td>
            </tr>
          ) : (
            rows.map((row, idx) => (
              <tr
                key={rowKey ? rowKey(row, idx) : String(idx)}
                className="hover:bg-slate-50/60 transition-colors"
              >
                {columns.map((col) => {
                  const value = col.cell
                    ? col.cell(row)
                    : (row as unknown as Record<string, unknown>)[col.key];
                  return (
                    <td
                      key={col.key}
                      className={
                        "px-4 py-3 align-middle " +
                        (col.nowrap ? "whitespace-nowrap " : "") +
                        (col.className ?? "")
                      }
                    >
                      {value as ReactNode}
                    </td>
                  );
                })}
              </tr>
            ))
          )}
        </tbody>
        {footer && (
          <tfoot className="bg-slate-50 border-t border-slate-200">
            <tr>
              <td colSpan={columns.length} className="px-4 py-3">
                {footer}
              </td>
            </tr>
          </tfoot>
        )}
      </table>
    </div>
  );
}
