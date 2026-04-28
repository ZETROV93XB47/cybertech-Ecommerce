"use client";

import { useEffect } from "react";

/**
 * `global-error.tsx` — Next 16 convention.
 *
 * Replaces the root layout (including <html>/<body>) when a rendering error
 * happens **above** the segment-level error boundary in `error.tsx`. Because
 * the root layout is bypassed, none of the global fonts, providers or
 * Tailwind layers are guaranteed to be available — we ship a self-contained
 * page using inline styles only so it always renders.
 */
export default function GlobalError({
  error,
}: {
  error: Error & { digest?: string };
}) {
  useEffect(() => {
    console.error("[global-error.tsx] root-level failure", error);
  }, [error]);

  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          backgroundColor: "#f8fafc",
          color: "#0f172a",
          fontFamily:
            "system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "48px 24px",
        }}
      >
        <div
          style={{
            maxWidth: "560px",
            width: "100%",
            textAlign: "center",
          }}
        >
          <p
            style={{
              fontSize: "12px",
              letterSpacing: "0.2em",
              textTransform: "uppercase",
              color: "#64748b",
              marginBottom: "24px",
            }}
          >
            Cybertech
          </p>
          <h1
            style={{
              fontSize: "32px",
              fontWeight: 700,
              lineHeight: 1.1,
              margin: "0 0 16px",
              color: "#0f172a",
            }}
          >
            The site hit a snag
          </h1>
          <p
            style={{
              fontSize: "16px",
              lineHeight: 1.6,
              color: "#475569",
              margin: "0 0 32px",
            }}
          >
            A critical error stopped the application from rendering. Reload the
            page to try again. If it keeps happening, give us a few minutes to
            investigate.
          </p>
          {error.digest ? (
            <p
              style={{
                fontFamily:
                  "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
                fontSize: "12px",
                color: "#94a3b8",
                margin: "0 0 24px",
              }}
            >
              Reference: <span>{error.digest}</span>
            </p>
          ) : null}
          <button
            type="button"
            onClick={() => {
              if (typeof window !== "undefined") {
                window.location.reload();
              }
            }}
            style={{
              backgroundColor: "#0f172a",
              color: "#ffffff",
              border: "none",
              padding: "16px 40px",
              fontSize: "13px",
              fontWeight: 600,
              letterSpacing: "0.05em",
              cursor: "pointer",
            }}
          >
            Reload
          </button>
        </div>
      </body>
    </html>
  );
}
