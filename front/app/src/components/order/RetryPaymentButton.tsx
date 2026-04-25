"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { retryPaymentAction } from "@/lib/actions/order";

export function RetryPaymentButton({ orderUuid }: { orderUuid: string }) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  const onClick = () =>
    startTransition(async () => {
      setError(null);
      const res = await retryPaymentAction(orderUuid);
      if (!res.ok) setError(res.error ?? "Retry failed.");
      else router.refresh();
    });

  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        onClick={onClick}
        disabled={pending}
        className="inline-flex items-center justify-center gap-2 px-6 h-12 bg-error text-on-error font-display font-semibold rounded-lg hover:brightness-110 active:scale-[0.98] transition-all disabled:opacity-60"
      >
        <Icon name="autorenew" size={18} />
        {pending ? "Retrying…" : "Retry payment"}
      </button>
      {error && (
        <p
          role="alert"
          className="text-sm text-on-error-container bg-error-container/60 px-4 py-2 rounded-lg"
        >
          {error}
        </p>
      )}
    </div>
  );
}
