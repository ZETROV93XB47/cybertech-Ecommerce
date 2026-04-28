import { redirect } from "next/navigation";
import Link from "next/link";
import { auth } from "@/lib/auth";
import { MainLayout } from "@/components/layout/MainLayout";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { Icon } from "@/components/ui/Icon";

export const metadata = {
  title: "Create your account — Cybertech",
  description:
    "Join Cybertech in three quick steps: identity, address, and an optional payment method.",
};

/**
 * Registration entry point. We bounce already-signed-in users straight to
 * their account dashboard rather than showing a meaningless form.
 */
export default async function RegisterPage() {
  const session = await auth();
  if (session) redirect("/account/profile");

  return (
    <MainLayout>
      <div className="bg-surface-container-low min-h-[calc(100vh-160px)]">
        <div className="max-w-3xl mx-auto px-6 md:px-8 py-12 md:py-20">
          <header className="mb-10">
            <Link
              href="/"
              className="inline-flex items-center gap-2 text-on-surface-variant hover:text-primary transition-colors mb-6"
            >
              <Icon name="arrow_back" size={16} />
              <span className="font-label-caps uppercase tracking-wider text-xs">
                Back to store
              </span>
            </Link>
            <p className="font-label-caps uppercase tracking-[0.2em] text-secondary mb-2 text-xs">
              Get started
            </p>
            <h1 className="font-display text-display-lg text-3xl md:text-4xl text-primary mb-3 font-bold">
              Create your Cybertech account
            </h1>
            <p className="font-body text-body-md text-on-surface-variant max-w-xl">
              Track orders, save payment methods, and unlock member pricing —
              setup takes less than a minute.
            </p>
          </header>

          <RegisterForm />
        </div>
      </div>
    </MainLayout>
  );
}
