import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import { SignOutButton } from "./SignOutButton";
import type { UserResponseDto } from "@/lib/api";

/**
 * Identity card for /account/profile.
 *
 * Backend's UserResponseDto has no avatar field — we synthesise one from the
 * user's initials on a deterministic gradient (hashed from the email/username
 * so the same user always gets the same hue). This avoids a chicken-and-egg
 * upload UX while leaving room to swap in a real photoUrl later.
 *
 * Renders a fallback identity (just session-shaped name + email) when the
 * full UserResponseDto fetch failed — see ProfilePage error policy.
 */

export interface ProfileFallback {
  fullName: string;
  email: string;
  username?: string | null;
}

interface ProfileSummaryProps {
  user: UserResponseDto | null;
  fallback: ProfileFallback;
}

const GRADIENT_PALETTE = [
  "from-secondary to-secondary-container",
  "from-primary-container to-secondary",
  "from-secondary to-primary",
  "from-secondary-container to-secondary",
  "from-secondary to-on-secondary-fixed-variant",
  "from-primary to-secondary-container",
];

function hashSeed(seed: string): number {
  let h = 0;
  for (let i = 0; i < seed.length; i++) {
    h = (h * 31 + seed.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

function pickGradient(seed: string): string {
  return GRADIENT_PALETTE[hashSeed(seed) % GRADIENT_PALETTE.length];
}

function initials(firstName?: string | null, lastName?: string | null, fallbackName?: string): string {
  const f = (firstName ?? "").trim();
  const l = (lastName ?? "").trim();
  if (f || l) {
    return `${f.charAt(0) ?? ""}${l.charAt(0) ?? ""}`.toUpperCase() || "?";
  }
  const name = (fallbackName ?? "").trim();
  if (!name) return "?";
  const parts = name.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0].charAt(0)}${parts[parts.length - 1].charAt(0)}`.toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
}

export function ProfileSummary({ user, fallback }: ProfileSummaryProps) {
  const firstName = user?.firstName ?? null;
  const lastName = user?.lastName ?? null;
  const email = user?.email ?? fallback.email;
  const username = user?.username ?? fallback.username ?? null;
  const fullName =
    user && (user.firstName || user.lastName)
      ? `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim()
      : fallback.fullName;

  const gradientSeed = email || username || fullName || "cybertech";
  const gradient = pickGradient(gradientSeed);
  const monogram = initials(firstName, lastName, fullName);

  const role = user?.role;
  const address = user?.address;

  return (
    <section
      aria-labelledby="profile-summary-heading"
      className="bg-white border border-slate-100 p-8 flex flex-col gap-6"
    >
      <h2 id="profile-summary-heading" className="sr-only">
        Profile summary
      </h2>

      <div className="flex flex-col items-center text-center gap-4">
        <div
          aria-hidden="true"
          className={`relative w-28 h-28 rounded-full bg-gradient-to-br ${gradient} flex items-center justify-center text-on-primary font-display text-3xl font-semibold tracking-tight shadow-sm`}
        >
          <span>{monogram}</span>
        </div>
        <div className="space-y-1">
          <p className="font-display text-headline-sm text-primary leading-tight">
            {fullName || "Cybertech member"}
          </p>
          {username && (
            <p className="font-body text-on-surface-variant text-sm">@{username}</p>
          )}
          <p className="font-body text-on-surface-variant text-sm break-all">{email}</p>
        </div>
        {role && (
          <span className="inline-flex items-center gap-1 px-3 py-1 bg-secondary/10 text-secondary uppercase tracking-wider text-label-caps">
            <Icon name="verified" size={14} />
            {role}
          </span>
        )}
      </div>

      {address && (address.street || address.city) && (
        <div className="border-t border-slate-100 pt-6 space-y-2">
          <p className="font-label-caps uppercase tracking-wider text-on-surface-variant">
            Default address
          </p>
          <address className="not-italic font-body text-sm text-on-surface leading-relaxed">
            {address.street && <span className="block">{address.street}</span>}
            {(address.zipCode || address.city) && (
              <span className="block">
                {address.zipCode} {address.city}
              </span>
            )}
            {address.country && <span className="block">{address.country}</span>}
          </address>
        </div>
      )}

      <div className="flex flex-col gap-3 border-t border-slate-100 pt-6">
        <Link
          href="/account/profile/edit"
          className="inline-flex items-center justify-center gap-2 h-11 border border-outline px-6 font-display text-sm font-semibold uppercase tracking-wider hover:bg-slate-50 transition-colors"
          aria-disabled={!user}
        >
          <Icon name="edit" size={16} />
          Edit profile
        </Link>
        <SignOutButton />
      </div>
    </section>
  );
}
