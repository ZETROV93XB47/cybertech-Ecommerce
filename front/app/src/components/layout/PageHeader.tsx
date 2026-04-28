import { Breadcrumbs, type BreadcrumbItem } from "@/components/ui/Breadcrumbs";

/**
 * Standardised header banner for static / marketing-style pages.
 * Renders an eyebrow caption, a large display title, an optional lead paragraph
 * and an optional Breadcrumbs trail above. Server component.
 *
 *   <PageHeader
 *     eyebrow="Legal"
 *     title="Privacy Policy"
 *     lead="How we collect, store and use your personal data."
 *     breadcrumbs={[{ label: "Home", href: "/" }, { label: "Legal" }]}
 *   />
 */
export function PageHeader({
  eyebrow,
  title,
  lead,
  breadcrumbs,
  align = "left",
}: {
  eyebrow?: string;
  title: string;
  lead?: string;
  breadcrumbs?: BreadcrumbItem[];
  align?: "left" | "center";
}) {
  const isCentered = align === "center";
  return (
    <header
      className={`pt-16 pb-12 border-b border-slate-100 ${isCentered ? "text-center" : ""}`}
    >
      <div className="container mx-auto px-6 md:px-8">
        {breadcrumbs && breadcrumbs.length > 0 && (
          <div
            className={`mb-6 ${isCentered ? "flex justify-center" : ""}`}
          >
            <Breadcrumbs items={breadcrumbs} />
          </div>
        )}
        {eyebrow && (
          <span className="font-label-caps text-secondary tracking-[0.2em] uppercase text-xs">
            {eyebrow}
          </span>
        )}
        <h1
          className={`font-display text-[clamp(36px,5vw,56px)] leading-[1.05] font-bold text-primary mt-3 ${
            isCentered ? "mx-auto max-w-3xl" : ""
          }`}
        >
          {title}
        </h1>
        {lead && (
          <p
            className={`font-body text-body-lg text-on-surface-variant mt-4 ${
              isCentered ? "mx-auto max-w-2xl" : "max-w-2xl"
            }`}
          >
            {lead}
          </p>
        )}
      </div>
    </header>
  );
}
