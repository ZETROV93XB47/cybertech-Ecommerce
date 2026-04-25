"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { getOrderStatusAction } from "@/lib/actions/order";

/**
 * Polls /order/status/{uuid} every 2 seconds, up to ~10 seconds, while the
 * order is in a transient state. When the status changes (or the budget
 * expires), it triggers a router refresh so the server component
 * re-renders with the new status. Renders nothing visible.
 *
 * Safe to mount unconditionally — it self-stops as soon as the status
 * stops being `CREATED`.
 */
export function OrderStatusPoller({
  orderUuid,
  initialStatus,
  budgetMs = 10_000,
  intervalMs = 2_000,
}: {
  orderUuid: string;
  initialStatus: string;
  budgetMs?: number;
  intervalMs?: number;
}) {
  const router = useRouter();
  const startedAt = useRef<number | null>(null);
  const [active, setActive] = useState(initialStatus === "CREATED");

  useEffect(() => {
    if (!active) return;
    if (startedAt.current === null) startedAt.current = Date.now();
    let cancelled = false;

    const tick = async () => {
      if (cancelled) return;
      if (Date.now() - (startedAt.current ?? Date.now()) > budgetMs) {
        setActive(false);
        return;
      }
      const res = await getOrderStatusAction(orderUuid);
      if (cancelled) return;
      if (!res) {
        setActive(false);
        return;
      }
      if (res.status !== initialStatus) {
        setActive(false);
        router.refresh();
      }
    };

    const handle = setInterval(tick, intervalMs);
    return () => {
      cancelled = true;
      clearInterval(handle);
    };
  }, [active, orderUuid, initialStatus, budgetMs, intervalMs, router]);

  return null;
}
